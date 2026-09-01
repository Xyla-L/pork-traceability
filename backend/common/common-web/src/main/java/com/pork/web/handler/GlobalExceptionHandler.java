package com.pork.web.handler;

import com.pork.core.enums.ErrorCode;
import com.pork.core.exception.BusinessException;
import com.pork.core.result.Result;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * 全局异常处理器
 * 规范：统一拦截并处理Controller层抛出的所有异常，转换为Result格式返回
 */
@Slf4j
@RestControllerAdvice // 相当于 @ControllerAdvice + @ResponseBody
public class GlobalExceptionHandler {

    /**
     * 拦截自定义业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常：code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 拦截 @RequestBody 参数校验异常 (BindException / MethodArgumentNotValidException)
     */
    @ExceptionHandler({BindException.class, MethodArgumentNotValidException.class})
    public Result<Void> handleValidException(Exception e) {
        String errorMessage = "参数校验失败";
        if (e instanceof MethodArgumentNotValidException) {
            MethodArgumentNotValidException ex = (MethodArgumentNotValidException) e;
            errorMessage = ex.getBindingResult().getFieldErrors().stream()
                    .map(FieldError::getDefaultMessage)
                    .collect(Collectors.joining(", "));
        }
        log.warn("参数校验异常：{}", errorMessage);
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(), errorMessage);
    }

    /**
     * 拦截 @RequestParam / @PathVariable 参数校验异常
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Void> handleConstraintViolationException(ConstraintViolationException e) {
        String errorMessage = e.getConstraintViolations().stream()
                .map(violation -> violation.getMessage())
                .collect(Collectors.joining(", "));
        log.warn("参数校验异常：{}", errorMessage);
        return Result.fail(ErrorCode.PARAM_ERROR.getCode(), errorMessage);
    }

    /**
     * 兜底：拦截其他所有未知系统异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统未知异常", e);
        return Result.fail(ErrorCode.SYSTEM_ERROR);
    }
}