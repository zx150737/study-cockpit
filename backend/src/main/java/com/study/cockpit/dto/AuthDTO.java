package com.study.cockpit.dto;

import jakarta.validation.constraints.NotBlank;
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
        private String username;

        @NotBlank(message = "密码不能为空")
        @Size(min = 8, max = 128, message = "密码长度需在 8~128 之间")
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
}
