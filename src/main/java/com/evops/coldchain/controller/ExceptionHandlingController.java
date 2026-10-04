package com.evops.coldchain.controller;

import com.evops.coldchain.dto.ExceptionConclusionRequest;
import com.evops.coldchain.dto.ExceptionReviewRequest;
import com.evops.coldchain.entity.ExceptionEscalation;
import com.evops.coldchain.entity.ExceptionEvent;
import com.evops.coldchain.entity.ExceptionEvidenceRef;
import com.evops.coldchain.service.ExceptionHandlingService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 运输异常分级处置（V6）：
 * 提交“引用证据”的处置结论 → 指定角色复核关闭；查询处置时钟/升级记录；人工重试失败通知。
 *
 * 提交结论需 coldchain:exception:handle；复核需 coldchain:exception:review（且具备规则指定角色）；
 * 重试需 coldchain:exception:escalate（见 ShiroConfig）。
 */
@RestController
@RequestMapping("/api/cold-chain/exceptions")
public class ExceptionHandlingController {

    private final ExceptionHandlingService handlingService;

    public ExceptionHandlingController(ExceptionHandlingService handlingService) {
        this.handlingService = handlingService;
    }

    /** 异常处置详情：异常（分级快照/处置时钟）+ 证据引用 + 升级通知记录 */
    @GetMapping("/{id}/handling")
    public ApiResponse<Map<String, Object>> handling(@PathVariable("id") Long id) {
        ExceptionEvent event = handlingService.requireEvent(id);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("exception", event);
        data.put("closed", handlingService.isClosed(event.getStatus()));
        data.put("evidenceRefs", handlingService.listRefs(id));
        data.put("escalations", handlingService.listEscalations(id));
        return ApiResponse.ok(data);
    }

    /** 提交处置结论（必须引用至少一条系统内证据；提交后待指定角色复核） */
    @PostMapping("/{id}/conclusion")
    public ApiResponse<ExceptionEvent> conclusion(@PathVariable("id") Long id,
                                                  @Valid @RequestBody ExceptionConclusionRequest req) {
        return ApiResponse.ok(handlingService.submitConclusion(id, req));
    }

    /** 指定角色复核：通过关闭 / 驳回（须填原因） */
    @PostMapping("/{id}/review")
    public ApiResponse<ExceptionEvent> review(@PathVariable("id") Long id,
                                              @Valid @RequestBody ExceptionReviewRequest req) {
        return ApiResponse.ok(handlingService.review(id, req));
    }

    /** 人工重试一条发送失败的升级通知（发送结果/重试原因继续留痕） */
    @PostMapping("/escalations/{escalationId}/retry")
    public ApiResponse<ExceptionEscalation> retry(@PathVariable("escalationId") Long escalationId) {
        return ApiResponse.ok(handlingService.retryEscalation(escalationId));
    }
}
