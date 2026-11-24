package com.study.cockpit.security;

import com.study.cockpit.common.ResultCode;
import com.study.cockpit.config.AppProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 全局接口限流（安全要求 3）。
 * 按「客户端 IP + 归一化 URI」做每分钟固定窗口计数；登录接口使用更严格的独立阈值。
 * 归一化把数字 ID 折叠成 {id}，避免攻击者用不同 ID 绕过限流。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final SecurityCacheService cache;
    private final AppProperties props;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String uri = normalize(request.getRequestURI());
        boolean isLogin = "/api/auth/login".equals(uri);
        long limit = isLogin ? props.getLoginRateLimitPerMinute() : props.getRateLimitPerMinute();
        String key = clientIp(request) + "|" + uri;

        if (!cache.tryAcquire(key, limit)) {
            log.warn("触发限流 key={} limit={}/min", key, limit);
            response.setStatus(ResultCode.RATE_LIMITED.getHttpStatus());
            response.setContentType("application/json;charset=UTF-8");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write("{\"code\":" + ResultCode.RATE_LIMITED.getCode()
                    + ",\"message\":\"" + ResultCode.RATE_LIMITED.getMessage()
                    + "\",\"timestamp\":" + System.currentTimeMillis() + "}");
            return;
        }
        chain.doFilter(request, response);
    }

    /** /api/tasks/123 -> /api/tasks/{id} */
    private String normalize(String uri) {
        return uri == null ? "" : uri.replaceAll("/\\d+(?=/|$)", "/{id}");
    }

    /**
     * 取 X-Forwarded-For 的最后一个地址：Nginx 以 $proxy_add_x_forwarded_for 追加真实远端 IP，
     * 客户端可伪造前面的值，取最后一个才不会被绕过。无代理时回退 remoteAddr。
     */
    private String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String[] parts = xff.split(",");
            return parts[parts.length - 1].trim();
        }
        String real = request.getHeader("X-Real-IP");
        if (real != null && !real.isBlank()) {
            return real.trim();
        }
        return request.getRemoteAddr();
    }
}
