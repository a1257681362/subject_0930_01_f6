package com.evops.coldchain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.coldchain.CcConstants;
import com.evops.coldchain.dto.ExceptionConclusionRequest;
import com.evops.coldchain.dto.ExceptionReviewRequest;
import com.evops.coldchain.entity.ExceptionEscalation;
import com.evops.coldchain.entity.ExceptionEvent;
import com.evops.coldchain.entity.ExceptionEvidence;
import com.evops.coldchain.entity.ExceptionEvidenceRef;
import com.evops.coldchain.entity.ExceptionGradeRule;
import com.evops.coldchain.entity.HandoverOrder;
import com.evops.coldchain.entity.HandoverSeal;
import com.evops.coldchain.entity.TempAlert;
import com.evops.coldchain.entity.TemperatureLog;
import com.evops.coldchain.mapper.ExceptionEscalationMapper;
import com.evops.coldchain.mapper.ExceptionEventMapper;
import com.evops.coldchain.mapper.ExceptionEvidenceMapper;
import com.evops.coldchain.mapper.ExceptionEvidenceRefMapper;
import com.evops.coldchain.mapper.HandoverOrderMapper;
import com.evops.coldchain.mapper.HandoverSealMapper;
import com.evops.coldchain.mapper.TempAlertMapper;
import com.evops.coldchain.mapper.TemperatureLogMapper;
import com.evops.common.BusinessException;
import com.evops.common.UserContext;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 运输异常分级处置服务（V6）。
 *
 * 处置时钟口径（人工登记与系统自动确认同一口径）：
 * - confirmedTime=异常“确认成立”的业务时刻：人工登记=入库确认时刻；温度超限=连续超限达到
 *   容忍时长、系统确认预警/越过缓冲的采样点时刻。时钟自该时刻启动，deadline=confirmed+响应时限。
 * - firstFoundTime=首次发现时间（可由发现人补录，可早于确认时刻）；confirmedTime/firstFoundTime/
 *   deadline 只在首次插入写入一次，后续补录凭证、补报温度、定时重扫均绝不覆盖。
 *
 * 升级：响应时限到期未提交有效处置结论的异常，按分级快照中的岗位序列逐级升级，
 * 每级一行 {@link ExceptionEscalation}；发送结果与历次重试原因完整留痕，失败幂等重试。
 *
 * 闭环：处置结论必须引用系统内已留存证据；提交后 PENDING_REVIEW，由分级规则指定角色复核
 * 通过才 CLOSED。结算/签收卡口只认 CLOSED（历史 RESOLVED 同视为已关闭）。
 */
