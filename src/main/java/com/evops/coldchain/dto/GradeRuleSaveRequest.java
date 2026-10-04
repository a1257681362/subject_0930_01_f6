package com.evops.coldchain.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.PositiveOrZero;
import java.util.List;

/**
 * 异常分级规则维护请求（V6）：对某一类运输异常发布新的生效版本。
 * 修改=旧版本置 SUPERSEDED + 追加新版本；只影响此后确认的异常，在途处置时钟不变。
 */
public class GradeRuleSaveRequest {
    /** TEMP_EXCURSION / SEAL_BROKEN / DELAY / RECEIVER_REJECT */
    @NotBlank(message = "异常类型不能为空")
    private String exceptionType;
    /** L1 / L2 / L3 */
    @NotBlank(message = "等级不能为空")
    private String gradeCode;
    @NotBlank(message = "等级名称不能为空")
    private String gradeName;
    /** 响应时限（分钟）：确认后必须在该时限内处置，到期未闭环逐级升级 */
    @NotNull(message = "响应时限（分钟）不能为空")
    @PositiveOrZero(message = "响应时限不能为负")
    private Integer responseMinutes;
    /** 升级岗位序列（按顺序逐级升级，至少一个岗位） */
    @NotEmpty(message = "升级岗位序列不能为空（至少配置一个升级岗位）")
    private List<String> escalateRoles;
    /** 关闭前复核角色 */
    @NotBlank(message = "关闭前复核角色不能为空")
    private String reviewRole;
    private Boolean enabled = Boolean.TRUE;
    private String changeReason;

    public String getExceptionType() { return exceptionType; }
    public void setExceptionType(String exceptionType) { this.exceptionType = exceptionType; }
    public String getGradeCode() { return gradeCode; }
    public void setGradeCode(String gradeCode) { this.gradeCode = gradeCode; }
    public String getGradeName() { return gradeName; }
    public void setGradeName(String gradeName) { this.gradeName = gradeName; }
    public Integer getResponseMinutes() { return responseMinutes; }
    public void setResponseMinutes(Integer responseMinutes) { this.responseMinutes = responseMinutes; }
    public List<String> getEscalateRoles() { return escalateRoles; }
    public void setEscalateRoles(List<String> escalateRoles) { this.escalateRoles = escalateRoles; }
    public String getReviewRole() { return reviewRole; }
    public void setReviewRole(String reviewRole) { this.reviewRole = reviewRole; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getChangeReason() { return changeReason; }
    public void setChangeReason(String changeReason) { this.changeReason = changeReason; }
}
