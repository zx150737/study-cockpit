package com.study.cockpit.common;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.io.Serializable;
import java.util.List;

/**
 * 全局统一响应体。生产环境不返回异常堆栈，仅返回脱敏错误码与提示。
 */
@Data
public class Result<T> implements Serializable {

    private int code;
    private String message;
    private T data;
    private String traceId;
    private long timestamp;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
        // 链路追踪 ID：由 RequestContextFilter 写入 MDC，便于日志排查
        this.traceId = MDC.get("traceId");
        this.timestamp = System.currentTimeMillis();
    }

    public static <T> Result<T> ok(T data) {
        return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMessage(), data);
    }

    public static <T> Result<T> ok() {
        return ok(null);
    }

    public static <T> Result<T> fail(ResultCode rc) {
        return new Result<>(rc.getCode(), rc.getMessage(), null);
    }

    public static <T> Result<T> fail(ResultCode rc, String message) {
        return new Result<>(rc.getCode(), message, null);
    }

    /** 统一分页结构，避免把 MyBatis-Plus 的 IPage 内部结构直接暴露给前端 */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Page<T> implements Serializable {
        private List<T> list;
        private long total;
        private long page;
        private long size;
    }

    public static <T> Result<Page<T>> page(List<T> list, long total, long page, long size) {
        return ok(new Page<>(list, total, page, size));
    }
}
