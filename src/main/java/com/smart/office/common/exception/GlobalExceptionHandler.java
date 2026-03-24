package com.smart.office.common.exception;

import com.smart.office.common.response.R;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

/**
 * 全局异常处理器，统一返回 JSON 结构。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常统一处理。
     */
    @ExceptionHandler(BusinessException.class)
    public R<?> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * Spring Validation 参数校验异常处理。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public R<?> handleValidationException(HandlerMethodValidationException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("status", 400);
        response.put("message", "参数校验失败");

        List<String> errors = new ArrayList<>();
        for (MessageSourceResolvable error : ex.getAllErrors()) {
            if (error instanceof FieldError fieldError) {
                errors.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
            } else if (error instanceof ObjectError objectError) {
                errors.add(objectError.getDefaultMessage());
            }
        }
        response.put("errors", errors);

        log.error("参数校验失败: {}", response);
        return R.fail(400, "参数校验失败");
    }

    /**
     * 未登录或凭证失效，返回 401。
     */
    @ExceptionHandler(AuthenticationException.class)
    public R<?> handleAuthenticationException(AuthenticationException e) {
        log.warn("认证异常: {}", e.getMessage());
        return R.fail(401, "未登录或登录已过期");
    }

    /**
     * 已登录但缺少权限，返回 403。
     */
    @ExceptionHandler(AccessDeniedException.class)
    public R<?> handleAccessDeniedException(AccessDeniedException e) {
        log.warn("权限不足: {}", e.getMessage());
        return R.fail(403, "没有访问该资源的权限");
    }

    /**
     * 其他未捕获异常，统一转为 500。
     */
    @ExceptionHandler(Exception.class)
    public R<?> handleException(Exception e) {
        log.error("系统内部异常", e);
        return R.fail(500, "系统内部错误");
    }
}
