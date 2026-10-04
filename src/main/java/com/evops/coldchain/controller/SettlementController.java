package com.evops.coldchain.controller;

import com.evops.coldchain.dto.SettlementCreateRequest;
import com.evops.coldchain.entity.FreightSettlement;
import com.evops.coldchain.service.SettlementService;
import com.evops.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;

/**
 * 运费结算（V6）：批次已签收且无未关闭异常才能进入结算，否则拒绝并指明阻断的异常。
 * 建单/确认需 coldchain:settlement（见 ShiroConfig）。
 */
@RestController
@RequestMapping("/api/cold-chain/settlements")
public class SettlementController {

    private final SettlementService settlementService;

    public SettlementController(SettlementService settlementService) {
        this.settlementService = settlementService;
    }

    /** 建结算单（前置卡口：已签收 + 无未关闭异常） */
    @PostMapping
    public ApiResponse<FreightSettlement> create(@Valid @RequestBody SettlementCreateRequest req) {
        return ApiResponse.ok(settlementService.create(req));
    }

    /** 确认结算单 */
    @PostMapping("/{id}/confirm")
    public ApiResponse<FreightSettlement> confirm(@PathVariable("id") Long id) {
        return ApiResponse.ok(settlementService.confirm(id));
    }

    /** 某批次的结算单 */
    @GetMapping("/batch/{batchId}")
    public ApiResponse<List<FreightSettlement>> listByBatch(@PathVariable("batchId") Long batchId) {
        return ApiResponse.ok(settlementService.listByBatch(batchId));
    }
}
