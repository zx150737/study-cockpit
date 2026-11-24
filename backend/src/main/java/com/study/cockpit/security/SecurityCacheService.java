package com.study.cockpit.security;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.study.cockpit.config.AppProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单机安全缓存：jti 黑名单、登录失败锁定、全局限流窗口。
 * 个人独立部署为单实例，用 Caffeine 即可，不引入 Redis（贴合固定技术栈）。
 * 若未来多实例部署，需将此处替换为集中式存储。
 */
@Component
@RequiredArgsConstructor
public class SecurityCacheService {

    private final AppProperties props;

    /** 注销令牌黑名单：TTL 覆盖最长 JWT 有效期（默认 30 分钟，这里留 2 小时余量） */
    private final Cache<String, Boolean> blacklist = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(2))
            .maximumSize(20_000)
            .build();

    private final Cache<String, AtomicInteger> failures = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(2))
            .maximumSize(20_000)
            .build();

    private final Cache<String, Long> lockUntil = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofHours(2))
            .maximumSize(20_000)
            .build();

    private final Cache<String, RateWindow> rateWindows = Caffeine.newBuilder()
            .expireAfterAccess(Duration.ofMinutes(5))
            .maximumSize(50_000)
            .build();

    // ---------- JWT 黑名单 ----------

    public void blacklist(String jti) {
        if (jti != null) {
            blacklist.put(jti, Boolean.TRUE);
        }
    }

    public boolean isBlacklisted(String jti) {
        return jti != null && blacklist.getIfPresent(jti) != null;
    }

    // ---------- 登录防爆破 ----------

    public boolean isLocked(String key) {
        Long until = lockUntil.getIfPresent(key);
        if (until == null) {
            return false;
        }
        if (until <= System.currentTimeMillis()) {
            lockUntil.invalidate(key);
            return false;
        }
        return true;
    }

    /** 记录一次失败，返回本次是否已触发锁定 */
    public boolean recordFailure(String key) {
        int count = failures.get(key, k -> new AtomicInteger(0)).incrementAndGet();
        if (count >= props.getLoginMaxFail()) {
            lockUntil.put(key, System.currentTimeMillis() + props.getLoginLockMinutes() * 60_000L);
            failures.invalidate(key);
            return true;
        }
        return false;
    }

    public void clearFailures(String key) {
        failures.invalidate(key);
        lockUntil.invalidate(key);
    }

    public long lockRemainSeconds(String key) {
        Long until = lockUntil.getIfPresent(key);
        return until == null ? 0L : Math.max(0L, (until - System.currentTimeMillis()) / 1000);
    }

    // ---------- 全局接口限流（固定窗口 + 本地计数，轻量令牌桶替代方案） ----------

    public boolean tryAcquire(String key, long limitPerMinute) {
        RateWindow window = rateWindows.get(key, k -> new RateWindow());
        return window.tryAcquire(limitPerMinute);
    }

    private static final class RateWindow {
        private long windowStart = System.currentTimeMillis();
        private final AtomicInteger count = new AtomicInteger(0);

        synchronized boolean tryAcquire(long limit) {
            long now = System.currentTimeMillis();
            // 跨窗口则重置计数，避免旧窗口计数长期占用
            if (now - windowStart >= 60_000L) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet() <= limit;
        }
    }
}
