package com.study.cockpit.notify;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 默认站内提醒实现。
 * 预警内容不落表：登录时由 GET /api/notifications/overdue 实时聚合展示，
 * 定时任务仅负责扫描并触发此扩展点（结构化日志留痕）。
 */
@Slf4j
@Component
public class InAppNotificationSender implements NotificationSender {

    @Override
    public void send(Long userId, NotificationPayload payload) {
        if (payload.overdueCount() > 0) {
            log.info("站内积压提醒 userId={} overdueCount={} samples={}",
                    userId, payload.overdueCount(), payload.titles());
        }
    }
}
