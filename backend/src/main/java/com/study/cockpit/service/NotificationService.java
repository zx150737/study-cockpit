package com.study.cockpit.service;

import com.study.cockpit.dto.ViewDTO;
import com.study.cockpit.notify.NotificationSender;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 站内提醒（需求 7）。
 * 登录后展示的积压预警为实时聚合，不落提醒表；
 * 定时扫描任务通过 NotificationSender 触发扩展通道（邮件接口已预留，本期不实现）。
 */
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final TaskService taskService;
    private final NotificationSender notificationSender;

    public ViewDTO.OverdueVO overdue(Long userId) {
        return taskService.overdue(userId, 20);
    }

    /** 由 OverdueScanTask 每小时调用 */
    public void scan(List<Long> userIds) {
        for (Long userId : userIds) {
            ViewDTO.OverdueVO vo = taskService.overdue(userId, 20);
            List<String> titles = vo.getItems() == null ? List.of()
                    : vo.getItems().stream().map(ViewDTO.BacklogItem::getTitle).toList();
            notificationSender.send(userId,
                    new NotificationSender.NotificationPayload(vo.getTotal(), titles));
        }
    }
}
