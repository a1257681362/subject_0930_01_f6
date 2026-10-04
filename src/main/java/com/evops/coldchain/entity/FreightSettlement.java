package com.evops.coldchain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.evops.common.BaseEntity;

import java.math.BigDecimal;

/**
 * 运费结算单（V6）。
 * 建单强卡口：批次必须已签收闭环，且不存在未关闭异常
 * （OPEN/PENDING_REVIEW/REVIEW_REJECTED，含历史 RESOLVED 视为已关闭），否则拒绝进入结算。
 */
@TableName("t_cc_freight_settlement")
public class FreightSettlement extends BaseEntity {
    private String settlementNo;
    private Long batchId;
    private Long orderId;
    private BigDecimal amount;
    /** CREATED 已建单 / CONFIRMED 已确认 / REVOKED 已作废 */
    private String status;
    private String remark;

    public String getSettlementNo() { return settlementNo; }
    public void setSettlementNo(String settlementNo) { this.settlementNo = settlementNo; }
    public Long getBatchId() { return batchId; }
    public void setBatchId(Long batchId) { this.batchId = batchId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
