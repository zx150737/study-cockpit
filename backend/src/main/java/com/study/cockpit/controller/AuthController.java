package com.study.cockpit.controller;

import com.study.cockpit.common.Result;
import com.study.cockpit.dto.AuthDTO;
import com.study.cockpit.config.AppProperties;
import com.study.cockpit.security.SecurityUtils;
import com.study.cockpit.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 认证接口：登录 / 注册 / 注销 / 当前用户 / CSRF 令牌预取 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AppProperties props;

    /**
     * 登录页加载时调用一次，触发 CookieCsrfTokenRepository 下发 XSRF-TOKEN。
     * 响应体不含任何敏感信息。
     */
    @GetMapping("/csrf")
    public Result<Void> csrf() {
        return Result.ok();
    }

    @PostMapping("/login")
    public Result<AuthDTO.UserVO> login(@Valid @RequestBody AuthDTO.LoginReq req,
                                        HttpServletResponse response) {
        return Result.ok(authService.login(req, response));
    }

    @GetMapping("/registration")
    public Result<AuthDTO.RegistrationConfig> registration() {
        return Result.ok(new AuthDTO.RegistrationConfig(props.isAllowRegister()));
    }

    /** 公开注册由 APP_ALLOW_REGISTER 控制，关闭时接口同样拒绝注册。 */
    @PostMapping("/register")
    public Result<AuthDTO.UserVO> register(@Valid @RequestBody AuthDTO.RegisterReq req) {
        return Result.ok(authService.register(req));
    }

    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
        return Result.ok();
    }

    @GetMapping("/me")
    public Result<AuthDTO.UserVO> me() {
        return Result.ok(authService.currentUser(SecurityUtils.getUserId()));
    }

    /** 修改密码：校验旧密码，成功后旧 JWT 全部失效，需重新登录 */
    @PostMapping("/change-password")
    public Result<Void> changePassword(@Valid @RequestBody AuthDTO.ChangePasswordReq req) {
        authService.changePassword(SecurityUtils.getUserId(), req);
        return Result.ok();
    }
}
