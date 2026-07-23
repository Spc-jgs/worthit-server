package com.shaopc.worthit.reminder.client.api;

import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.contract.ReminderClientContract;
import com.shaopc.worthit.reminder.client.response.ReconcileReminderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * Tracking 调用 Reminder 的内部命令契约。
 *
 * <p>本接口只声明 HTTP 协议，不负责代理创建、鉴权、服务发现或错误解码。</p>
 */
@HttpExchange(ReminderClientContract.BASE_PATH)
public interface ReminderCommandClient {

    /**
     * 按 Tracking 完整期望状态协调 Reminder Binding 与提醒实例。
     *
     * @param eventId Outbox 事件标识，用作幂等键
     * @param command 完整期望状态命令
     * @return Reminder reconcile 处理结果
     */
    @PostExchange(ReminderClientContract.RECONCILE_PATH)
    ReconcileReminderResponse reconcile(
            @NotBlank(message = "幂等键不能为空")
            @RequestHeader(ReminderClientContract.IDEMPOTENCY_HEADER)
            String eventId,
            @Valid @RequestBody ReconcileReminderCommand command);
}
