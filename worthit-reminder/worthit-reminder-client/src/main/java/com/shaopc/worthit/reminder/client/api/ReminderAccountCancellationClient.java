package com.shaopc.worthit.reminder.client.api;

import com.shaopc.worthit.reminder.client.command.ReminderAccountCancellationCommand;
import com.shaopc.worthit.reminder.client.response.ReminderAccountCancellationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** Auth 调用 Reminder 执行账号注销清理的运行时中立契约。 */
@HttpExchange(accept = "application/json", contentType = "application/json")
public interface ReminderAccountCancellationClient {

    /** 幂等清理指定用户的全部 Reminder 数据。 */
    @PostExchange("/internal/v1/reminders/users/{userId}/account-cancellation")
    ReminderAccountCancellationResponse cancelAccount(
            @PathVariable("userId") long userId,
            @NotBlank @RequestHeader("X-Idempotency-Key") String cancellationId,
            @Valid @RequestBody ReminderAccountCancellationCommand command);
}
