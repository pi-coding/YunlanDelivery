package com.fastdine.handler;

import com.fastdine.constant.MessageConstant;
import com.fastdine.exception.BaseException;
import com.fastdine.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 全局异常处理器，处理项目中抛出的业务异常
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * 从 Duplicate entry 'zhangsan' for key 'idx_username' 中提取重复的值
     */
    private static final Pattern DUPLICATE_ENTRY = Pattern.compile("Duplicate entry '([^']*)'");

    /**
     * 捕获业务异常
     *
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BaseException ex) {
        log.error("异常信息：{}", ex.getMessage());
        return Result.error(ex.getMessage());
    }

    /**
     * 捕获sql异常（例如用户名已存在时唯一索引冲突）
     *
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(SQLIntegrityConstraintViolationException ex) {
        log.error("异常信息：{}", ex.getMessage());
        String message = ex.getMessage();
        if (message != null && message.contains("Duplicate entry")) {
            //用正则提取引号内的值，避免按空格切分时下标越界（MySQL 不同版本的提示文案并不一致）
            Matcher matcher = DUPLICATE_ENTRY.matcher(message);
            if (matcher.find()) {
                return Result.error(matcher.group(1) + MessageConstant.ALREADY_EXISTS);
            }
        }
        return Result.error(MessageConstant.UNKNOWN_ERROR);
    }

    /**
     * 捕获 @RequestBody 上的参数校验失败（@Valid 触发），把第一条校验信息返回给前端
     *
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(BindException ex) {
        String message = MessageConstant.UNKNOWN_ERROR;
        FieldError fieldError = ex.getBindingResult().getFieldError();
        if (fieldError != null && fieldError.getDefaultMessage() != null) {
            message = fieldError.getDefaultMessage();
        }
        log.error("参数校验失败：{}", message);
        return Result.error(message);
    }

    /**
     * 捕获方法参数上的校验失败（@Validated + @RequestParam 等场景）
     *
     * @param ex
     * @return
     */
    @ExceptionHandler
    public Result exceptionHandler(ConstraintViolationException ex) {
        String message = MessageConstant.UNKNOWN_ERROR;
        for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
            if (violation.getMessage() != null) {
                message = violation.getMessage();
                break;
            }
        }
        log.error("参数校验失败：{}", message);
        return Result.error(message);
    }
}
