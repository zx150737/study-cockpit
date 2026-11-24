package com.study.cockpit.common;

import lombok.Getter;

/**
 * 类型化业务异常：所有可预期的失败都通过它抛出，由全局异常处理器统一转成脱敏响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    private final ResultCode resultCode;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.resultCode = resultCode;
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.resultCode = resultCode;
    }

    /** 常用快捷构造 */
    public static BusinessException notFound() {
        return new BusinessException(ResultCode.NOT_FOUND);
    }

    public static BusinessException param(String message) {
        return new BusinessException(ResultCode.PARAM_ERROR, message);
    }
}
