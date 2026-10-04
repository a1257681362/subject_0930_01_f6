package com.evops.coldchain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 异常事件：追加式，一批可多次。
 * 原描述 description 一旦写入永不修改。
 *
 * V6 分级处置状态机：
 * OPEN（确认即启动处置时钟）→ 提交带证据引用的处置结论 → PENDING_REVIEW
 *   → 指定角色复核通过 → CLOSED；复核驳回 → REVIEW_REJECTED（可补充证据后再次提交）。
 * 分级规则在确认瞬间快照到本行（gradeCode/gradeName/responseMinutes/escalateRoles/reviewRole），
 * confirmedTime/firstFoundTime/clockDeadline 首次写入后不再被任何补录/重扫覆盖。
 */
@TableName("t_cc_exception")
public class ExceptionEvent extends BaseEntity {
    private Long batchId;
    /** TEMP_EXCURSION / DAMAGE / DELAY / SEAL_BROKEN / RECEIVER_REJECT / OTHER */
    private String exceptionType;
    private String description;
    private LocalDateTime occurredTime;
    /** OPEN / PENDING_REVIEW / REVIEW_REJECTED / CLOSED（历史数据可能为 RESOLVED） */
    private String status;
    private String resolveNote;
    private Long resolveBy;
    private LocalDateTime resolveTime;
    /** 越限命中的路线分段序号（有路线分段时），便于追溯该段责任人 */
    private Integer segmentSeq;
    /** V5：触发该温度异常的温控预警ID（预警闭环时一并闭环对应异常） */
    private Long alertId;

    // ---- V6 分级规则快照（确认瞬间固化，此后与规则表脱钩） ----
    private String gradeCode;
    private String gradeName;
    private Integer gradeRuleVersion;
    private Integer responseMinutes;
    /** 升级岗位序列 JSON 快照 */
    private String escalateRoles;
    private String reviewRole;

    // ---- V6 处置时钟（首次写入后不可覆盖） ----
    /** 首次确认时刻=处置时钟起点 */
    private LocalDateTime confirmedTime;
    /** 首次发现时间（可由发现人补录，一旦写入永不修改） */
    private LocalDateTime firstFoundTime;
    /** 处置时钟截止时间 = confirmedTime + responseMinutes */
    private LocalDateTime clockDeadline;
    /** 已发出的最高升级级次（0=尚未升级） */
    private Integer escalationLevel;

    // ---- V6 提交/复核闭环（只追加，不改原描述） ----
    private LocalDateTime submittedTime;
    private Long reviewBy;
    private LocalDateTime reviewTime;
    private String reviewRemark;
    private String reviewRejectReason;
    private LocalDateTime closeTime;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getOccurredTime() { return occurredTime; }
    public void setOccurredTime(LocalDateTime occurredTime) { this.occurredTime = occurredTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResolveNote() { return resolveNote; }
    public void setResolveNote(String resolveNote) { this.resolveNote = resolveNote; }
    public Long getResolveBy() { return resolveBy; }
    public void setResolveBy(Long resolveBy) { this.resolveBy = resolveBy; }
    public LocalDateTime getResolveTime() { return resolveTime; }
    public void setResolveTime(LocalDateTime resolveTime) { this.resolveTime = resolveTime; }
    public Integer getSegmentSeq() { return segmentSeq; }
    public void setSegmentSeq(Integer segmentSeq) { this.segmentSeq = segmentSeq; }
    public Long getAlertId() { return alertId; }
    public void setAlertId(Long alertId) { this.alertId = alertId; }
    public String getGradeCode() { return gradeCode; }
    public void setGradeCode(String gradeCode) { this.gradeCode = gradeCode; }
    public String getGradeName() { return gradeName; }
    public void setGradeName(String gradeName) { this.gradeName = gradeName; }
    public Integer getGradeRuleVersion() { return gradeRuleVersion; }
    public void setGradeRuleVersion(Integer gradeRuleVersion) { this.gradeRuleVersion = gradeRuleVersion; }
    public Integer getResponseMinutes() { return responseMinutes; }
    public void setResponseMinutes(Integer responseMinutes) { this.responseMinutes = responseMinutes; }
    public String getEscalateRoles() { return escalateRoles; }
    public void setEscalateRoles(String escalateRoles) { this.escalateRoles = escalateRoles; }
    public String getReviewRole() { return reviewRole; }
    public void setReviewRole(String reviewRole) { this.reviewRole = reviewRole; }
    public LocalDateTime getConfirmedTime() { return confirmedTime; }
    public void setConfirmedTime(LocalDateTime confirmedTime) { this.confirmedTime = confirmedTime; }
    public LocalDateTime getFirstFoundTime() { return firstFoundTime; }
    public void setFirstFoundTime(LocalDateTime firstFoundTime) { this.firstFoundTime = firstFoundTime; }
    public LocalDateTime getClockDeadline() { return clockDeadline; }
    public void setClockDeadline(LocalDateTime clockDeadline) { this.clockDeadline = clockDeadline; }
    public Integer getEscalationLevel() { return escalationLevel; }
    public void setEscalationLevel(Integer escalationLevel) { this.escalationLevel = escalationLevel; }
    public LocalDateTime getSubmittedTime() { return submittedTime; }
    public void setSubmittedTime(LocalDateTime submittedTime) { this.submittedTime = submittedTime; }
    public Long getReviewBy() { return reviewBy; }
    public void setReviewBy(Long reviewBy) { this.reviewBy = reviewBy; }
    public LocalDateTime getReviewTime() { return reviewTime; }
    public void setReviewTime(LocalDateTime reviewTime) { this.reviewTime = reviewTime; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public String getReviewRejectReason() { return reviewRejectReason; }
    public void setReviewRejectReason(String reviewRejectReason) { this.reviewRejectReason = reviewRejectReason; }
    public LocalDateTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalDateTime closeTime) { this.closeTime = closeTime; }
}
