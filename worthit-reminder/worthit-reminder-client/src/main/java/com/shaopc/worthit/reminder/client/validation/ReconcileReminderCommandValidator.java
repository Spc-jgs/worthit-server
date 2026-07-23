package com.shaopc.worthit.reminder.client.validation;

import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderOperationType;
import com.shaopc.worthit.reminder.client.model.ReminderType;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Reminder reconcile 命令跨字段契约校验器。
 */
public final class ReconcileReminderCommandValidator
        implements ConstraintValidator<ValidReconcileReminderCommand, ReconcileReminderCommand> {

    /**
     * 所有业务对象都允许的操作类型。
     */
    private static final Set<ReminderOperationType> COMMON_OPERATIONS = Set.of(
            ReminderOperationType.INITIAL_SYNC,
            ReminderOperationType.ENABLE_REMINDER,
            ReminderOperationType.DISABLE_REMINDER,
            ReminderOperationType.UPDATE_BUSINESS_DATE,
            ReminderOperationType.CORRECT_BUSINESS_DATE,
            ReminderOperationType.DELETE_OBJECT);

    /**
     * 仅订阅允许的操作类型。
     */
    private static final Set<ReminderOperationType> SUBSCRIPTION_OPERATIONS = Set.of(
            ReminderOperationType.ADVANCE_NEXT_RENEWAL_DATE,
            ReminderOperationType.PAUSE_SUBSCRIPTION,
            ReminderOperationType.END_SUBSCRIPTION,
            ReminderOperationType.RESUME_SUBSCRIPTION);

    /**
     * 仅想买允许的操作类型。
     */
    private static final Set<ReminderOperationType> WISH_OPERATIONS = Set.of(
            ReminderOperationType.PURCHASE_WISH,
            ReminderOperationType.ABANDON_WISH,
            ReminderOperationType.CONTINUE_CONSIDERING);

    /**
     * 校验业务类型、提醒类型、日期和操作类型是否形成合法契约。
     *
     * @param command 待校验命令
     * @param context 校验上下文
     * @return 命令满足跨字段契约时返回 {@code true}
     */
    @Override
    public boolean isValid(
            ReconcileReminderCommand command,
            ConstraintValidatorContext context) {
        if (command == null) {
            return true;
        }

        context.disableDefaultConstraintViolation();
        boolean valid = true;

        if (command.businessType() != null
                && command.reminderType() != null
                && !isReminderTypeSupported(command.businessType(), command.reminderType())) {
            addViolation(context, "提醒类型与业务类型不匹配", "reminderType");
            valid = false;
        }

        if (command.reminderEnabled() && command.businessDate() == null) {
            addViolation(context, "提醒开启时业务日期不能为空", "businessDate");
            valid = false;
        }
        if (command.reminderEnabled() && command.remindAt() == null) {
            addViolation(context, "提醒开启时提醒时间不能为空", "remindAt");
            valid = false;
        }
        if (command.businessDate() == null
                && !command.reminderEnabled()
                && command.remindAt() != null) {
            addViolation(context, "业务日期为空时提醒时间必须为空", "remindAt");
            valid = false;
        }

        if (command.businessDate() != null
                && command.remindAt() != null
                && command.reminderType() != null
                && !expectedRemindAt(command).equals(command.remindAt())) {
            addViolation(context, "提醒时间不符合提醒类型规则", "remindAt");
            valid = false;
        }

        if (command.businessType() != null
                && command.operationType() != null
                && !isOperationSupported(command.businessType(), command.operationType())) {
            addViolation(context, "操作类型不适用于当前业务类型", "operationType");
            valid = false;
        }
        return valid;
    }

    /**
     * 判断业务对象是否支持指定提醒类型。
     *
     * @param businessType 业务类型
     * @param reminderType 提醒类型
     * @return 类型组合受契约支持时返回 {@code true}
     */
    private static boolean isReminderTypeSupported(
            ReminderBusinessType businessType,
            ReminderType reminderType) {
        return switch (businessType) {
            case ITEM -> reminderType == ReminderType.WARRANTY;
            case SUBSCRIPTION -> reminderType == ReminderType.RENEWAL;
            case WISH -> reminderType == ReminderType.WATCH;
        };
    }

    /**
     * 根据冻结的提醒时间规则计算期望时间。
     *
     * @param command 同时包含非空业务日期和提醒类型的命令
     * @return 精确到分钟的期望提醒时间
     */
    private static LocalDateTime expectedRemindAt(ReconcileReminderCommand command) {
        return switch (command.reminderType()) {
            case RENEWAL -> command.businessDate().minusDays(1).atStartOfDay();
            case WARRANTY -> command.businessDate().minusDays(7).atStartOfDay();
            case WATCH -> command.businessDate().atStartOfDay();
        };
    }

    /**
     * 判断业务对象是否支持指定操作类型。
     *
     * @param businessType 业务类型
     * @param operationType 操作类型
     * @return 操作受契约支持时返回 {@code true}
     */
    private static boolean isOperationSupported(
            ReminderBusinessType businessType,
            ReminderOperationType operationType) {
        if (COMMON_OPERATIONS.contains(operationType)) {
            return true;
        }
        return switch (businessType) {
            case ITEM -> operationType == ReminderOperationType.DISPOSE_ITEM;
            case SUBSCRIPTION -> SUBSCRIPTION_OPERATIONS.contains(operationType);
            case WISH -> WISH_OPERATIONS.contains(operationType);
        };
    }

    /**
     * 把类级校验错误绑定到具体命令字段。
     *
     * @param context 校验上下文
     * @param message 中文校验消息
     * @param property 命令字段名
     */
    private static void addViolation(
            ConstraintValidatorContext context,
            String message,
            String property) {
        context.buildConstraintViolationWithTemplate(message)
                .addPropertyNode(property)
                .addConstraintViolation();
    }
}
