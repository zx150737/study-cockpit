package com.study.cockpit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 学习监控驾驶舱 · 后端启动入口。
 * JVM 建议显式指定 -Duser.timezone=Asia/Shanghai，容器设置 TZ=Asia/Shanghai，
 * 与 MySQL default-time-zone='+08:00' 保持一致，避免周次计算错乱。
 */
@SpringBootApplication
public class CockpitApplication {

    public static void main(String[] args) {
        SpringApplication.run(CockpitApplication.class, args);
    }
}
