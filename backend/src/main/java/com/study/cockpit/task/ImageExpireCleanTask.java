package com.study.cockpit.task;

import com.study.cockpit.service.TaskImageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 图片 15 天到期清理（需求 3）。
 * 每日 03:00（UTC+8）执行：分页扫描 expire_time <= now 的记录，
 * 先删物理文件、再删数据库记录；删除失败保留记录留待下次重试。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ImageExpireCleanTask {

    private final TaskImageService imageService;

    @Scheduled(cron = "0 0 3 * * *", zone = "Asia/Shanghai")
    public void clean() {
        try {
            int count = imageService.cleanExpired();
            log.info("图片过期清理任务完成，删除 {} 条", count);
        } catch (Exception e) {
            // 定时任务异常不得影响容器与其它任务
            log.error("图片过期清理任务异常", e);
        }
    }
}
