package com.study.cockpit.security;

import com.study.cockpit.common.BusinessException;
import com.study.cockpit.common.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.Serializable;

/**
 * 登录上下文工具。所有业务查询都必须通过 getUserId() 注入过滤条件，
 * 这是数据隔离（安全要求 8）的统一入口，禁止在 Controller/Service 里从请求参数接收 userId。
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    @Data
    @AllArgsConstructor
    public static class LoginUser implements Serializable {
        private Long userId;
        private String username;
        private Integer tokenVersion;
    }

    public static LoginUser getLoginUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof LoginUser user)) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return user;
    }

    public static Long getUserId() {
        return getLoginUser().getUserId();
    }
}
