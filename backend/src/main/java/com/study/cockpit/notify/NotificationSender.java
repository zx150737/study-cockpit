package com.study.cockpit.notify;

/**
 * 站内提醒发送扩展点（需求 7：预留邮件提醒扩展接口）。
 * 本期仅提供 InAppNotificationSender；接入邮件时新增
 * EmailNotificationSender implements NotificationSender 并在配置中启用即可，
 * 业务代码与定时任务均不需要修改。本期不实现邮件发送，避免超出需求范围。
 */
public interface NotificationSender {

    void send(Long userId, NotificationPayload payload);

    /** 提醒载荷：仅传递数量与标题摘要，不含任何敏感内容 */
    record NotificationPayload(long overdueCount, java.util.List<String> titles) {
    }
}
