package com.smart.office.common.exception;

import com.smart.office.common.response.R;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 全局异常处理器
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public R<?> handleBusinessException(BusinessException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public R<?> handleValidationException(HandlerMethodValidationException ex) {

        Map<String, Object> response = new HashMap<>();
        response.put("status", 400);
        response.put("message", "参数校验失败");

        // 获取具体的校验错误
        List<String> errors = new ArrayList<>();
        for (MessageSourceResolvable error : ex.getAllErrors()) {
            if (error instanceof FieldError) {
                FieldError fieldError = (FieldError) error;
                errors.add(fieldError.getField() + ": " + fieldError.getDefaultMessage());
            } else if (error instanceof ObjectError) {
                errors.add(error.getDefaultMessage());
            }
        }
        response.put("errors", errors);

        log.error("参数校验失败: {}", response);

        return R.fail(400, "参数校验失败");
    }

    /**
     * 处理其他异常
     */
    @ExceptionHandler(Exception.class)
    public R<?> handleException(Exception e) {
        e.printStackTrace();
        return R.fail(500, "系统内部错误");
    }
}