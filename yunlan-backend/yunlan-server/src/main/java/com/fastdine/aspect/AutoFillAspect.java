package com.fastdine.aspect;

import com.fastdine.annotation.AutoFill;
import com.fastdine.constant.AutoFillConstant;
import com.fastdine.context.BaseContext;
import com.fastdine.enumeration.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.LocalDateTime;

/**
 * 自定义切面，实现公共字段（创建时间/创建人/修改时间/修改人）自动填充
 */
@Aspect
@Component
@Slf4j
public class AutoFillAspect {

    /**
     * 切入点：mapper 包下所有加了 @AutoFill 注解的方法
     */
    @Pointcut("execution(* com.fastdine.mapper.*.*(..)) && @annotation(com.fastdine.annotation.AutoFill)")
    public void autoFillPointCut() {
    }

    /**
     * 前置通知，在 SQL 执行前为实体对象的公共字段赋值
     */
    @Before("autoFillPointCut()")
    public void autoFill(JoinPoint joinPoint) {
        log.debug("开始进行公共字段自动填充...");

        //获取被拦截方法上的数据库操作类型
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        AutoFill autoFill = signature.getMethod().getAnnotation(AutoFill.class);
        OperationType operationType = autoFill.value();

        //获取被拦截方法的第一个参数（实体对象），约定实体对象放在第一个参数位置
        Object[] args = joinPoint.getArgs();
        if (args == null || args.length == 0) {
            return;
        }
        Object entity = args[0];
        if (entity == null) {
            return;
        }

        //准备赋值的数据
        LocalDateTime now = LocalDateTime.now();
        Long currentId = BaseContext.getCurrentId();

        try {
            if (operationType == OperationType.INSERT) {
                invoke(entity, AutoFillConstant.SET_CREATE_TIME, LocalDateTime.class, now);
                invoke(entity, AutoFillConstant.SET_CREATE_USER, Long.class, currentId);
                invoke(entity, AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class, now);
                invoke(entity, AutoFillConstant.SET_UPDATE_USER, Long.class, currentId);
            } else if (operationType == OperationType.UPDATE) {
                invoke(entity, AutoFillConstant.SET_UPDATE_TIME, LocalDateTime.class, now);
                invoke(entity, AutoFillConstant.SET_UPDATE_USER, Long.class, currentId);
            }
        } catch (Exception e) {
            log.error("公共字段自动填充失败：{}", e.getMessage());
        }
    }

    /**
     * 反射调用 setter，实体未定义该方法时静默跳过
     */
    private void invoke(Object entity, String methodName, Class<?> paramType, Object value) throws Exception {
        Method setter = entity.getClass().getDeclaredMethod(methodName, paramType);
        setter.invoke(entity, value);
    }
}
