package com.evops.coldchain.dto;

import javax.validation.constraints.NotNull;

/** 处置结论复核请求（V6）：approved=TRUE 关闭异常；FALSE 驳回并要求填写驳回原因 */
public class ExceptionReviewRequest {
    @NotNull(message = "复核结论不能为空")
    private Boolean approved;
    /** 复核意见（通过/驳回均可填） */
    private String reviewRemark;
    /** 驳回原因（approved=FALSE 时必填） */
    private String rejectReason;

    public Boolean getApproved() { return approved; }
    public void setApproved(Boolean approved) { this.approved = approved; }
    public String getReviewRemark() { return reviewRemark; }
    public void setReviewRemark(String reviewRemark) { this.reviewRemark = reviewRemark; }
    public String getRejectReason() { return rejectReason; }
    public void setRejectReason(String rejectReason) { this.rejectReason = rejectReason; }
}
