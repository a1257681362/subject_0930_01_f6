package com.evops.coldchain.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.evops.coldchain.CcConstants;
import com.evops.coldchain.dto.GradeRuleSaveRequest;
import com.evops.coldchain.entity.ExceptionGradeRule;
import com.evops.coldchain.mapper.ExceptionGradeRuleMapper;
import com.evops.common.BusinessException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 异常分级规则服务（V6）。
 *
 * 四类运输异常（温度超限/封签破损/延误/收货拒签）各维护一行“当前生效版本”：
 * - 修改规则 = 旧生效版本置 SUPERSEDED + 追加新版本（version_no 在类型内单调），只追加不改原值；
 * - 异常确认瞬间取当时生效版本逐字段快照，此后规则调整与在途异常处置时钟脱钩（快照口径）。
 *
 * 升级岗位序列以 JSON 数组字符串存储（如 ["DISPATCHER","MANAGER","ADMIN"]），
 * 解析/序列化统一走本服务，避免各处手写 JSON。
 */
@Service
public class ExceptionGradeService {

    /** 纳入分级处置的四类运输异常（其它历史类型 DAMAGE/OTHER 走人工处置，不启时钟） */
    public static final Set<String> GRADED_TYPES = new LinkedHashSet<>(Arrays.asList(
            CcConstants.EXC_TEMP,
            CcConstants.EXC_SEAL_BROKEN,
            CcConstants.EXC_DELAY,
            CcConstants.EXC_RECEIVER_REJECT));

    private static final Set<String> VALID_GRADES = new LinkedHashSet<>(Arrays.asList(
            CcConstants.GRADE_L1, CcConstants.GRADE_L2, CcConstants.GRADE_L3));

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> ROLE_LIST = new TypeReference<List<String>>() {};

    @Resource
    private ExceptionGradeRuleMapper ruleMapper;
    @Resource
    private OpLogService opLogService;

    /** 取某异常类型当前生效规则；无生效版本返回 null（调用方决定不启时钟） */
    public ExceptionGradeRule effectiveRule(String exceptionType) {
        List<ExceptionGradeRule> rows = ruleMapper.selectList(new QueryWrapper<ExceptionGradeRule>()
                .eq("exception_type", exceptionType)
                .eq("status", CcConstants.EFFECTIVE)
                .orderByDesc("version_no")
                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** 某类型全部版本（审计追溯），版本倒序 */
    public List<ExceptionGradeRule> listVersions(String exceptionType) {
        return ruleMapper.selectList(new QueryWrapper<ExceptionGradeRule>()
                .eq("exception_type", exceptionType)
                .orderByDesc("version_no"));
    }

    /** 全部类型当前生效规则（配置总览） */
    public List<ExceptionGradeRule> listEffective() {
        return ruleMapper.selectList(new QueryWrapper<ExceptionGradeRule>()
                .eq("status", CcConstants.EFFECTIVE)
                .orderByAsc("exception_type"));
    }

    /**
     * 发布新生效版本：先把同类型当前生效版本置 SUPERSEDED，再追加新版本。
     * 只影响此后确认的异常；已确认异常持有旧快照，处置时钟/升级岗位/复核角色不变。
     */
    @Transactional
    public ExceptionGradeRule saveRule(GradeRuleSaveRequest req) {
        String type = normalizeType(req.getExceptionType());
        String grade = req.getGradeCode() == null ? null : req.getGradeCode().trim().toUpperCase();
        if (!VALID_GRADES.contains(grade)) {
            throw new BusinessException("非法异常等级: " + req.getGradeCode() + "（仅支持 L1/L2/L3）");
        }
        List<String> roles = normalizeRoles(req.getEscalateRoles());
        String reviewRole = normalizeRole(req.getReviewRole());

        ExceptionGradeRule current = effectiveRule(type);
        int nextVersion = current == null ? 1 : current.getVersionNo() + 1;
        if (current != null) {
            current.setStatus(CcConstants.SUPERSEDED);
            ruleMapper.updateById(current);
        }

        ExceptionGradeRule rule = new ExceptionGradeRule();
        rule.setExceptionType(type);
        rule.setVersionNo(nextVersion);
        rule.setGradeCode(grade);
        rule.setGradeName(req.getGradeName().trim());
        rule.setResponseMinutes(req.getResponseMinutes());
        rule.setEscalateRoles(toJson(roles));
        rule.setReviewRole(reviewRole);
        rule.setEnabled(req.getEnabled() == null ? Boolean.TRUE : req.getEnabled());
        rule.setChangeReason(req.getChangeReason());
        rule.setStatus(CcConstants.EFFECTIVE);
        ruleMapper.insert(rule);

        opLogService.log(CcConstants.ENT_EXC_GRADE, rule.getId(), null, CcConstants.ACT_EXC_GRADE_SAVE,
                String.format("异常分级规则发布 V%d：类型=%s，等级=%s/%s，响应时限=%d分钟，升级岗位=%s，复核角色=%s，启用=%s。原因：%s",
                        nextVersion, type, grade, rule.getGradeName(), rule.getResponseMinutes(),
                        roles, reviewRole, rule.getEnabled(),
                        req.getChangeReason() == null ? "" : req.getChangeReason()));
        return rule;
    }

    /** 解析升级岗位序列（非法 JSON 时回退为空列表，保证升级扫描不被脏数据打断） */
    public List<String> parseRoles(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<String> roles = JSON.readValue(json, ROLE_LIST);
            return roles == null ? new ArrayList<>() : roles;
        } catch (IOException e) {
            throw new BusinessException("升级岗位序列配置无法解析: " + json);
        }
    }

    public String toJson(List<String> roles) {
        try {
            return JSON.writeValueAsString(roles);
        } catch (IOException e) {
            throw new BusinessException("升级岗位序列序列化失败");
        }
    }

    /** 把外部/历史异常类型归一到四类受管类型；不在范围内则拒绝维护 */
    private String normalizeType(String type) {
        if (type == null) {
            throw new BusinessException("异常类型不能为空");
        }
        String t = type.trim().toUpperCase();
        if (!GRADED_TYPES.contains(t)) {
            throw new BusinessException("仅支持对四类运输异常配置分级: " + GRADED_TYPES + "；收到: " + t);
        }
        return t;
    }

    private List<String> normalizeRoles(List<String> raw) {
        List<String> roles = new ArrayList<>();
        if (raw != null) {
            for (String r : raw) {
                if (r != null && !r.trim().isEmpty()) {
                    String role = r.trim().toUpperCase();
                    if (!roles.contains(role)) {
                        roles.add(role);
                    }
                }
            }
        }
        if (roles.isEmpty()) {
            throw new BusinessException("升级岗位序列不能为空（至少配置一个升级岗位）");
        }
        return roles;
    }

    private String normalizeRole(String role) {
        if (role == null || role.trim().isEmpty()) {
            throw new BusinessException("关闭前复核角色不能为空");
        }
        return role.trim().toUpperCase();
    }
}
