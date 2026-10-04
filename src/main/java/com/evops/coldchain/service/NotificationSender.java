package com.evops.coldchain.service;

/**
 * 升级通知发送通道抽象（V6）。
 *
 * 演示环境提供站内通道实现；生产可替换为短信/邮件/IM 适配器。
 * 发送必须返回明确结果：成功给通道回执，失败给可留痕、可据此重试的失败原因。
 */
public interface NotificationSender {

    /** 发送结果（success / 通道回执 / 失败原因） */
    class SendOutcome {
        private final boolean success;
        private final String channel;
        private final String response;
        private final String failReason;

        private SendOutcome(boolean success, String channel, String response, String failReason) {
            this.success = success;
            this.channel = channel;
            this.response = response;
            this.failReason = failReason;
        }

        public static SendOutcome ok(String channel, String response) {
            return new SendOutcome(true, channel, response, null);
        }

        public static SendOutcome fail(String channel, String response, String failReason) {
            return new SendOutcome(false, channel, response, failReason);
        }

        public boolean isSuccess() { return success; }
        public String getChannel() { return channel; }
        public String getResponse() { return response; }
        public String getFailReason() { return failReason; }
    }

    /**
     * 向指定岗位发送一条异常升级通知。
     * @param targetRole 接收岗位
     * @param title      通知标题
     * @param content    通知正文（含批次/异常/等级/处置时钟/级次）
     */
    SendOutcome send(String targetRole, String title, String content);
}
