package com.evops.coldchain.dto;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;

/**
 * 处置结论提交请求（V6）。
 * 结论必须引用至少一条系统内已留存证据（温度留痕/异常凭证/温控预警/封签核验/交接单），
 * 无证据引用的结论一律拒绝。提交后异常进入待指定角色复核状态，不直接关闭。
 */
public class ExceptionConclusionRequest {
    @NotBlank(message = "处置结论不能为空")
    private String conclusion;
    /** 证据引用（至少一条） */
    @NotNull(message = "处置结论必须引用证据")
    private List<EvidenceRef> evidences = new ArrayList<>();

    public static class EvidenceRef {
        /** TEMPERATURE_LOG / EVIDENCE / TEMP_ALERT / SEAL / HANDOVER */
        @NotBlank(message = "证据类型不能为空")
        private String evidenceType;
        @NotNull(message = "证据ID不能为空")
        private Long evidenceId;
        /** 引用说明：该证据如何支撑结论 */
        private String note;

        public String getEvidenceType() { return evidenceType; }
        public void setEvidenceType(String evidenceType) { this.evidenceType = evidenceType; }
        public Long getEvidenceId() { return evidenceId; }
        public void setEvidenceId(Long evidenceId) { this.evidenceId = evidenceId; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
    }

    public String getConclusion() { return conclusion; }
    public void setConclusion(String conclusion) { this.conclusion = conclusion; }
    public List<EvidenceRef> getEvidences() { return evidences; }
    public void setEvidences(List<EvidenceRef> evidences) { this.evidences = evidences; }
}
