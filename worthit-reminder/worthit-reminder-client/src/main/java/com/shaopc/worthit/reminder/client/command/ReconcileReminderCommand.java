package com.shaopc.worthit.reminder.client.command;

import com.shaopc.worthit.reminder.client.contract.ReminderClientContract;
import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderOperationType;
import com.shaopc.worthit.reminder.client.model.ReminderType;
import com.shaopc.worthit.reminder.client.validation.ValidReconcileReminderCommand;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Tracking 发往 Reminder 的完整期望状态命令。
 *
 * @param userId 用户标识
 * @param businessType 业务对象类型
 * @param businessId 业务对象标识
 * @param reminderType 提醒类型
 * @param sourceVersion Tracking 业务对象来源版本
 * @param businessDate 提醒计算使用的业务日期，可空
 * @param remindAt 计算后的无时区提醒时间，可空
 * @param reminderEnabled 是否期望启用提醒
 * @param businessStatusCode Tracking 业务状态码
 * @param operationType 服务端生成的业务操作类型
 * @param schemaVersion reconcile 契约版本，M1 固定为 1
 */
@ValidReconcileReminderCommand
public record ReconcileReminderCommand(
        @Positive(message = "用户标识必须大于0")
        long userId,
        @NotNull(message = "业务类型不能为空")
        ReminderBusinessType businessType,
        @Positive(message = "业务对象标识必须大于0")
        long businessId,
        @NotNull(message = "提醒类型不能为空")
        ReminderType reminderType,
        @Positive(message = "来源版本必须大于0")
        long sourceVersion,
        LocalDate businessDate,
        LocalDateTime remindAt,
        boolean reminderEnabled,
        @NotBlank(message = "业务状态码不能为空")
        String businessStatusCode,
        @NotNull(message = "操作类型不能为空")
        ReminderOperationType operationType,
        @Min(value = ReminderClientContract.SCHEMA_VERSION, message = "契约版本必须为1")
        @Max(value = ReminderClientContract.SCHEMA_VERSION, message = "契约版本必须为1")
        int schemaVersion) {
}
