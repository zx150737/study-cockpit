package com.study.cockpit.task;

import com.study.cockpit.mapper.DailyTaskMapper;
import com.study.cockpit.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 未完成任务定时扫描（需求 7）。
 * 每小时扫描一次积压任务并触发 NotificationSender 扩展点；
 * 站内展示本身是登录时实时聚合，邮件通道仅预留接口，本期不实现。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueScanTask {

    private final DailyTaskMapper taskMapper;
    private final NotificationService notificationService;

    @Scheduled(cron = "0 0 * * * *", zone = "Asia/Shanghai")
    public void scan() {
        try {
            List<Long> userIds = taskMapper.selectAllUserIds();
            notificationService.scan(userIds);
        } catch (Exception e) {
            log.error("积压任务扫描任务异常", e);
        }
    }
}
