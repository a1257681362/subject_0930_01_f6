package com.evops.coldchain.dto;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Positive;
import java.math.BigDecimal;

/**
 * 运费结算建单请求（V6）。
 * 强卡口：批次必须已签收闭环，且不存在未关闭异常（OPEN/待复核/复核驳回），否则拒绝进入结算。
 */
public class SettlementCreateRequest {
    @NotNull(message = "运输批次ID不能为空")
    private Long batchId;
    @NotNull(message = "结算金额不能为空")
    @Positive(message = "结算金额必须大于0")
    private BigDecimal amount;
    private String remark;

    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