@Service
public class ExceptionHandlingService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 仍在处置时钟约束下（未提交有效结论）的状态：到期要继续升级 */
    private static final List<String> CLOCK_ACTIVE = Arrays.asList(
            CcConstants.EXC_OPEN, CcConstants.EXC_REVIEW_REJECTED);

    @Resource
    private ExceptionEventMapper exceptionMapper;
    @Resource
    private ExceptionGradeService gradeService;
    @Resource
    private ExceptionEscalationMapper escalationMapper;
    @Resource
    private ExceptionEvidenceRefMapper evidenceRefMapper;
    @Resource
    private ExceptionEvidenceMapper evidenceMapper;
    @Resource
    private TemperatureLogMapper temperatureMapper;
    @Resource
    private TempAlertMapper alertMapper;
    @Resource
    private HandoverSealMapper handoverSealMapper;
    @Resource
    private HandoverOrderMapper handoverOrderMapper;
    @Resource
    private NotificationSender notificationSender;
    @Resource
    private OpLogService opLogService;

    /** 逐级升级间隔（分钟）：1 级在响应时限到期发送，之后每隔该间隔升一级 */
    @Value("${coldchain.escalation.escalate-interval-minutes:15}")
    private long escalateIntervalMinutes;
    /** 失败通知的自动重试间隔（分钟）；手动重试不受此间隔限制 */
    @Value("${coldchain.escalation.retry-interval-minutes:5}")
    private long retryIntervalMinutes;

    // ============================ 处置时钟 ============================

    /**
     * 异常确认即启动处置时钟：取当时生效分级规则逐字段快照，计算截止时间。
     * 幂等：confirmed_time 已存在（重复确认/重扫）时原样保留，绝不覆盖首次发现时间/时钟起点。
     *
     * @param event          已落库（有 id）的异常事件
     * @param confirmedTime  异常确认成立的业务时刻
     * @param firstFoundTime 首次发现时间（补录）；为空取确认时刻
     * @return 同一异常事件（已写分级快照与时钟字段）
     */
    @Transactional
    public ExceptionEvent startClockOnConfirm(ExceptionEvent event, LocalDateTime confirmedTime,
                                              LocalDateTime firstFoundTime) {
        if (event.getId() == null) {
            throw new BusinessException("异常事件尚未落库，不能启动处置时钟");
        }
        // 唯一写入口：时钟一旦启动，任何补录/重报/重扫都不得覆盖
        if (event.getConfirmedTime() != null) {
            return event;
        }
        LocalDateTime confirmAt = confirmedTime == null ? LocalDateTime.now() : confirmedTime;

        ExceptionGradeRule rule = gradeService.effectiveRule(event.getExceptionType());
        if (rule == null || !Boolean.TRUE.equals(rule.getEnabled())) {
            // 未配置/停用分级的类型不启时钟（如历史 DAMAGE/OTHER），异常仍按 OPEN 挂起
            event.setConfirmedTime(confirmAt);
            event.setFirstFoundTime(firstFoundTime == null ? confirmAt : firstFoundTime);
            exceptionMapper.updateById(event);
            opLogService.log(CcConstants.ENT_EXCEPTION, event.getId(), event.getBatchId(),
                    CcConstants.ACT_EXC_CLOCK_START,
                    String.format("异常#%d[%s]于 %s 确认（首次发现=%s），该类型未启用分级处置时钟，按常规异常挂起",
                            event.getId(), event.getExceptionType(), confirmAt,
                            firstFoundTime == null ? confirmAt : firstFoundTime));
            return event;
        }

        event.setGradeCode(rule.getGradeCode());
        event.setGradeName(rule.getGradeName());
        event.setGradeRuleVersion(rule.getVersionNo());
        event.setResponseMinutes(rule.getResponseMinutes());
        event.setEscalateRoles(rule.getEscalateRoles());
        event.setReviewRole(rule.getReviewRole());
        event.setConfirmedTime(confirmAt);
        event.setFirstFoundTime(firstFoundTime == null ? confirmAt : firstFoundTime);
        event.setClockDeadline(confirmAt.plusMinutes(rule.getResponseMinutes()));
        event.setEscalationLevel(0);
        exceptionMapper.updateById(event);

        opLogService.log(CcConstants.ENT_EXCEPTION, event.getId(), event.getBatchId(),
                CcConstants.ACT_EXC_CLOCK_START,
                String.format("异常#%d[%s]确认即启动处置时钟：等级=%s/%s（规则V%d快照），响应时限=%d分钟，"
                                + "确认时刻=%s，首次发现=%s，处置截止=%s，升级岗位=%s，复核角色=%s",
                        event.getId(), event.getExceptionType(), rule.getGradeCode(), rule.getGradeName(),
                        rule.getVersionNo(), rule.getResponseMinutes(), confirmAt,
                        event.getFirstFoundTime(), event.getClockDeadline(),
                        gradeService.parseRoles(rule.getEscalateRoles()), rule.getReviewRole()));
        return event;
    }

    // ============================ 升级通知 ============================

    /**
     * 定时扫描：① 到期未闭环异常逐级升级；② 到重试间隔的失败通知自动重发。
     * 全程幂等：级次唯一约束兜底并发/重启，不多发；已关闭异常不再产生升级。
     */
    @Transactional
    public Map<String, Object> scanEscalations() {
        return scanEscalations(LocalDateTime.now());
    }

    /** 可注入时钟的扫描（测试用） */
    @Transactional
    public Map<String, Object> scanEscalations(LocalDateTime now) {
        int raised = 0, retried = 0, failed = 0;

        // ① 逐级升级：仍在处置时钟约束下（OPEN/驳回）且已过截止时间的异常
        List<ExceptionEvent> active = exceptionMapper.selectList(new QueryWrapper<ExceptionEvent>()
                .in("status", CLOCK_ACTIVE)
                .isNotNull("clock_deadline")
                .le("clock_deadline", now));
        for (ExceptionEvent event : active) {
            List<String> roles = gradeService.parseRoles(event.getEscalateRoles());
            int level = event.getEscalationLevel() == null ? 0 : event.getEscalationLevel();
            while (level < roles.size()) {
                int nextLevel = level + 1;
                LocalDateTime due = event.getClockDeadline().plusMinutes((nextLevel - 1) * escalateIntervalMinutes);
                if (due.isAfter(now)) {
                    break; // 下一级未到点
                }
                ExceptionEscalation esc = raiseLevel(event, roles.get(nextLevel - 1), nextLevel, due, now);
                level = nextLevel;
                raised++;
                if (CcConstants.ESCALATION_FAILED.equals(esc.getSendResult())) {
                    failed++;
                }
            }
        }

        // ② 失败通知自动重试（未关闭异常；已 CLOSED 的不再打扰）
        List<ExceptionEscalation> pending = escalationMapper.selectList(new QueryWrapper<ExceptionEscalation>()
                .eq("send_result", CcConstants.ESCALATION_FAILED));
        for (ExceptionEscalation esc : pending) {
            ExceptionEvent event = exceptionMapper.selectById(esc.getExceptionId());
            if (event == null || isClosed(event.getStatus())) {
                continue;
            }
            LocalDateTime base = esc.getLastRetryTime() != null ? esc.getLastRetryTime()
                    : (esc.getSendTime() != null ? esc.getSendTime() : esc.getCreateTime());
            if (base != null && base.plusMinutes(retryIntervalMinutes).isAfter(now)) {
                continue; // 未到自动重试间隔（手动重试不受限）
            }
            retrySend(esc, event, "到达自动重试间隔（" + retryIntervalMinutes + " 分钟）后重发", now);
            retried++;
            if (CcConstants.ESCALATION_FAILED.equals(esc.getSendResult())) {
                failed++;
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("raised", raised);
        summary.put("retried", retried);
        summary.put("failed", failed);
        summary.put("scanTime", now);
        summary.put("operator", UserContext.currentName());
        return summary;
    }

    /** 产生某一级升级通知（唯一约束兜底重复）并立即尝试首发 */
    private ExceptionEscalation raiseLevel(ExceptionEvent event, String role, int level,
                                           LocalDateTime due, LocalDateTime now) {
        ExceptionEscalation exist = escalationMapper.selectOne(new QueryWrapper<ExceptionEscalation>()
                .eq("exception_id", event.getId()).eq("level_seq", level).last("LIMIT 1"));
        if (exist != null) {
            // 并发/重启兜底：级次行已存在，只同步异常上的最高级次
            syncEventLevel(event, level);
            return exist;
        }
        ExceptionEscalation esc = new ExceptionEscalation();
        esc.setExceptionId(event.getId());
        esc.setBatchId(event.getBatchId());
        esc.setLevelSeq(level);
        esc.setGradeCode(event.getGradeCode());
        esc.setNotifyRole(role);
        esc.setDueTime(due);
        esc.setAttemptCount(0);
        esc.setSendResult(CcConstants.ESCALATION_PENDING);
        try {
            escalationMapper.insert(esc);
        } catch (DuplicateKeyException dup) {
            ExceptionEscalation row = escalationMapper.selectOne(new QueryWrapper<ExceptionEscalation>()
                    .eq("exception_id", event.getId()).eq("level_seq", level).last("LIMIT 1"));
            syncEventLevel(event, level);
            return row;
        }

        attemptSend(esc, event, role, false, null, now);
        syncEventLevel(event, level);

        opLogService.log(CcConstants.ENT_ESCALATION, esc.getId(), event.getBatchId(),
                CcConstants.ACT_ESCALATION_SEND,
                String.format("异常#%d[%s/%s]处置时限到期，第%d级升级通知→岗位 %s（计划=%s）：发送结果=%s%s",
                        event.getId(), event.getExceptionType(), event.getGradeCode(), level, role, due,
                        esc.getSendResult(),
                        CcConstants.ESCALATION_SUCCESS.equals(esc.getSendResult())
                                ? "，回执=" + esc.getChannelResponse()
                                : "，失败原因=" + esc.getLastRetryReason()));
        return esc;
    }

    private void syncEventLevel(ExceptionEvent event, int level) {
        int current = event.getEscalationLevel() == null ? 0 : event.getEscalationLevel();
        if (level > current) {
            event.setEscalationLevel(level);
            exceptionMapper.updateById(event);
        }
    }

    /** 手动重试一条升级通知（不受自动重试间隔限制；已送达/已关闭异常拒绝） */
    @Transactional
    public ExceptionEscalation retryEscalation(Long escalationId) {
        ExceptionEscalation esc = requireEscalation(escalationId);
        ExceptionEvent event = exceptionMapper.selectById(esc.getExceptionId());
        if (event != null && isClosed(event.getStatus())) {
            throw new BusinessException("异常#" + event.getId() + " 已复核关闭，无需重发升级通知");
        }
        if (CcConstants.ESCALATION_SUCCESS.equals(esc.getSendResult())) {
            throw new BusinessException("升级通知#" + escalationId + " 已送达成功（" + esc.getChannelResponse() + "），无需重试");
        }
        retrySend(esc, event, "人工触发重试（操作者=" + UserContext.currentName() + "）", LocalDateTime.now());

        opLogService.log(CcConstants.ENT_ESCALATION, esc.getId(), esc.getBatchId(),
                CcConstants.ACT_ESCALATION_RETRY,
                String.format("异常#%d 第%d级升级通知人工重试：结果=%s，累计尝试=%d%s",
                        esc.getExceptionId(), esc.getLevelSeq(), esc.getSendResult(), esc.getAttemptCount(),
                        CcConstants.ESCALATION_FAILED.equals(esc.getSendResult())
                                ? "，仍失败原因=" + esc.getLastRetryReason() : "，已送达"));
        return esc;
    }

    private void retrySend(ExceptionEscalation esc, ExceptionEvent event, String triggerReason, LocalDateTime now) {
        attemptSend(esc, event, esc.getNotifyRole(), true, triggerReason, now);
    }

    /**
     * 一次发送尝试：更新发送结果/通道回执/尝试次数/重试原因。
     * 历次尝试结果（含首发失败原因）追加进 retryReasonLog，永不覆盖。
     */
    private void attemptSend(ExceptionEscalation esc, ExceptionEvent event, String role,
                             boolean retry, String triggerReason, LocalDateTime now) {
        String title = String.format("[冷链异常升级-L%d] %s 批次异常#%d",
                esc.getLevelSeq(), event.getGradeCode(), event.getId());
        String content = String.format(
                "异常#%d（批次#%d，类型=%s，等级=%s/%s）处置时钟已到期。确认=%s，首次发现=%s，截止=%s，"
                        + "当前状态=%s，级次=%d，通知岗位=%s。描述：%s",
                event.getId(), event.getBatchId(), event.getExceptionType(),
                event.getGradeCode(), event.getGradeName(),
                event.getConfirmedTime(), event.getFirstFoundTime(), event.getClockDeadline(),
                event.getStatus(), esc.getLevelSeq(), role, event.getDescription());

        NotificationSender.SendOutcome outcome = notificationSender.send(role, title, content);

        esc.setAttemptCount((esc.getAttemptCount() == null ? 0 : esc.getAttemptCount()) + 1);
        esc.setSendTime(now);
        esc.setChannel(outcome.getChannel());
        esc.setChannelResponse(outcome.getResponse());
        String stamp = "[" + now.format(TS) + "]" + (retry ? "第" + esc.getAttemptCount() + "次尝试" : "首发");
        if (outcome.isSuccess()) {
            esc.setSendResult(CcConstants.ESCALATION_SUCCESS);
            if (retry) {
                esc.setLastRetryTime(now);
                esc.setLastRetryReason("重试成功（" + triggerReason + "）");
            }
            esc.setRetryReasonLog(appendLog(esc.getRetryReasonLog(),
                    stamp + " SUCCESS 回执=" + outcome.getResponse()
                            + (retry ? "（" + triggerReason + "）" : "")));
        } else {
            esc.setSendResult(CcConstants.ESCALATION_FAILED);
            esc.setLastRetryTime(now);
            esc.setLastRetryReason((retry ? "重试仍失败：" : "首发失败：") + outcome.getFailReason()
                    + (retry ? "（" + triggerReason + "）" : ""));
            esc.setRetryReasonLog(appendLog(esc.getRetryReasonLog(),
                    stamp + " FAILED 原因=" + outcome.getFailReason()
                            + (retry ? "（" + triggerReason + "）" : "")));
        }
        escalationMapper.updateById(esc);
    }

    private String appendLog(String log, String line) {
        return (log == null || log.isEmpty()) ? line : log + "\n" + line;
    }

    // ============================ 处置结论 + 证据引用 ============================

    /**
     * 提交处置结论：必须引用至少一条系统内已留存证据，且证据须归属本异常/本批次。
     * 提交后进入 PENDING_REVIEW，由分级规则指定角色复核；驳回后可补充证据再次提交。
     */
    @Transactional
    public ExceptionEvent submitConclusion(Long exceptionId, ExceptionConclusionRequest req) {
        ExceptionEvent event = requireEvent(exceptionId);
        String status = event.getStatus();
        if (CcConstants.EXC_PENDING_REVIEW.equals(status)) {
            throw new BusinessException("异常#" + exceptionId + " 已提交待复核，不能重复提交，请等待复核结论");
        }
        if (isClosed(status)) {
            throw new BusinessException("异常#" + exceptionId + " 已关闭，不能再提交处置结论");
        }
        if (req.getEvidences() == null || req.getEvidences().isEmpty()) {
            throw new BusinessException("处置结论必须引用证据：至少引用一条系统内已留存证据"
                    + "（温度留痕/异常凭证/温控预警/封签核验/交接单）");
        }

        // 逐条校验证据真实存在且归属本异常/本批次，防止引用凭空ID或他批证据
        List<ExceptionEvidenceRef> refs = new ArrayList<>();
        for (ExceptionConclusionRequest.EvidenceRef ref : req.getEvidences()) {
            validateEvidenceBelongs(event, ref);
            ExceptionEvidenceRef row = new ExceptionEvidenceRef();
            row.setExceptionId(event.getId());
            row.setEvidenceType(ref.getEvidenceType().trim().toUpperCase());
            row.setEvidenceId(ref.getEvidenceId());
            row.setNote(ref.getNote());
            try {
                evidenceRefMapper.insert(row);
            } catch (DuplicateKeyException dup) {
                throw new BusinessException(String.format("证据已引用，请勿重复添加：%s#%d",
                        row.getEvidenceType(), row.getEvidenceId()));
            }
            refs.add(row);
        }

        LocalDateTime now = LocalDateTime.now();
        // 结论以追加方式保留：驳回后再次提交不覆盖首次结论
        String conclusion = req.getConclusion().trim();
        String merged = event.getResolveNote() == null || event.getResolveNote().isEmpty()
                ? conclusion
                : event.getResolveNote() + "\n【再次提交 " + now.format(TS) + "】" + conclusion;
        event.setResolveNote(merged);
        event.setResolveBy(UserContext.currentId());
        event.setResolveTime(now);
        event.setSubmittedTime(now);
        event.setStatus(CcConstants.EXC_PENDING_REVIEW);
        exceptionMapper.updateById(event);

        opLogService.log(CcConstants.ENT_EXCEPTION, event.getId(), event.getBatchId(),
                CcConstants.ACT_EXCEPTION_SUBMIT,
                String.format("异常#%d[%s]提交处置结论待复核：%s；引用证据 %d 条%s，复核角色=%s",
                        event.getId(), event.getExceptionType(), conclusion, refs.size(),
                        refs.stream().map(r -> r.getEvidenceType() + "#" + r.getEvidenceId())
                                .collect(java.util.stream.Collectors.joining(",", "[", "]")),
                        event.getReviewRole() == null ? "（未分级，管理员兜底）" : event.getReviewRole()));
        return event;
    }

    private void validateEvidenceBelongs(ExceptionEvent event, ExceptionConclusionRequest.EvidenceRef ref) {
        if (ref == null || ref.getEvidenceType() == null || ref.getEvidenceId() == null) {
            throw new BusinessException("证据引用不完整：类型与ID必填");
        }
        String type = ref.getEvidenceType().trim().toUpperCase();
        Long id = ref.getEvidenceId();
        switch (type) {
            case CcConstants.EVIDENCE_TEMPERATURE_LOG: {
                TemperatureLog log = temperatureMapper.selectById(id);
                if (log == null || !event.getBatchId().equals(log.getBatchId())) {
                    throw new BusinessException("证据不存在或不归属本批次：温度留痕#" + id);
                }
                break;
            }
            case CcConstants.EVIDENCE_EXCEPTION_FILE: {
                ExceptionEvidence e = evidenceMapper.selectById(id);
                if (e == null || !event.getId().equals(e.getExceptionId())) {
                    throw new BusinessException("证据不存在或不归属本异常：异常凭证#" + id);
                }
                break;
            }
            case CcConstants.EVIDENCE_TEMP_ALERT: {
                TempAlert a = alertMapper.selectById(id);
                if (a == null || !event.getBatchId().equals(a.getBatchId())) {
                    throw new BusinessException("证据不存在或不归属本批次：温控预警#" + id);
                }
                break;
            }
            case CcConstants.EVIDENCE_SEAL: {
                HandoverSeal s = handoverSealMapper.selectById(id);
                if (s == null || !event.getBatchId().equals(s.getBatchId())) {
                    throw new BusinessException("证据不存在或不归属本批次：封签核验#" + id);
                }
                break;
            }
            case CcConstants.EVIDENCE_HANDOVER: {
                HandoverOrder h = handoverOrderMapper.selectById(id);
                if (h == null || !event.getBatchId().equals(h.getBatchId())) {
                    throw new BusinessException("证据不存在或不归属本批次：交接单#" + id);
                }
                break;
            }
            default:
                throw new BusinessException("不支持的证据类型: " + type
                        + "（仅支持 TEMPERATURE_LOG/EVIDENCE/TEMP_ALERT/SEAL/HANDOVER）");
        }
    }

    // ============================ 复核关闭 ============================

    /**
     * 指定角色复核：通过 → CLOSED；驳回 → REVIEW_REJECTED（须填原因，结论与证据原样保留可再提交）。
     * 角色取自异常分级快照 reviewRole；无分级快照时由 ADMIN 兜底。
     * Shiro 环境外的系统直调（定时任务/服务测试，主体 system）视为系统放行。
     */
    @Transactional
    public ExceptionEvent review(Long exceptionId, ExceptionReviewRequest req) {
        ExceptionEvent event = requireEvent(exceptionId);
        if (!CcConstants.EXC_PENDING_REVIEW.equals(event.getStatus())) {
            throw new BusinessException("异常#" + exceptionId + " 当前状态 " + event.getStatus()
                    + "，不允许复核（仅待复核异常可复核）");
        }
        requireReviewerRole(event.getReviewRole());

        LocalDateTime now = LocalDateTime.now();
        event.setReviewBy(UserContext.currentId());
        event.setReviewTime(now);
        event.setReviewRemark(req.getReviewRemark());
        boolean approved = Boolean.TRUE.equals(req.getApproved());
        if (approved) {
            event.setStatus(CcConstants.EXC_CLOSED);
            event.setCloseTime(now);
            event.setReviewRejectReason(null);
        } else {
            if (req.getRejectReason() == null || req.getRejectReason().trim().isEmpty()) {
                throw new BusinessException("复核驳回必须填写驳回原因");
            }
            event.setStatus(CcConstants.EXC_REVIEW_REJECTED);
            event.setReviewRejectReason(req.getRejectReason().trim());
        }
        exceptionMapper.updateById(event);

        opLogService.log(CcConstants.ENT_EXCEPTION, event.getId(), event.getBatchId(),
                CcConstants.ACT_EXCEPTION_REVIEW,
                approved
                        ? String.format("异常#%d 经角色 %s 复核通过关闭：%s（处置结论与 %d 条证据引用留存）",
                                event.getId(), event.getReviewRole(),
                                req.getReviewRemark() == null ? "" : req.getReviewRemark(),
                                countRefs(event.getId()))
                        : String.format("异常#%d 经角色 %s 复核驳回：%s（结论/证据保留，退回补充后再提交）",
                                event.getId(), event.getReviewRole(), event.getReviewRejectReason()));
        return event;
    }

    /** 校验当前登录主体具备复核角色；系统主体（无 Shiro 环境）放行 */
    private void requireReviewerRole(String requiredRole) {
        if (!currentPrincipalCanReview(requiredRole)) {
            String role = requiredRole == null ? CcConstants.ROLE_ADMIN : requiredRole;
            throw new BusinessException("关闭复核需要岗位角色 " + role + "，当前账号不具备该角色，不能复核关闭");
        }
    }

    /**
     * 当前主体是否具备复核权限：无 Shiro 主体（系统直调/定时任务）放行；
     * 具备指定角色或 ADMIN 放行。供“预警闭环联动异常”判断能否当场代为复核。
     */
    public boolean currentPrincipalCanReview(String requiredRole) {
        String role = requiredRole == null ? CcConstants.ROLE_ADMIN : requiredRole;
        try {
            Subject subject = SecurityUtils.getSubject();
            if (subject.getPrincipal() == null) {
                return true; // 无登录主体（系统直调）= system 放行
            }
            return subject.hasRole(role) || subject.hasRole(CcConstants.ROLE_ADMIN);
        } catch (Exception ignored) {
            // 无 Shiro 环境（服务层直调测试），按系统主体放行
            return true;
        }
    }

    // ============================ 预警联动闭环 ============================

    /**
     * 温控预警闭环时，对其联动产生的温度异常按同一套 V6 口径推进：
     * 以“该温控预警”作为证据引用，写入处置结论；当前主体具备复核角色（系统/ADMIN）则
     * 当场复核关闭，否则进入 PENDING_REVIEW 等待指定角色复核。已关闭异常原样跳过。
     */
    @Transactional
    public ExceptionEvent applyAlertResolution(ExceptionEvent event, Long alertId, String resolveNote) {
        if (event == null || isClosed(event.getStatus())) {
            return event;
        }
        if (CcConstants.EXC_PENDING_REVIEW.equals(event.getStatus())) {
            return event; // 已在复核流程，不重复提交
        }
        // 证据引用：温控预警（归属同批，已在提交校验中验证；此处幂等落引用）
        ExceptionEvidenceRef ref = new ExceptionEvidenceRef();
        ref.setExceptionId(event.getId());
        ref.setEvidenceType(CcConstants.EVIDENCE_TEMP_ALERT);
        ref.setEvidenceId(alertId);
        ref.setNote("随温控预警#" + alertId + " 闭环引用的连续超限预警证据");
        try {
            evidenceRefMapper.insert(ref);
        } catch (DuplicateKeyException ignored) {
            // 已引用过该预警，幂等跳过
        }

        LocalDateTime now = LocalDateTime.now();
        event.setResolveNote("随温控预警#" + alertId + " 一并提交处置结论：" + resolveNote);
        event.setResolveBy(UserContext.currentId());
        event.setResolveTime(now);
        event.setSubmittedTime(now);

        if (currentPrincipalCanReview(event.getReviewRole())) {
            event.setStatus(CcConstants.EXC_CLOSED);
            event.setReviewBy(UserContext.currentId());
            event.setReviewTime(now);
            event.setReviewRemark("随温控预警#" + alertId + " 闭环，由具备复核角色的操作者/系统复核关闭");
            event.setCloseTime(now);
        } else {
            event.setStatus(CcConstants.EXC_PENDING_REVIEW);
        }
        exceptionMapper.updateById(event);
        return event;
    }

    // ============================ 查询 / 卡口辅助 ============================

    public ExceptionEvent requireEvent(Long exceptionId) {
        ExceptionEvent event = exceptionMapper.selectById(exceptionId);
        if (event == null) {
            throw new BusinessException("异常事件不存在: " + exceptionId);
        }
        return event;
    }

    public ExceptionEscalation requireEscalation(Long id) {
        ExceptionEscalation esc = escalationMapper.selectById(id);
        if (esc == null) {
            throw new BusinessException("升级通知记录不存在: " + id);
        }
        return esc;
    }

    /** 已关闭：CLOSED 与历史 RESOLVED 同视为关闭；其余（OPEN/PENDING_REVIEW/REVIEW_REJECTED）均未关闭 */
    public boolean isClosed(String status) {
        return CcConstants.EXC_CLOSED.equals(status) || CcConstants.EXC_RESOLVED.equals(status);
    }

    /** 批次未关闭异常数（结算/签收卡口） */
    public long countOpenForBatch(Long batchId) {
        Long n = exceptionMapper.selectCount(new QueryWrapper<ExceptionEvent>()
                .eq("batch_id", batchId)
                .notIn("status", CcConstants.EXC_CLOSED, CcConstants.EXC_RESOLVED));
        return n == null ? 0 : n;
    }

    public List<ExceptionEvidenceRef> listRefs(Long exceptionId) {
        return evidenceRefMapper.selectList(new QueryWrapper<ExceptionEvidenceRef>()
                .eq("exception_id", exceptionId).orderByAsc("id"));
    }

    public List<ExceptionEscalation> listEscalations(Long exceptionId) {
        return escalationMapper.selectList(new QueryWrapper<ExceptionEscalation>()
                .eq("exception_id", exceptionId).orderByAsc("level_seq"));
    }

    private long countRefs(Long exceptionId) {
        Long n = evidenceRefMapper.selectCount(new QueryWrapper<ExceptionEvidenceRef>()
                .eq("exception_id", exceptionId));
        return n == null ? 0 : n;
    }
}
