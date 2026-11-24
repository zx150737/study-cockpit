package com.study.cockpit.security;

import com.study.cockpit.common.ResultCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.regex.Pattern;

/**
 * 全局 XSS 过滤（安全要求 4）。
 *
 * 采用「危险模式检测 + 拒绝」而非「改写文本」：不修改任何入参值，
 * 因此不会破坏登录密码等凭据字段，也不会把用户复盘备注悄悄改样。
 * 输出侧再由 Vue 的 {{ }} 默认转义、CSP 与 Content-Type:nosniff 兜底。
 *
 * multipart 上传请求不读取 body，避免消耗文件流导致 MultipartFile 解析失败。
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class XssFilter extends OncePerRequestFilter {

    private static final Pattern DANGEROUS = Pattern.compile(
            "(?i)<\\s*script|</\\s*script|javascript\\s*:|<\\s*iframe|<\\s*object|<\\s*embed"
                    + "|\\sonerror\\s*=|\\sonload\\s*=|\\beval\\s*\\(");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        boolean multipart = request.getContentType() != null
                && request.getContentType().toLowerCase().startsWith("multipart/");

        if (multipart) {
            // 仅检查 URL 查询串与请求头，原始请求体交给 MultipartResolver
            if (dangerous(request.getQueryString()) || dangerousHeaders(request)) {
                reject(response);
                return;
            }
            chain.doFilter(request, response);
            return;
        }

        XssRequestWrapper wrapper = new XssRequestWrapper(request);
        if (dangerous(request.getQueryString()) || dangerousHeaders(wrapper) || dangerous(wrapper.getBody())) {
            reject(response);
            return;
        }
        chain.doFilter(wrapper, response);
    }

    private boolean dangerous(String text) {
        return text != null && DANGEROUS.matcher(text).find();
    }

    private boolean dangerousHeaders(HttpServletRequest request) {
        Enumeration<String> names = request.getHeaderNames();
        if (names == null) {
            return false;
        }
        for (String name : Collections.list(names)) {
            if (dangerous(request.getHeader(name))) {
                return true;
            }
        }
        return false;
    }

    private void reject(HttpServletResponse response) throws IOException {
        log.warn("XSS 危险字符检测命中，拒绝请求");
        response.setStatus(ResultCode.PARAM_ERROR.getHttpStatus());
        response.setContentType("application/json;charset=UTF-8");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"code\":" + ResultCode.PARAM_ERROR.getCode()
                + ",\"message\":\"请求包含不允许的字符\",\"timestamp\":"
                + System.currentTimeMillis() + "}");
    }

    /** 缓存请求体，使检测之后 Controller 仍能正常 @RequestBody 读取 */
    public static class XssRequestWrapper extends HttpServletRequestWrapper {

        private final byte[] body;

        public XssRequestWrapper(HttpServletRequest request) throws IOException {
            super(request);
            this.body = StreamUtils.copyToByteArray(request.getInputStream());
        }

        public String getBody() {
            return new String(body, StandardCharsets.UTF_8);
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream in = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return in.read();
                }

                @Override
                public boolean isFinished() {
                    return in.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("不支持异步读取");
                }
            };
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String charset = getCharacterEncoding() == null ? "UTF-8" : getCharacterEncoding();
            return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset));
        }
    }
}
