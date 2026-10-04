package com.evops.coldchain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.time.LocalDateTime;

/**
 * 升级通知记录（V6）：按“异常×升级级次”唯一一行，只追加。
 *
 * 首发与每次重试都在本行留痕：sendResult（PENDING/SUCCESS/FAILED）、通道回执、
 * 尝试次数 attemptCount、最近重试时间/原因 lastRetryTime/lastRetryReason，
 * 历次重试原因完整追加在 retryReasonLog（每行一个原因），首发失败原因不会被重试覆盖。
 */
@TableName("t_cc_exception_escalation")
public class ExceptionEscalation extends BaseEntity {
    private Long exceptionId;
    private Long batchId;
    /** 升级级次：1=响应时限到期首报，之后逐级 */
    private Integer levelSeq;
    private String gradeCode;
    /** 本级通知岗位 */
    private String notifyRole;
    /** 本级计划发送时间 */
    private LocalDateTime dueTime;
    private LocalDateTime sendTime;
    /** PENDING / SUCCESS / FAILED */
    private String sendResult;
    private String channel;
    private String channelResponse;
    private Integer attemptCount;
    private LocalDateTime lastRetryTime;
    private String lastRetryReason;
    /** 历次重试原因（只追加，每行一个） */
    private String retryReasonLog;

    public Long getExceptionId() { return exceptionId; }
    public void setExceptionId(Long exceptionId) { this.exceptionId = exceptionId; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Integer getLevelSeq() { return levelSeq; }
    public void setLevelSeq(Integer levelSeq) { this.levelSeq = levelSeq; }
    public String getGradeCode() { return gradeCode; }
    public void setGradeCode(String gradeCode) { this.gradeCode = gradeCode; }
    public String getNotifyRole() { return notifyRole; }
    public void setNotifyRole(String notifyRole) { this.notifyRole = notifyRole; }
    public LocalDateTime getDueTime() { return dueTime; }
    public void setDueTime(LocalDateTime dueTime) { this.dueTime = dueTime; }
    public LocalDateTime getSendTime() { return sendTime; }
    public void setSendTime(LocalDateTime sendTime) { this.sendTime = sendTime; }
    public String getSendResult() { return sendResult; }
    public void setSendResult(String sendResult) { this.sendResult = sendResult; }
    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }
    public String getChannelResponse() { return channelResponse; }
    public void setChannelResponse(String channelResponse) { this.channelResponse = channelResponse; }
    public Integer getAttemptCount() { return attemptCount; }
    public void setAttemptCount(Integer attemptCount) { this.attemptCount = attemptCount; }
    public LocalDateTime getLastRetryTime() { return lastRetryTime; }
    public void setLastRetryTime(LocalDateTime lastRetryTime) { this.lastRetryTime = lastRetryTime; }
    public String getLastRetryReason() { return lastRetryReason; }
    public void setLastRetryReason(String lastRetryReason) { this.lastRetryReason = lastRetryReason; }
    public String getRetryReasonLog() { return retryReasonLog; }
    public void setRetryReasonLog(String retryReasonLog) { this.retryReasonLog = retryReasonLog; }
}
