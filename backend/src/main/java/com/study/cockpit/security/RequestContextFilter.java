package com.study.cockpit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 链路追踪 ID + 安全响应头（安全要求 9）。
 * HSTS 由 Nginx 层统一添加；此处负责应用层可控制的全部安全头。
 * CSP 允许 style-src 'unsafe-inline'（Element Plus 行内样式）与 img-src blob:（上传预览）。
 */
@Component("cockpitRequestContextFilter")
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class RequestContextFilter extends OncePerRequestFilter {

    private static final String CSP = "default-src 'self'; "
            + "script-src 'self'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: blob:; "
            + "font-src 'self' data:; "
            + "connect-src 'self'; "
            + "object-src 'none'; "
            + "frame-ancestors 'none'; "
            + "base-uri 'self'; "
            + "form-action 'self'";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString().replace("-", "");
        MDC.put("traceId", traceId);

        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("Permissions-Policy", "geolocation=(), camera=(), microphone=()");
        response.setHeader("Content-Security-Policy", CSP);
        response.setHeader("Cross-Origin-Opener-Policy", "same-origin");
        response.setHeader("Cross-Origin-Resource-Policy", "same-origin");
        response.setHeader("X-Trace-Id", traceId);

        if (request.getRequestURI().startsWith("/api/")) {
            // 业务接口一律不缓存，避免含个人数据的响应落盘
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
            response.setHeader("Pragma", "no-cache");
        }
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("traceId");
        }
    }
}
