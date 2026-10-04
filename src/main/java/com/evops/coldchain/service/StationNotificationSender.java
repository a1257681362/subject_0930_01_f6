package com.evops.coldchain.service;

import com.evops.coldchain.CcConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 站内升级通知通道（V6 演示默认实现）。
 *
 * 仅做站内记账式投递（落操作日志），默认投递成功并返回回执；
 * 通过 coldchain.escalation.station-fail=true 可令其对每一条通知返回失败
 * （集成测试据此验证“发送结果=FAILED + 失败原因留痕 + 幂等重试后成功”链路，生产不配置）。
 */
@Component
public class StationNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(StationNotificationSender.class);

    /** 演示开关：置 true 时站内通道一律返回失败（用于演练失败重试留痕） */
    @Value("${coldchain.escalation.station-fail:false}")
    private boolean forceFail;

    @Override
    public SendOutcome send(String targetRole, String title, String content) {
        if (forceFail) {
            String reason = "站内通道不可用（演示开关 station-fail=true）：岗位 " + targetRole + " 投递失败，等待重试";
            log.warn("[升级通知-站内-模拟失败] role={}, title={}", targetRole, title);
            return SendOutcome.fail(CcConstants.ESCALATION_CHANNEL_STATION,
                    "STATION_UNAVAILABLE", reason);
        }
        log.info("[升级通知-站内] role={}, title={}, content={}", targetRole, title, content);
        return SendOutcome.ok(CcConstants.ESCALATION_CHANNEL_STATION,
                "STATION_DELIVERED_TO_" + targetRole);
    }
}
