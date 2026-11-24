package com.study.cockpit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** 认证模块请求/响应对象 */
public class AuthDTO {

    @Data
    public static class LoginReq {
        @NotBlank(message = "用户名不能为空")
        @Size(max = 64, message = "用户名过长")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(max = 128, message = "密码过长")
        // 注意：密码字段不做任何改写/转义，避免破坏凭据；XSS 由全局危险字符检测统一拒绝
        private String password;
    }

    @Data
    public static class RegisterReq {
        @NotBlank(message = "用户名不能为空")
        @Size(min = 3, max = 64, message = "用户名长度需在 3~64 之间")
        @Pattern(regexp = "[A-Za-z0-9_.-]+", message = "用户名仅支持英文字母、数字、下划线、点和短横线")
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 72, message = "密码长度需在 8~72 之间")
        private String password;

        @Size(max = 64, message = "昵称过长")
        private String nickname;
    }

    @Data
    public static class UserVO {
        private Long id;
        private String username;
        private String nickname;
    }

    /** 仅公开注册开关，不返回服务端密钥或其他部署配置。 */
    public record RegistrationConfig(boolean enabled) {}

    /** 修改密码请求：校验旧密码，BCrypt 存新密文，改密后旧 JWT 全部失效 */
    @Data
    public static class ChangePasswordReq {
        @NotBlank(message = "当前密码不能为空")
        @Size(max = 128, message = "密码过长")
        private String oldPassword;

        @NotBlank(message = "新密码不能为空")
        @Size(min = 8, max = 72, message = "新密码长度需在 8~72 之间")
        private String newPassword;
    }
}
