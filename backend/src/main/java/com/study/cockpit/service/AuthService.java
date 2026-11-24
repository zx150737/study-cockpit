package com.study.cockpit.service;

import com.study.cockpit.common.BusinessException;
import com.study.cockpit.common.ResultCode;
import com.study.cockpit.config.AppProperties;
import com.study.cockpit.dto.AuthDTO;
import com.study.cockpit.entity.SysUser;
import com.study.cockpit.mapper.SysUserMapper;
import com.study.cockpit.security.JwtTokenProvider;
import com.study.cockpit.security.SecurityCacheService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * 认证服务（安全要求 1/2/3）。
 * - 密码 BCrypt 加盐存储，登录用 matches  constant-time 比较
 * - JWT 通过 HttpOnly + Secure + SameSite=Strict Cookie 下发，前端不接触 token
 * - 登录防爆破：连续失败锁定 + 全局限流（RateLimitFilter）
 * - 注销把 jti 加入黑名单，使该令牌在有效期内也无法再复用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 用户不存在时也做一次真实 BCrypt 比对，抹平响应时间差，防用户名枚举 */
    private static final String DUMMY_HASH =
            new BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final SecurityCacheService securityCache;
    private final AppProperties props;

    /** 自助注册由部署开关控制；账号数据独立，用户名由唯一索引保证。 */
    public AuthDTO.UserVO register(AuthDTO.RegisterReq req) {
        if (!props.isAllowRegister()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "注册接口已关闭");
        }
        validatePasswordBytes(req.getPassword());
        if (sysUserMapper.selectByUsername(req.getUsername()) != null) {
            throw BusinessException.param("用户名已存在");
        }
        SysUser user = new SysUser();
        user.setUsername(req.getUsername());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setNickname(req.getNickname());
        user.setStatus(1);
        user.setTokenVersion(0);
        try {
            sysUserMapper.insert(user);
        } catch (DuplicateKeyException e) {
            // 同名并发注册可能同时通过预检查，由数据库唯一索引兜底。
            throw BusinessException.param("用户名已存在");
        }
        return toVO(user);
    }

    public AuthDTO.UserVO login(AuthDTO.LoginReq req, HttpServletResponse response) {
        String attemptKey = req.getUsername().trim().toLowerCase();
        if (securityCache.isLocked(attemptKey)) {
            throw new BusinessException(ResultCode.RATE_LIMITED,
                    "登录失败次数过多，请 " + securityCache.lockRemainSeconds(attemptKey) + " 秒后再试");
        }

        SysUser user = sysUserMapper.selectByUsername(req.getUsername());
        // 用户不存在时与一个一次性随机密文比对，抹平响应时间差，防用户名枚举
        String storedHash = user == null ? DUMMY_HASH : user.getPassword();
        boolean matched = passwordEncoder.matches(req.getPassword(), storedHash);
        if (user == null || user.getStatus() == null || user.getStatus() != 1 || !matched) {
            boolean locked = securityCache.recordFailure(attemptKey);
            log.warn("登录失败 username={} locked={}", req.getUsername(), locked);
            throw new BusinessException(ResultCode.UNAUTHORIZED,
                    locked ? "登录失败次数过多，账号已临时锁定" : "用户名或密码错误");
        }

        securityCache.clearFailures(attemptKey);
        String token = jwtTokenProvider.issue(user.getId(), user.getUsername(), user.getTokenVersion());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie(token, props.getJwtTtlMinutes() * 60L).toString());
        return toVO(user);
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = resolveToken(request);
        if (token != null) {
            try {
                securityCache.blacklist(jwtTokenProvider.parse(token).jti());
            } catch (Exception ignored) {
                // 令牌本就无效/过期，无需加入黑名单
            }
        }
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("", 0L).toString());
    }

    public AuthDTO.UserVO currentUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return toVO(user);
    }

    /**
     * 修改密码。
     * 1. 校验旧密码（constant-time 比对，失败不区分用户是否存在）；
     * 2. 新密码 BCrypt(12) 加盐存储；
     * 3. token_version +1，使该用户所有旧 JWT 立即失效，强制重新登录。
     */
    public void changePassword(Long userId, AuthDTO.ChangePasswordReq req) {
        validatePasswordBytes(req.getNewPassword());
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "当前密码不正确");
        }
        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "新密码不能与当前密码相同");
        }
        int updated = sysUserMapper.changePassword(userId,
                passwordEncoder.encode(req.getNewPassword()), user.getTokenVersion());
        if (updated != 1) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "账号状态已变化，请重新登录");
        }
        log.info("密码已修改 userId={}，旧会话已失效", userId);
    }

    private void validatePasswordBytes(String password) {
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw BusinessException.param("密码过长：最多 72 个英文字符，中文等字符会占用更多长度");
        }
    }

    private ResponseCookie buildCookie(String value, long maxAgeSeconds) {
        return ResponseCookie.from(props.getJwtCookieName(), value)
                .httpOnly(true)
                .secure(props.isJwtCookieSecure())
                .path("/")
                .sameSite("Strict")
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
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
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        return header != null && header.startsWith("Bearer ") ? header.substring(7) : null;
    }

    private AuthDTO.UserVO toVO(SysUser user) {
        AuthDTO.UserVO vo = new AuthDTO.UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        return vo;
    }
}
