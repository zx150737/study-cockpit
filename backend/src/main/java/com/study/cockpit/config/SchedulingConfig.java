package com.study.cockpit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 启用 Spring 原生定时任务。
 * 所有 @Scheduled 均显式声明 zone = "Asia/Shanghai"（UTC+8），
 * 与 JVM -Duser.timezone、MySQL default-time-zone 保持一致，避免周次/结算错乱。
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
