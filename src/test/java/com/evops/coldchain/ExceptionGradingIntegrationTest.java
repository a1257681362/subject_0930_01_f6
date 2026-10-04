package com.evops.coldchain;

import com.evops.coldchain.dto.DispatchRequest;
import com.evops.coldchain.dto.DrugCreateRequest;
import com.evops.coldchain.dto.ExceptionConclusionRequest;
import com.evops.coldchain.dto.ExceptionMarkRequest;
import com.evops.coldchain.dto.ExceptionReviewRequest;
import com.evops.coldchain.dto.GradeRuleSaveRequest;
import com.evops.coldchain.dto.OrderCreateRequest;
import com.evops.coldchain.dto.ProbeBindRequest;
import com.evops.coldchain.dto.ProbeCreateRequest;
import com.evops.coldchain.dto.SettlementCreateRequest;
import com.evops.coldchain.dto.SignRequest;
import com.evops.coldchain.dto.TempReportRequest;
import com.evops.coldchain.dto.TemplateSaveRequest;
import com.evops.coldchain.dto.VehicleCreateRequest;
import com.evops.coldchain.dto.ZoneDraftRequest;
import com.evops.coldchain.entity.ExceptionEscalation;
import com.evops.coldchain.entity.ExceptionEvent;
import com.evops.coldchain.entity.ExceptionEvidence;
import com.evops.coldchain.entity.FreightSettlement;
import com.evops.coldchain.entity.TemperatureLog;
import com.evops.coldchain.entity.TransportBatch;
import com.evops.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 冷链 V6 验收：运输异常分级处置机制。
 *
 * 覆盖：
 * 1) 四类异常分级配置（等级/响应时限/升级岗位/复核角色）、规则版本化只影响此后确认的异常；
 * 2) 异常确认即启动处置时钟；首次发现时间可补录但一经写入不被后续补录覆盖；
 * 3) 冷链温度分级调到临界时间后，系统预警联动异常与人工异常的处置时钟按同一口径归属
 *    （确认时刻/首次发现/截止）；
 * 4) 升级通知逐级发送、发送结果与重试原因留痕、失败可重试；
 * 5) 处置结论必须引用证据（无证据/他批证据拒绝）、指定角色复核（HTTP 权限点 + 角色）；
 * 6) 未关闭异常不得进入结算（未签收/未关闭均卡口），关闭并签收后方可结算。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ExceptionGradingIntegrationTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 10, 3, 8, 0);

    @LocalServerPort
    private int port;
    @Autowired
    private TestRestTemplate http;

    @Autowired private com.evops.coldchain.service.DrugService drugService;
    @Autowired private com.evops.coldchain.service.VehicleService vehicleService;
    @Autowired private com.evops.coldchain.service.OrderService orderService;
    @Autowired private com.evops.coldchain.service.ProbeService probeService;
    @Autowired private com.evops.coldchain.service.TransportBatchService batchSvc;
    @Autowired private com.evops.coldchain.service.DrugCategoryService categoryService;
    @Autowired private com.evops.coldchain.service.ExceptionGradeService gradeService;
    @Autowired private com.evops.coldchain.service.ExceptionHandlingService handlingService;
    @Autowired private com.evops.coldchain.service.SettlementService settlementService;
    @Autowired private com.evops.coldchain.service.StationNotificationSender stationSender;

    // ====================================================================
    // 1) 默认分级种子（V1 历史行）+ 规则版本化快照隔离
    // ====================================================================
    @Test
    void defaultGrades_seededAsVersion1_andVersionedSnapshotIsolation() {
        // 四类受管异常的内置种子规则 V1 一定作为历史版本存在（即便被本类其它用例发布的新版本 SUPERSEDED）
        assertSeedVersion(CcConstants.EXC_TEMP, CcConstants.GRADE_L2, 30, CcConstants.ROLE_ADMIN);
        assertSeedVersion(CcConstants.EXC_SEAL_BROKEN, CcConstants.GRADE_L3, 15, CcConstants.ROLE_ADMIN);
        assertSeedVersion(CcConstants.EXC_DELAY, CcConstants.GRADE_L1, 120, CcConstants.ROLE_MANAGER);
        assertSeedVersion(CcConstants.EXC_RECEIVER_REJECT, CcConstants.GRADE_L3, 20, CcConstants.ROLE_ADMIN);

        Long batchId = simpleShippedBatch("规则隔离-" + System.nanoTime());

        // 本用例自洽：先发布一条“基线”延误规则（120/MANAGER），再造异常持有该基线快照
        int baseVer = publishDelayRule(CcConstants.GRADE_L1, "一般", 120,
                Arrays.asList(CcConstants.ROLE_DISPATCHER, CcConstants.ROLE_MANAGER), CcConstants.ROLE_MANAGER);
        ExceptionEvent oldExc = batchSvc.markException(delayMark(batchId, "基线规则下确认的延误", T0));
        assertEquals(120, oldExc.getResponseMinutes());
        assertEquals(CcConstants.ROLE_MANAGER, oldExc.getReviewRole());
        assertEquals(baseVer, oldExc.getGradeRuleVersion());

        // 再发布新规则：L2 / 60 分钟 / 仅升级 ADMIN / ADMIN 复核
        int newVer = publishDelayRule(CcConstants.GRADE_L2, "较重", 60,
                Collections.singletonList(CcConstants.ROLE_ADMIN), CcConstants.ROLE_ADMIN);
        assertEquals(baseVer + 1, newVer);

        // 旧异常快照不随规则变化；新异常取新规则
        ExceptionEvent oldReload = handlingService.requireEvent(oldExc.getId());
        assertEquals(120, oldReload.getResponseMinutes());
        assertEquals(CcConstants.ROLE_MANAGER, oldReload.getReviewRole());

        ExceptionEvent newExc = batchSvc.markException(delayMark(batchId, "新规则下确认的延误", T0.plusHours(1)));
        assertEquals(60, newExc.getResponseMinutes());
        assertEquals(CcConstants.ROLE_ADMIN, newExc.getReviewRole());
        assertEquals(CcConstants.GRADE_L2, newExc.getGradeCode());
        assertEquals(newVer, newExc.getGradeRuleVersion());
        // 版本只追加，历史行全部保留
        assertTrue(gradeService.listVersions(CcConstants.EXC_DELAY).size() >= 2);
    }

    private void assertSeedVersion(String type, String grade, int minutes, String reviewRole) {
        com.evops.coldchain.entity.ExceptionGradeRule seed = gradeService.listVersions(type).stream()
                .filter(r -> r.getVersionNo() == 1)
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 " + type + " 内置种子 V1"));
        assertEquals(grade, seed.getGradeCode());
        assertEquals(minutes, seed.getResponseMinutes());
        assertEquals(reviewRole, seed.getReviewRole());
        assertTrue(gradeService.parseRoles(seed.getEscalateRoles()).size() >= 1);
    }

    private int publishDelayRule(String grade, String gradeName, int minutes,
                                 List<String> roles, String reviewRole) {
        GradeRuleSaveRequest rule = new GradeRuleSaveRequest();
        rule.setExceptionType(CcConstants.EXC_DELAY);
        rule.setGradeCode(grade);
        rule.setGradeName(gradeName);
        rule.setResponseMinutes(minutes);
        rule.setEscalateRoles(roles);
        rule.setReviewRole(reviewRole);
        rule.setChangeReason("V6 验收规则发布");
        return gradeService.saveRule(rule).getVersionNo();
    }

    // ====================================================================
    // 2) 确认即启动时钟 + 首次发现时间补录不可覆盖
    // ====================================================================
    @Test
    void clockStartsOnConfirm_firstFoundTimeNeverOverwritten() {
        // 自洽：先把延误规则发布为本用例依赖的 120 分钟基线，不依赖其它用例/默认当前状态
        publishDelayRule(CcConstants.GRADE_L1, "一般", 120,
                Arrays.asList(CcConstants.ROLE_DISPATCHER, CcConstants.ROLE_MANAGER), CcConstants.ROLE_MANAGER);

        Long batchId = simpleShippedBatch("时钟-" + System.nanoTime());
        LocalDateTime foundEarly = T0.minusMinutes(40); // 发现早于报告
        ExceptionMarkRequest mark = delayMark(batchId, "司机反馈途中拥堵，事后补录首次发现时间", foundEarly);
        ExceptionEvent exc = batchSvc.markException(mark);

        assertNotNull(exc.getConfirmedTime(), "确认即启动处置时钟");
        assertNotNull(exc.getClockDeadline());
        assertEquals(0, T0.minusMinutes(40).compareTo(exc.getFirstFoundTime()), "首次发现时间取补录值");
        // 120 分钟：deadline = confirmed + 120（confirmed 为登记墙钟时刻，这里验证相对差）
        long minutes = java.time.Duration.between(exc.getConfirmedTime(), exc.getClockDeadline()).toMinutes();
        assertEquals(120, minutes);

        // 后续补录凭证、再补报一条同类异常，均不得覆盖本异常首次发现/确认/截止时间
        ExceptionEvidence ev = appendEvidence(exc.getId(), "拥堵照片.jpg");
        batchSvc.markException(delayMark(batchId, "另一起独立延误", T0.plusHours(2)));
        ExceptionEvent reload = handlingService.requireEvent(exc.getId());
        assertEquals(exc.getConfirmedTime(), reload.getConfirmedTime());
        assertEquals(exc.getFirstFoundTime(), reload.getFirstFoundTime());
        assertEquals(exc.getClockDeadline(), reload.getClockDeadline());
        assertNotNull(ev.getId());
    }

    // ====================================================================
    // 3) 温度分级调到临界时间，系统预警联动异常与人工异常时钟同一口径
    // ====================================================================
    @Test
    void coldChainTempGradeAtCriticalTime_clockAttributionSameCaliber() {
        long nano = System.nanoTime();
        // 类别模板：冷藏 2~8℃，容忍 30 分钟，抖动合并 10 分钟
        Long categoryId = createCategory("临界温区-" + nano, null);
        saveTemplate(categoryId, "2.00", "8.00", 30, 10);
        Long batchId = shippedBatchWithCategory(categoryId, "临界批次-" + nano);

        // 把温度超限分级调到临界：响应时限 5 分钟，升级岗位 调度→经理→管理员
        GradeRuleSaveRequest rule = new GradeRuleSaveRequest();
        rule.setExceptionType(CcConstants.EXC_TEMP);
        rule.setGradeCode(CcConstants.GRADE_L3);
        rule.setGradeName("重大");
        rule.setResponseMinutes(5);
        rule.setEscalateRoles(Arrays.asList(
                CcConstants.ROLE_DISPATCHER, CcConstants.ROLE_MANAGER, CcConstants.ROLE_ADMIN));
        rule.setReviewRole(CcConstants.ROLE_ADMIN);
        rule.setChangeReason("验收：温度超限调到临界 5 分钟");
        gradeService.saveRule(rule);

        // 连续超限：T0 起 9℃，T0+40 仍 9℃，持续 40 > 容忍 30 → 预警在 T0+40 触发
        report(batchId, T0, "9.0");
        report(batchId, T0.plusMinutes(40), "9.0");

        List<ExceptionEvent> excs = batchSvc.listExceptions(batchId);
        assertEquals(1, excs.size());
        ExceptionEvent tempExc = excs.get(0);
        // 同一口径：确认时刻=达到容忍时长的采样点 T0+40；首次发现=连续超限起点 T0；截止=T0+45
        assertEquals(CcConstants.GRADE_L3, tempExc.getGradeCode(), "取确认时生效的新分级（临界5分钟）");
        assertEquals(5, tempExc.getResponseMinutes());
        assertEquals(0, T0.plusMinutes(40).compareTo(tempExc.getConfirmedTime()));
        assertEquals(0, T0.compareTo(tempExc.getFirstFoundTime()));
        assertEquals(0, T0.plusMinutes(45).compareTo(tempExc.getClockDeadline()));

        // 处置时钟到期（临界 5 分钟）：T0+45 扫描出第 1 级升级 → 调度岗，发送成功
        Map<String, Object> at45 = handlingService.scanEscalations(T0.plusMinutes(45));
        assertEquals(1, ((Number) at45.get("raised")).intValue());
        List<ExceptionEscalation> escs = handlingService.listEscalations(tempExc.getId());
        assertEquals(1, escs.size());
        assertEquals(1, escs.get(0).getLevelSeq());
        assertEquals(CcConstants.ROLE_DISPATCHER, escs.get(0).getNotifyRole());
        assertEquals(CcConstants.ESCALATION_SUCCESS, escs.get(0).getSendResult());
        assertEquals(1, escs.get(0).getAttemptCount());

        // 仍未闭环：T0+60 第 2 级 → 经理；T0+75 第 3 级 → 管理员（每级间隔 15 分钟口径）
        handlingService.scanEscalations(T0.plusMinutes(60));
        handlingService.scanEscalations(T0.plusMinutes(75));
        escs = handlingService.listEscalations(tempExc.getId());
        assertEquals(3, escs.size());
        assertEquals(CcConstants.ROLE_MANAGER, escs.get(1).getNotifyRole());
        assertEquals(CcConstants.ROLE_ADMIN, escs.get(2).getNotifyRole());
        // 重启/重复扫描幂等：不再多发
        handlingService.scanEscalations(T0.plusMinutes(200));
        assertEquals(3, handlingService.listEscalations(tempExc.getId()).size());

        // 人工补录的另一起温度异常：确认时刻=登记时刻，与系统确认同一套时钟字段口径
        ExceptionMarkRequest manual = new ExceptionMarkRequest();
        manual.setBatchId(batchId);
        manual.setExceptionType(CcConstants.EXC_TEMP);
        manual.setDescription("人工复核确认后门封条处短暂温升");
        manual.setOccurredTime(T0.plusMinutes(50));
        manual.setFirstFoundTime(T0.plusMinutes(48));
        ExceptionEvent manualExc = batchSvc.markException(manual);
        assertNotNull(manualExc.getConfirmedTime());
        assertNotNull(manualExc.getClockDeadline());
        assertEquals(0, T0.plusMinutes(48).compareTo(manualExc.getFirstFoundTime()));
        assertEquals(5, manualExc.getResponseMinutes(), "与系统联动异常同一分级口径");
    }

    // ====================================================================
    // 4) 处置结论必须引用证据；指定角色复核（权限点 + 角色）
    // ====================================================================
    @Test
    void conclusionRequiresEvidence_reviewEnforcedByPermissionAndRole() {
        long nano = System.nanoTime();
        Long categoryId = createCategory("复核温区-" + nano, null);
        saveTemplate(categoryId, "2.00", "8.00", 30, 10);
        Long batchId = shippedBatchWithCategory(categoryId, "复核批次-" + nano);
        report(batchId, T0, "9.0");
        report(batchId, T0.plusMinutes(40), "9.0");
        Long excId = batchSvc.listExceptions(batchId).get(0).getId();
        Long tempLogId = batchSvc.listTemperatures(batchId).get(0).getId();

        // 4.1 无证据引用 → 拒绝
        ExceptionConclusionRequest noEvidence = new ExceptionConclusionRequest();
        noEvidence.setConclusion("口头说已处理");
        BusinessException ex = assertThrows(BusinessException.class,
                () -> handlingService.submitConclusion(excId, noEvidence));
        assertTrue(ex.getMessage().contains("必须引用证据"));

        // 4.2 引用他批温度留痕 → 拒绝
        Long otherBatch = simpleShippedBatch("他批-" + nano);
        TempReportRequest tr = new TempReportRequest();
        tr.setBatchId(otherBatch);
        tr.setRecordTime(T0.plusHours(1));
        tr.setTemperature(new BigDecimal("5.0"));
        tr.setRecordedBy("车载温控仪");
        final Long otherTempId = batchSvc.reportTemperature(tr).getId();

        BusinessException wrongOwner = assertThrows(BusinessException.class,
                () -> submitConclusion(excId, "引用他批证据", CcConstants.EVIDENCE_TEMPERATURE_LOG, otherTempId));
        assertTrue(wrongOwner.getMessage().contains("不归属本批次"));

        // 4.3 HTTP：dispatcher 有处置权可提交结论；但无复核权 → review 403
        ResponseEntity<String> submitted = exchange("/exceptions/" + excId + "/conclusion", HttpMethod.POST,
                conclusionBody("回温合格，附温度曲线", CcConstants.EVIDENCE_TEMPERATURE_LOG, tempLogId),
                "dispatcher");
        assertEquals(HttpStatus.OK, submitted.getStatusCode());
        assertTrue(submitted.getBody().contains("\"success\":true"));
        assertEquals(CcConstants.EXC_PENDING_REVIEW, handlingService.requireEvent(excId).getStatus());

        ResponseEntity<String> forbidden = exchange("/exceptions/" + excId + "/review", HttpMethod.POST,
                Collections.singletonMap("approved", true), "dispatcher");
        assertEquals(HttpStatus.FORBIDDEN, forbidden.getStatusCode());

        // 4.4 admin 具备复核角色 → 复核关闭
        ResponseEntity<String> reviewed = exchange("/exceptions/" + excId + "/review", HttpMethod.POST,
                Collections.singletonMap("approved", true), "admin");
        assertEquals(HttpStatus.OK, reviewed.getStatusCode());
        assertEquals(CcConstants.EXC_CLOSED, handlingService.requireEvent(excId).getStatus());
        assertNotNull(handlingService.requireEvent(excId).getCloseTime());

        // 4.5 驳回必须填原因，驳回后可补充证据再次提交
        ExceptionEvent delayExc = batchSvc.markException(delayMark(batchId, "缺材料的延误", T0));
        submitConclusion(delayExc.getId(), "先提交", CcConstants.EVIDENCE_TEMPERATURE_LOG, tempLogIdOf(batchId));
        ExceptionReviewRequest rejectNoReason = new ExceptionReviewRequest();
        rejectNoReason.setApproved(false);
        BusinessException rejectEx = assertThrows(BusinessException.class,
                () -> handlingService.review(delayExc.getId(), rejectNoReason));
        assertTrue(rejectEx.getMessage().contains("驳回原因"));
        // 经理具备延误复核角色（MANAGER），系统直调放行：驳回 → 再以凭证证据提交 → 关闭
        ExceptionReviewRequest reject = new ExceptionReviewRequest();
        reject.setApproved(false);
        reject.setRejectReason("缺少路面管制证明");
        handlingService.review(delayExc.getId(), reject);
        assertEquals(CcConstants.EXC_REVIEW_REJECTED, handlingService.requireEvent(delayExc.getId()).getStatus());
        ExceptionEvidence proof = appendEvidence(delayExc.getId(), "交管管制通报.pdf");
        submitConclusion(delayExc.getId(), "补充交管管制证明，确属不可抗力",
                CcConstants.EVIDENCE_EXCEPTION_FILE, proof.getId());
        ExceptionReviewRequest approve = new ExceptionReviewRequest();
        approve.setApproved(true);
        approve.setReviewRemark("证明有效");
        handlingService.review(delayExc.getId(), approve);
        assertEquals(CcConstants.EXC_CLOSED, handlingService.requireEvent(delayExc.getId()).getStatus());
    }

    // ====================================================================
    // 5) 未关闭异常不得结算；关闭并签收后方可
    // ====================================================================
    @Test
    void settlementBlockedUntilExceptionsClosedAndSigned() {
        long nano = System.nanoTime();
        Long categoryId = createCategory("结算温区-" + nano, null);
        saveTemplate(categoryId, "2.00", "8.00", 30, 10);
        Long batchId = shippedBatchWithCategory(categoryId, "结算批次-" + nano);
        report(batchId, T0, "9.0");
        report(batchId, T0.plusMinutes(40), "9.0");
        ExceptionEvent exc = batchSvc.listExceptions(batchId).get(0);
        TransportBatch batch = batchSvc.requireBatch(batchId);

        SettlementCreateRequest settlement = new SettlementCreateRequest();
        settlement.setBatchId(batchId);
        settlement.setAmount(new BigDecimal("1280.00"));

        // 未签收先拦
        BusinessException notSigned = assertThrows(BusinessException.class,
                () -> settlementService.create(settlement));
        assertTrue(notSigned.getMessage().contains("未签收"));

        // 签收也会被未关闭异常拦（签收前卡口）
        SignRequest sign = signReq(batchId, batch);
        BusinessException signBlocked = assertThrows(BusinessException.class, () -> batchSvc.sign(sign));
        assertTrue(signBlocked.getMessage().contains("未关闭异常"));

        // 关闭异常（温度留痕为证）后签收
        submitConclusion(exc.getId(), "回温评估合格",
                CcConstants.EVIDENCE_TEMPERATURE_LOG, tempLogIdOf(batchId));
        ExceptionReviewRequest review = new ExceptionReviewRequest();
        review.setApproved(true);
        handlingService.review(exc.getId(), review);
        batchSvc.sign(signReq(batchId, batch));

        // 进入结算成功；重复结算拒绝
        FreightSettlement created = settlementService.create(settlement);
        assertTrue(created.getSettlementNo().startsWith("JS"));
        assertEquals(0, new BigDecimal("1280.00").compareTo(created.getAmount()));
        BusinessException dup = assertThrows(BusinessException.class,
                () -> settlementService.create(settlement));
        assertTrue(dup.getMessage().contains("已存在结算单"));

        // 另一批：签收但遗留未关闭异常（新起一起延误，不闭环）→ 结算卡口指明异常号
        Long batch2 = simpleShippedBatch("结算拦截批-" + nano);
        batchSvc.markException(delayMark(batch2, "未处置完的延误", T0));
        // 该批无温度异常可直接签收（延误异常未关闭，应被拦）
        SignRequest sign2 = signReq(batch2, batchSvc.requireBatch(batch2));
        BusinessException blocked2 = assertThrows(BusinessException.class, () -> batchSvc.sign(sign2));
        assertTrue(blocked2.getMessage().contains("未关闭异常"));
    }

    // ====================================================================
    // 6) 升级通知发送失败：发送结果/首发与重试原因留痕，重试成功不覆盖首发原因
    // ====================================================================
    @Test
    void escalationFailure_recordsResultAndRetryReason_untilRetrySuccess() throws Exception {
        // 延误规则设为 0 分钟响应、仅升级调度岗（本用例自洽）
        publishDelayRule(CcConstants.GRADE_L1, "一般", 0,
                Collections.singletonList(CcConstants.ROLE_DISPATCHER), CcConstants.ROLE_MANAGER);

        setStationFail(true);
        try {
            Long batchId = simpleShippedBatch("失败重试-" + System.nanoTime());
            ExceptionEvent exc = batchSvc.markException(delayMark(batchId, "需要立即升级的延误", T0));
            LocalDateTime t = LocalDateTime.now().plusMinutes(2); // 超过 deadline(=确认时刻，0分钟时限)

            // 首发：通道失败 -> FAILED，记录首发失败原因，尝试 1 次
            handlingService.scanEscalations(t);
            ExceptionEscalation esc = handlingService.listEscalations(exc.getId()).get(0);
            Long escId = esc.getId();
            assertEquals(CcConstants.ESCALATION_FAILED, esc.getSendResult());
            assertEquals(1, esc.getAttemptCount());
            assertNotNull(esc.getLastRetryReason());
            assertTrue(esc.getLastRetryReason().contains("首发失败"));
            assertTrue(esc.getRetryReasonLog().contains("FAILED"));
            assertTrue(esc.getRetryReasonLog().contains("首发"));

            // 未到自动重试间隔（5 分钟）再扫：不重发，尝试次数不变
            handlingService.scanEscalations(t);
            assertEquals(1, handlingService.requireEscalation(escId).getAttemptCount());

            // 到间隔后自动重发，通道仍失败：尝试 2 次，原因日志追加（首发记录仍在）
            handlingService.scanEscalations(t.plusMinutes(6));
            esc = handlingService.requireEscalation(escId);
            assertEquals(2, esc.getAttemptCount());
            assertEquals(CcConstants.ESCALATION_FAILED, esc.getSendResult());
            assertTrue(esc.getRetryReasonLog().contains("首发"), "首发失败记录不得被覆盖");
            assertEquals(2, countOccurrences(esc.getRetryReasonLog(), "FAILED"),
                    "首发与第二次失败两条记录都应保留");
            assertTrue(esc.getLastRetryReason().contains("重试仍失败"));

            // 人工重试不受间隔限制：通道恢复 -> SUCCESS，尝试 3 次，三次尝试日志全部保留
            setStationFail(false);
            ExceptionEscalation retried = handlingService.retryEscalation(escId);
            assertEquals(CcConstants.ESCALATION_SUCCESS, retried.getSendResult());
            assertEquals(3, retried.getAttemptCount());
            assertTrue(retried.getRetryReasonLog().contains("首发"), "历史失败记录完整保留");
            assertEquals(2, countOccurrences(retried.getRetryReasonLog(), "FAILED"));
            assertTrue(retried.getRetryReasonLog().contains("SUCCESS"), "成功结果追加留痕");

            // 已送达再重试 -> 拒绝
            BusinessException again = assertThrows(BusinessException.class,
                    () -> handlingService.retryEscalation(escId));
            assertTrue(again.getMessage().contains("已送达成功"));
        } finally {
            setStationFail(false);
        }
    }

    private void setStationFail(boolean fail) throws Exception {
        java.lang.reflect.Field f = com.evops.coldchain.service.StationNotificationSender.class
                .getDeclaredField("forceFail");
        f.setAccessible(true);
        f.setBoolean(stationSender, fail);
    }

    private int countOccurrences(String text, String token) {
        if (text == null || token == null || token.isEmpty()) {
            return 0;
        }
        int count = 0, idx = 0;
        while ((idx = text.indexOf(token, idx)) >= 0) {
            count++;
            idx += token.length();
        }
        return count;
    }

    // ---------------- 建链 / 请求辅助 ----------------

    private Long simpleShippedBatch(String uniq) {
        DrugCreateRequest dc = new DrugCreateRequest();
        dc.setDrugName(uniq);
        dc.setApprovalNo("国药准字V6-" + Math.abs(uniq.hashCode() % 1000000));
        dc.setTempMin(new BigDecimal("2.00"));
        dc.setTempMax(new BigDecimal("8.00"));
        dc.setPackageSpec("1支/盒");
        Long drugId = drugService.create(dc).getId();

        OrderCreateRequest oc = new OrderCreateRequest();
        oc.setDrugId(drugId);
        oc.setReceiverOrg("V6验收医院");
        oc.setShipQty(10);
        Long orderId = orderService.create(oc).getId();

        Long vehicleId = createVehicle(uniq);
        bindProbe(vehicleId, "SN-V6-" + Math.abs(uniq.hashCode()));

        DispatchRequest dr = new DispatchRequest();
        dr.setOrderId(orderId);
        dr.setVehicleId(vehicleId);
        Long batchId = batchSvc.dispatch(dr).getId();
        batchSvc.ship(batchId);
        return batchId;
    }

    private Long shippedBatchWithCategory(Long categoryId, String uniq) {
        DrugCreateRequest dc = new DrugCreateRequest();
        dc.setDrugName(uniq);
        dc.setApprovalNo("国药准字V6C-" + Math.abs(uniq.hashCode() % 1000000));
        dc.setTempMin(new BigDecimal("2.00"));
        dc.setTempMax(new BigDecimal("8.00"));
        dc.setPackageSpec("1支/盒");
        dc.setCategoryId(categoryId);
        Long drugId = drugService.create(dc).getId();

        OrderCreateRequest oc = new OrderCreateRequest();
        oc.setDrugId(drugId);
        oc.setReceiverOrg("V6临界医院");
        oc.setShipQty(10);
        Long orderId = orderService.create(oc).getId();

        Long vehicleId = createVehicle(uniq);
        bindProbe(vehicleId, "SN-V6C-" + Math.abs(uniq.hashCode()));

        DispatchRequest dr = new DispatchRequest();
        dr.setOrderId(orderId);
        dr.setVehicleId(vehicleId);
        Long batchId = batchSvc.dispatch(dr).getId();
        batchSvc.ship(batchId);
        return batchId;
    }

    private Long createCategory(String name, Long parentId) {
        com.evops.coldchain.dto.CategoryCreateRequest req =
                new com.evops.coldchain.dto.CategoryCreateRequest();
        req.setCategoryName(name);
        req.setParentId(parentId);
        return categoryService.createCategory(req).getId();
    }

    private void saveTemplate(Long categoryId, String min, String max, int tolerate, int jitter) {
        TemplateSaveRequest req = new TemplateSaveRequest();
        req.setInheritParent(true);
        req.setChangeReason("V6 临界验收模板");
        ZoneDraftRequest z = new ZoneDraftRequest();
        z.setZoneCode(CcConstants.ZONE_REFRIGERATE);
        z.setZoneName(CcConstants.ZONE_REFRIGERATE_NAME);
        z.setTempMin(new BigDecimal(min));
        z.setTempMax(new BigDecimal(max));
        z.setTolerateMinutes(tolerate);
        z.setJitterGapMinutes(jitter);
        req.setZones(Collections.singletonList(z));
        categoryService.saveTemplate(categoryId, req);
    }

    private Long createVehicle(String uniq) {
        VehicleCreateRequest vr = new VehicleCreateRequest();
        vr.setPlateNo("测6·冷" + Math.abs(uniq.hashCode() % 1000000));
        vr.setCarrierName("V6物流");
        vr.setDriverName("赵司机");
        return vehicleService.create(vr).getId();
    }

    private void bindProbe(Long vehicleId, String serial) {
        ProbeCreateRequest pc = new ProbeCreateRequest();
        pc.setSerialNo(serial);
        pc.setModel("TP-300");
        pc.setRangeMin(new BigDecimal("-40.00"));
        pc.setRangeMax(new BigDecimal("60.00"));
        pc.setCalibrateOrg("华东计量院");
        pc.setValidUntil(T0.plusYears(1));
        pc.setCertNo("CERT-" + serial);
        pc.setCalibrateDate(T0.minusDays(1));
        pc.setCertFileName(serial + ".txt");
        pc.setCertFileContent("cert " + serial);
        Long probeId = probeService.createProbe(pc).getId();

        ProbeBindRequest pb = new ProbeBindRequest();
        pb.setRequestId("bind-" + serial);
        pb.setVehicleId(vehicleId);
        pb.setProbeId(probeId);
        probeService.bindIdempotent(pb);
    }

    private void report(Long batchId, LocalDateTime time, String temp) {
        TempReportRequest req = new TempReportRequest();
        req.setBatchId(batchId);
        req.setRecordTime(time);
        req.setTemperature(new BigDecimal(temp));
        req.setRecordedBy("车载温控仪");
        batchSvc.reportTemperature(req);
    }

    private ExceptionMarkRequest delayMark(Long batchId, String desc, LocalDateTime found) {
        ExceptionMarkRequest mark = new ExceptionMarkRequest();
        mark.setBatchId(batchId);
        mark.setExceptionType(CcConstants.EXC_DELAY);
        mark.setDescription(desc);
        mark.setOccurredTime(found);
        mark.setFirstFoundTime(found);
        return mark;
    }

    private ExceptionEvidence appendEvidence(Long exceptionId, String fileName) {
        com.evops.coldchain.dto.EvidenceAppendRequest req =
                new com.evops.coldchain.dto.EvidenceAppendRequest();
        req.setExceptionId(exceptionId);
        req.setFileName(fileName);
        req.setFileUrl("/v6/" + fileName);
        req.setNote("V6 验收凭证");
        return batchSvc.appendEvidence(req);
    }

    private void submitConclusion(Long excId, String conclusion, String evidenceType, Long evidenceId) {
        ExceptionConclusionRequest req = new ExceptionConclusionRequest();
        req.setConclusion(conclusion);
        ExceptionConclusionRequest.EvidenceRef ref = new ExceptionConclusionRequest.EvidenceRef();
        ref.setEvidenceType(evidenceType);
        ref.setEvidenceId(evidenceId);
        ref.setNote("结论证据");
        req.setEvidences(Collections.singletonList(ref));
        handlingService.submitConclusion(excId, req);
    }

    private Map<String, Object> conclusionBody(String conclusion, String evidenceType, Long evidenceId) {
        java.util.Map<String, Object> ref = new java.util.LinkedHashMap<>();
        ref.put("evidenceType", evidenceType);
        ref.put("evidenceId", evidenceId);
        ref.put("note", "HTTP 提交证据");
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("conclusion", conclusion);
        body.put("evidences", Collections.singletonList(ref));
        return body;
    }

    private Long tempLogIdOf(Long batchId) {
        List<TemperatureLog> temps = batchSvc.listTemperatures(batchId);
        assertFalse(temps.isEmpty(), "该批应有温度留痕可作证据");
        return temps.get(0).getId();
    }

    private SignRequest signReq(Long batchId, TransportBatch batch) {
        SignRequest req = new SignRequest();
        req.setBatchId(batchId);
        req.setSignBy("V6收货人");
        req.setReceivedApprovalNo(batch.getApprovalNoSnap());
        req.setReceivedPackageSpec(batch.getPackageSpecSnap());
        req.setConfirmQty(batch.getShipQty());
        return req;
    }

    private ResponseEntity<String> exchange(String path, HttpMethod method, Object body, String user) {
        String url = "http://localhost:" + port + "/api/cold-chain" + path;
        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(user, user + "123");
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(url, method, new HttpEntity<>(body, headers), String.class);
    }
}
