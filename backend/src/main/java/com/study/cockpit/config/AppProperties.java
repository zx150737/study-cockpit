package com.study.cockpit.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 集中管理所有来自环境变量的配置，并在启动阶段做 fail-fast 校验。
 *
 * 安全要求 7：仓库与配置文件中不存在任何真实密钥。
 * - prod：JWT_SECRET / FILE_TOKEN_SECRET / DB_PASSWORD 缺失或过短 -> 直接启动失败
 * - dev ：缺失时生成"每次启动变化的临时随机密钥"，保证本地可运行且不硬编码
 */
@Slf4j
@Component
@Getter
public class AppProperties {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${spring.datasource.password:}")
    private String dbPassword;

    @Value("${cockpit.jwt.secret:}")
    private String jwtSecret;

    @Value("${cockpit.jwt.ttl-minutes:30}")
    private long jwtTtlMinutes;

    @Value("${cockpit.jwt.cookie-secure:true}")
    private boolean jwtCookieSecure;

    @Value("${cockpit.jwt.cookie-name:ACCESS_TOKEN}")
    private String jwtCookieName;

    @Value("${cockpit.file.token-secret:}")
    private String fileTokenSecret;

    @Value("${cockpit.file.token-ttl-seconds:1800}")
    private long fileTokenTtlSeconds;

    @Value("${cockpit.file.storage-dir:/data/study-cockpit/uploads}")
    private String storageDir;

    @Value("${cockpit.file.max-size-bytes:5242880}")
    private long fileMaxSizeBytes;

    @Value("${cockpit.file.allowed-ext:jpg,jpeg,png,webp}")
    private String allowedExt;

    @Value("${cockpit.security.allow-register:false}")
    private boolean allowRegister;

    @Value("${cockpit.security.rate-limit-per-minute:60}")
    private long rateLimitPerMinute;

    @Value("${cockpit.security.login-rate-limit-per-minute:5}")
    private long loginRateLimitPerMinute;

    @Value("${cockpit.security.login-max-fail:5}")
    private int loginMaxFail;

    @Value("${cockpit.security.login-lock-minutes:15}")
    private long loginLockMinutes;

    /** dev 缺失密钥时使用的临时值，不写入任何文件 */
    private String temporarySecret(String name) {
        String s = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
        log.warn("{} 未配置，dev 模式使用本次启动的临时随机密钥（重启后失效）", name);
        return s;
    }

    /** 手写 getter：对 dev 空白密钥做兜底；Lombok @Getter 会跳过已有同名方法 */
    public String getJwtSecret() {
        return jwtSecret;
    }

    public String getFileTokenSecret() {
        return fileTokenSecret;
    }

    @PostConstruct
    public void validate() {
        boolean prod = "prod".equalsIgnoreCase(activeProfile);

        if (jwtSecret == null || jwtSecret.isBlank()) {
            if (prod) {
                throw new IllegalStateException("生产环境必须通过环境变量 JWT_SECRET 设置 JWT 密钥(>=32字节)");
            }
            jwtSecret = temporarySecret("JWT_SECRET");
        }
        if (fileTokenSecret == null || fileTokenSecret.isBlank()) {
            if (prod) {
                throw new IllegalStateException("生产环境必须通过环境变量 FILE_TOKEN_SECRET 设置图片签名密钥(>=32字节)");
            }
            fileTokenSecret = temporarySecret("FILE_TOKEN_SECRET");
        }

        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET 长度不足 32 字节");
        }
        if (fileTokenSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("FILE_TOKEN_SECRET 长度不足 32 字节");
        }
        // 两个密钥必须不同，避免签名域互相串用
        if (jwtSecret.equals(fileTokenSecret)) {
            throw new IllegalStateException("JWT_SECRET 与 FILE_TOKEN_SECRET 不得相同");
        }
        if (prod && (dbPassword == null || dbPassword.isBlank())) {
            throw new IllegalStateException("生产环境必须通过环境变量 DB_PASSWORD 设置数据库密码");
        }

        Path dir = Paths.get(storageDir);
        if (!dir.isAbsolute()) {
            throw new IllegalStateException("FILE_STORAGE_DIR 必须是绝对路径");
        }
        // 存储目录不得落在 Web 静态资源目录下，防止上传文件被直接执行/访问
        String normalized = dir.normalize().toString().replace('\\', '/').toLowerCase();
        if (normalized.contains("/static/") || normalized.contains("/public/") || normalized.endsWith("/static")) {
            throw new IllegalStateException("FILE_STORAGE_DIR 不得位于 Web 静态资源目录内");
        }
        if (jwtTtlMinutes <= 0 || jwtTtlMinutes > 120) {
            throw new IllegalStateException("JWT_TTL_MINUTES 必须落在 1~120 分钟（短有效期）");
        }
        if (fileMaxSizeBytes <= 0) {
            throw new IllegalStateException("FILE_MAX_SIZE_BYTES 必须为正数");
        }
        log.info("配置校验通过 profile={} jwtTtl={}min fileTtl={}s maxFileSize={}bytes allowRegister={}",
                activeProfile, jwtTtlMinutes, fileTokenTtlSeconds, fileMaxSizeBytes, allowRegister);
    }
}
