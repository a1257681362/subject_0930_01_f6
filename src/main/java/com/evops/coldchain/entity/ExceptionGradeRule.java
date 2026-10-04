package com.evops.coldchain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

/**
 * 异常分级规则（V6）：四类运输异常（温度超限/封签破损/延误/收货拒签）各配置
 * 等级、响应时限、升级岗位序列、关闭前复核角色。
 *
 * 规则按类型只追加版本：修改=旧版本置 SUPERSEDED + 追加新版本（version_no 在类型内单调）。
 * 异常确认瞬间把当时生效版本逐字段快照到异常行，此后规则调整与在途处置时钟脱钩。
 */
@TableName("t_cc_exception_grade_rule")
public class ExceptionGradeRule extends BaseEntity {
    /** TEMP_EXCURSION / SEAL_BROKEN / DELAY / RECEIVER_REJECT */
    private String exceptionType;
    /** 同异常类型内从 1 递增 */
    private Integer versionNo;
    /** L1 / L2 / L3 */
    private String gradeCode;
    private String gradeName;
    /** 响应时限（分钟） */
    private Integer responseMinutes;
    /** 升级岗位序列 JSON 数组字符串，如 ["DISPATCHER","MANAGER","ADMIN"] */
    private String escalateRoles;
    /** 关闭前复核角色 */
    private String reviewRole;
    /** FALSE=停用该类型分级（异常不启时钟/不升级） */
    private Boolean enabled;
    private String changeReason;
    /** EFFECTIVE / SUPERSEDED */
    private String status;

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    public Integer getVersionNo() { return versionNo; }
    public void setVersionNo(Integer versionNo) { this.versionNo = versionNo; }
    public String getGradeCode() { return gradeCode; }
    public void setGradeCode(String gradeCode) { this.gradeCode = gradeCode; }
    public String getGradeName() { return gradeName; }
    public void setGradeName(String gradeName) { this.gradeName = gradeName; }
    public Integer getResponseMinutes() { return responseMinutes; }
    public void setResponseMinutes(Integer responseMinutes) { this.responseMinutes = responseMinutes; }
    public String getEscalateRoles() { return escalateRoles; }
    public void setEscalateRoles(String escalateRoles) { this.escalateRoles = escalateRoles; }
    public String getReviewRole() { return reviewRole; }
    public void setReviewRole(String reviewRole) { this.reviewRole = reviewRole; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
