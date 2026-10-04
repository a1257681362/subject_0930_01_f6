package com.evops.coldchain.controller;

import com.evops.coldchain.dto.GradeRuleSaveRequest;
import com.evops.coldchain.entity.ExceptionGradeRule;
import com.evops.coldchain.service.ExceptionGradeService;
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
 * 运输异常分级规则配置（V6）：
 * 四类运输异常（温度超限/封签破损/延误/收货拒签）的等级、响应时限、升级岗位、复核角色维护。
 * 写操作需 coldchain:excgrade:manage（见 ShiroConfig）；规则版本只追加，只影响此后确认的异常。
 */
@RestController
@RequestMapping("/api/cold-chain/exception-grades")
public class ExceptionGradeController {

    private final ExceptionGradeService gradeService;

    public ExceptionGradeController(ExceptionGradeService gradeService) {
        this.gradeService = gradeService;
    }

    /** 全部类型当前生效规则（配置总览） */
    @GetMapping("/effective")
    public ApiResponse<List<ExceptionGradeRule>> listEffective() {
        return ApiResponse.ok(gradeService.listEffective());
    }

    /** 某异常类型的全部规则版本（版本倒序，审计追溯） */
    @GetMapping("/{exceptionType}/versions")
    public ApiResponse<List<ExceptionGradeRule>> versions(@PathVariable("exceptionType") String exceptionType) {
        return ApiResponse.ok(gradeService.listVersions(exceptionType.toUpperCase()));
    }

    /** 发布新生效版本（旧版本置 SUPERSEDED，只追加；不影响在途异常处置时钟） */
    @PostMapping
    public ApiResponse<ExceptionGradeRule> save(@Valid @RequestBody GradeRuleSaveRequest req) {
        return ApiResponse.ok(gradeService.saveRule(req));
    }
}
