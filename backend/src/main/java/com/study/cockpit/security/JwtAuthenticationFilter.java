package com.study.cockpit.security;

import com.study.cockpit.config.AppProperties;
import com.study.cockpit.entity.SysUser;
import com.study.cockpit.mapper.SysUserMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * JWT 认证过滤器：优先从 HttpOnly Cookie 读取令牌（生产），
 * 兼容 Authorization: Bearer（便于本地/接口调试）。
 *
 * 校验链：签名 -> 过期 -> jti 黑名单 -> 用户状态 -> token_version 版本号。
 * 任一不通过都不写入 SecurityContext，最终由 SecurityConfig 的入口点返回 401。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final List<GrantedAuthority> NO_AUTHORITIES = Collections.emptyList();

    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityCacheService securityCache;
    private final SysUserMapper sysUserMapper;
    private final AppProperties props;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null && !token.isBlank()) {
            try {
                JwtTokenProvider.JwtClaims claims = jwtTokenProvider.parse(token);
                if (!securityCache.isBlacklisted(claims.jti())) {
                    SysUser user = sysUserMapper.selectById(claims.userId());
                    // token_version 不匹配说明用户已改密/被强制下线，令牌立即作废
                    if (user != null
                            && user.getStatus() != null && user.getStatus() == 1
                            && user.getTokenVersion() != null
                            && user.getTokenVersion() == claims.ver()) {
                        SecurityUtils.LoginUser loginUser = new SecurityUtils.LoginUser(
                                user.getId(), user.getUsername(), user.getTokenVersion());
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(loginUser, null, NO_AUTHORITIES);
                        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                        SecurityContextHolder.getContext().setAuthentication(authentication);
                    }
                }
            } catch (JwtException | IllegalArgumentException e) {
                // 令牌无效：不抛异常，交由安全入口点统一返回 401；仅 debug 级别记录，不泄露细节
                log.debug("JWT 校验失败: {}", e.getClass().getSimpleName());
            }
        }
        chain.doFilter(request, response);
    }

    private String resolveToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (props.getJwtCookieName().equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
