package com.study.cockpit.common;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一错误码。越权访问复用 NOT_FOUND(404)，避免向攻击者泄露资源是否存在。
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(0, 200, "成功"),
    PARAM_ERROR(40001, 400, "参数校验失败"),
    UNAUTHORIZED(40101, 401, "未登录或登录已失效"),
    FORBIDDEN(40301, 403, "禁止访问"),
    NOT_FOUND(40401, 404, "资源不存在"),
    FILE_TOO_LARGE(41301, 413, "文件超过大小限制"),
    FILE_TYPE_REJECTED(41501, 415, "文件类型或内容校验不通过"),
    RATE_LIMITED(42901, 429, "操作过于频繁，请稍后再试"),
    INTERNAL_ERROR(50000, 500, "服务器内部错误");

    private final int code;
    private final int httpStatus;
    private final String message;
}
