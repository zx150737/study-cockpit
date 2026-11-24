package com.study.cockpit.common;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;

/**
 * 全局异常处理器。
 * 安全要求 6：生产环境只返回脱敏错误码与简短提示，完整堆栈仅写入服务端日志。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private ResponseEntity<Result<Void>> build(ResultCode rc, String message) {
        return ResponseEntity.status(rc.getHttpStatus()).body(Result.fail(rc, message));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<Void>> handleBusiness(BusinessException e) {
        // 业务异常属于可预期失败，不打堆栈，避免日志噪音
        log.warn("业务异常 code={} msg={}", e.getResultCode().getCode(), e.getMessage());
        return build(e.getResultCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<Void>> handleValid(MethodArgumentNotValidException e) {
        FieldError fe = e.getBindingResult().getFieldError();
        String msg = fe == null ? ResultCode.PARAM_ERROR.getMessage() : fe.getField() + " " + fe.getDefaultMessage();
        return build(ResultCode.PARAM_ERROR, msg);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<Void>> handleConstraint(ConstraintViolationException e) {
        return build(ResultCode.PARAM_ERROR, e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Result<Void>> handleBadRequest(Exception e) {
        return build(ResultCode.PARAM_ERROR, "请求参数格式不正确");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Result<Void>> handleUploadSize(MaxUploadSizeExceededException e) {
        return build(ResultCode.FILE_TOO_LARGE, ResultCode.FILE_TOO_LARGE.getMessage());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> handleAuth(AuthenticationException e) {
        return build(ResultCode.UNAUTHORIZED, ResultCode.UNAUTHORIZED.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> handleDenied(AccessDeniedException e) {
        return build(ResultCode.FORBIDDEN, ResultCode.FORBIDDEN.getMessage());
    }

    /**
     * 兜底：记录完整堆栈到服务端日志，但响应体绝不包含任何环境/堆栈信息。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> handleUnknown(Exception e, HttpServletRequest request) {
        log.error("未处理异常 uri={} method={}", request.getRequestURI(), request.getMethod(), e);
        return build(ResultCode.INTERNAL_ERROR, ResultCode.INTERNAL_ERROR.getMessage());
    }
}
