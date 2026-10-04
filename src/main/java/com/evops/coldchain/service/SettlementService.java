package com.evops.coldchain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.coldchain.CcConstants;
import com.evops.coldchain.dto.SettlementCreateRequest;
import com.evops.coldchain.entity.ExceptionEvent;
import com.evops.coldchain.entity.FreightSettlement;
import com.evops.coldchain.entity.TransportBatch;
import com.evops.coldchain.mapper.ExceptionEventMapper;
import com.evops.coldchain.mapper.FreightSettlementMapper;
import com.evops.coldchain.mapper.SequenceMapper;
import com.evops.coldchain.mapper.TransportBatchMapper;
import com.evops.common.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 运费结算服务（V6）。
 *
 * 结算前置卡口（任一不满足都拒绝进入结算）：
 * 1. 批次必须已签收闭环（SIGNED）；
 * 2. 批次不存在未关闭异常——OPEN / PENDING_REVIEW / REVIEW_REJECTED 均视为未关闭，
 *    只有 CLOSED（历史 RESOLVED 同视）才放行；
 * 3. 同批次至多一张非作废结算单。
 */
@Service
public class SettlementService {

    @Resource
    private FreightSettlementMapper settlementMapper;
    @Resource
    private TransportBatchMapper batchMapper;
    @Resource
    private ExceptionEventMapper exceptionMapper;
    @Resource
    private SequenceMapper sequenceMapper;
    @Resource
    private ExceptionHandlingService exceptionHandlingService;
    @Resource
    private OpLogService opLogService;

    /** 建结算单前的纯校验（也供外部/页面预检“能不能结算”） */
    public void requireSettleable(Long batchId) {
        TransportBatch batch = batchMapper.selectById(batchId);
        if (batch == null) {
            throw new BusinessException("运输批次不存在: " + batchId);
        }
        if (!CcConstants.BATCH_SIGNED.equals(batch.getStatus())) {
            throw new BusinessException(String.format(
                    "批次 %s 当前状态 %s，未签收闭环，不能进入运费结算", batch.getBatchNo(), batch.getStatus()));
        }
        long open = exceptionHandlingService.countOpenForBatch(batchId);
        if (open > 0) {
            List<ExceptionEvent> blocking = exceptionMapper.selectList(new QueryWrapper<ExceptionEvent>()
                    .eq("batch_id", batchId)
                    .notIn("status", CcConstants.EXC_CLOSED, CcConstants.EXC_RESOLVED)
                    .orderByAsc("id"));
            StringBuilder detail = new StringBuilder();
            for (ExceptionEvent e : blocking) {
                if (detail.length() > 0) {
                    detail.append("；");
                }
                detail.append("#").append(e.getId()).append("[").append(e.getExceptionType())
                        .append("/").append(e.getGradeCode() == null ? "-" : e.getGradeCode())
                        .append("]=").append(e.getStatus());
            }
            throw new BusinessException(String.format(
                    "批次 %s 有 %d 条异常未关闭（%s），未关闭异常不能进入结算；"
                            + "请先提交引用证据的处置结论并经指定角色复核关闭",
                    batch.getBatchNo(), open, detail));
        }
    }

    @Transactional
    public FreightSettlement create(SettlementCreateRequest req) {
        requireSettleable(req.getBatchId());
        TransportBatch batch = batchMapper.selectById(req.getBatchId());

        FreightSettlement existing = settlementMapper.selectOne(new QueryWrapper<FreightSettlement>()
                .eq("batch_id", batch.getId())
                .ne("status", CcConstants.SETTLEMENT_REVOKED)
                .last("LIMIT 1"));
        if (existing != null) {
            throw new BusinessException("批次 " + batch.getBatchNo() + " 已存在结算单 "
                    + existing.getSettlementNo() + "（" + existing.getStatus() + "），不能重复结算");
        }

        FreightSettlement settlement = new FreightSettlement();
        settlement.setSettlementNo(generateNo());
        settlement.setBatchId(batch.getId());
        settlement.setOrderId(batch.getOrderId());
        settlement.setAmount(req.getAmount());
        settlement.setStatus(CcConstants.SETTLEMENT_CREATED);
        settlement.setRemark(req.getRemark());
        settlementMapper.insert(settlement);

        opLogService.log(CcConstants.ENT_SETTLEMENT, settlement.getId(), batch.getId(),
                CcConstants.ACT_SETTLEMENT_CREATE,
                String.format("批次 %s 进入运费结算：结算单 %s，金额=%s；前置卡口通过（已签收、无未关闭异常）。备注：%s",
                        batch.getBatchNo(), settlement.getSettlementNo(), settlement.getAmount(),
                        req.getRemark() == null ? "" : req.getRemark()));
        return settlement;
    }

    /** 结算确认（仅已建单可确认） */
    @Transactional
    public FreightSettlement confirm(Long settlementId) {
        FreightSettlement settlement = requireSettlement(settlementId);
        if (CcConstants.SETTLEMENT_CONFIRMED.equals(settlement.getStatus())) {
            throw new BusinessException("结算单 " + settlement.getSettlementNo() + " 已确认，不能重复确认");
        }
        if (CcConstants.SETTLEMENT_REVOKED.equals(settlement.getStatus())) {
            throw new BusinessException("结算单 " + settlement.getSettlementNo() + " 已作废，不能确认");
        }
        settlement.setStatus(CcConstants.SETTLEMENT_CONFIRMED);
        settlementMapper.updateById(settlement);

        opLogService.log(CcConstants.ENT_SETTLEMENT, settlement.getId(), settlement.getBatchId(),
                CcConstants.ACT_SETTLEMENT_CONFIRM,
                "结算单 " + settlement.getSettlementNo() + " 确认（金额=" + settlement.getAmount() + "）");
        return settlement;
    }

    public FreightSettlement requireSettlement(Long id) {
        FreightSettlement settlement = settlementMapper.selectById(id);
        if (settlement == null) {
            throw new BusinessException("结算单不存在: " + id);
        }
        return settlement;
    }

    public List<FreightSettlement> listByBatch(Long batchId) {
        return settlementMapper.selectList(new QueryWrapper<FreightSettlement>()
                .eq("batch_id", batchId).orderByDesc("id"));
    }

    private String generateNo() {
        return String.format("JS%08d", sequenceMapper.nextSettlementNo());
    }
}
