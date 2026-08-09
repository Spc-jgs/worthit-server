package com.shaopc.worthit.reminder.client.command;

import jakarta.validation.constraints.NotBlank;

/** Auth 请求 Reminder 清理指定用户数据的稳定命令。 */
public record ReminderAccountCancellationCommand(
        @NotBlank(message = "注销标识不能为空") String cancellationId) {
}
