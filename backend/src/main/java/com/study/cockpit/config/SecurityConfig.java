package com.study.cockpit.config;

import com.study.cockpit.common.ResultCode;
import com.study.cockpit.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

import java.io.IOException;

/**
 * Spring Security 6 配置。
 *
 * 安全要求 8：除登录/注册/CSRF 预取外，/api/** 全部需要认证；完全无状态。
 * 安全要求 2：JWT 放在 HttpOnly + Secure + SameSite=Strict Cookie 中，前端不接触 token；
 *             配合 CSRF 双提交 Cookie（XSRF-TOKEN / X-XSRF-TOKEN）防止跨站伪造。
 * 生产为同域 Nginx 反代，故不开启 CORS，从源头缩小跨域攻击面。
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** BCrypt 加盐强度 12，禁止明文存储 */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // 关闭 Spring Security 6 默认的 XOR/deferred CSRF token：
        // 让 Cookie 与请求头都使用明文 token，Axios 可直接读取 XSRF-TOKEN 并回传。
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        csrfHandler.setCsrfRequestAttributeName(null);

        http
            .csrf(csrf -> csrf
                    .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                    .csrfTokenRequestHandler(csrfHandler))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                    // 其余所有接口（含图片字节流）一律要求已认证
                    .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint((req, res, e) -> writeError(res, ResultCode.UNAUTHORIZED))
                    .accessDeniedHandler((req, res, e) -> writeError(res, ResultCode.FORBIDDEN)))
            .formLogin(AbstractHttpConfigurer::disable)
            .httpBasic(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .requestCache(cache -> cache.disable())
            // 安全响应头由 RequestContextFilter 统一设置，此处禁用默认重复写入
            .headers(headers -> headers.disable())
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** 认证/鉴权失败返回统一 JSON，不返回任何环境信息或堆栈 */
    private static void writeError(HttpServletResponse res, ResultCode rc) throws IOException {
        res.setStatus(rc.getHttpStatus());
        res.setContentType("application/json;charset=UTF-8");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write("{\"code\":" + rc.getCode()
                + ",\"message\":\"" + rc.getMessage()
                + "\",\"timestamp\":" + System.currentTimeMillis() + "}");
    }
}
