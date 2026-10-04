package com.evops.coldchain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.CreateOnlyEntity;

/**
 * 处置结论的证据引用（V6）：只追加，不改写/删除。
 * 处置结论必须至少引用一条系统内已留存证据（温度留痕/异常凭证/温控预警/封签核验/交接单）。
 */
@TableName("t_cc_exception_evidence_ref")
public class ExceptionEvidenceRef extends CreateOnlyEntity {
    private Long exceptionId;
    /** TEMPERATURE_LOG / EVIDENCE / TEMP_ALERT / SEAL / HANDOVER */
    private String evidenceType;
    /** 对应业务表主键（建引用时校验存在） */
    private Long evidenceId;
    /** 引用说明：该证据如何支撑处置结论 */
    private String note;

    public Long getExceptionId() { return exceptionId; }
    public void setExceptionId(Long exceptionId) { this.exceptionId = exceptionId; }
    public String getEvidenceType() { return evidenceType; }
    public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }
    public Long getEvidenceId() { return evidenceId; }
    public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
