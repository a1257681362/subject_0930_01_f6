package com.evops.coldchain.job;

import com.evops.coldchain.service.ExceptionHandlingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Map;

/**
 * 运输异常分级处置定时扫描（V6）。
 *
 * 固定间隔触发 {@link ExceptionHandlingService#scanEscalations()}：
 * ① 处置时钟到期未闭环的异常按岗位序列逐级升级；
 * ② 发送失败的升级通知到达重试间隔后自动重发，历次重试原因完整留痕。
 * - 扫描幂等（级次唯一约束兜底），应用重启后不重复发级、不覆盖首发/重试结果；
 * - 单次失败不影响下一周期；定时线程无登录主体，操作者记 system；
 * - 通过 coldchain.escalation.scan.enabled=false 可关闭（集成测试直调服务，避免定时器干扰）。
 */
@Component
@ConditionalOnProperty(name = "coldchain.escalation.scan.enabled", havingValue = "true", matchIfMissing = true)
public class ExceptionEscalationScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExceptionEscalationScheduler.class);

    @Resource
    private ExceptionHandlingService exceptionHandlingService;

    @Scheduled(fixedDelayString = "${coldchain.escalation.scan.fixed-delay-ms:60000}",
            initialDelayString = "${coldchain.escalation.scan.initial-delay-ms:20000}")
    public void scan() {
        try {
            Map<String, Object> summary = exceptionHandlingService.scanEscalations();
            int raised = (int) summary.getOrDefault("raised", 0);
            int retried = (int) summary.getOrDefault("retried", 0);
            if (raised > 0 || retried > 0 || log.isDebugEnabled()) {
                log.info("异常分级升级定时扫描完成: {}", summary);
            }
        } catch (Exception e) {
            // 单次扫描失败不影响下次调度；数据未提交（@Transactional 回滚），下次扫描幂等续算
            log.error("异常分级升级定时扫描失败，将在下个周期重试", e);
        }
    }
}
