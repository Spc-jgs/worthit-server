package com.shaopc.worthit.reminder.client.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 校验 Reminder reconcile 命令的跨字段契约不变量。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = ReconcileReminderCommandValidator.class)
public @interface ValidReconcileReminderCommand {

    /**
     * 默认校验消息。
     *
     * @return 默认校验消息
     */
    String message() default "提醒协调命令不符合契约";

    /**
     * Bean Validation 分组。
     *
     * @return 校验分组
     */
    Class<?>[] groups() default {};

    /**
     * Bean Validation 负载。
     *
     * @return 校验负载
     */
    Class<? extends Payload>[] payload() default {};
}
