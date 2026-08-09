package com.shaopc.worthit.reminder.app.accountcancellation.interfaces.rest;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.error.SecurityErrorCode;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.reminder.app.accountcancellation.application.ReminderAccountCancellationService;
import com.shaopc.worthit.reminder.client.command.ReminderAccountCancellationCommand;
import com.shaopc.worthit.reminder.client.response.ReminderAccountCancellationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** 仅供 Auth 在可信内部边界内调用的 Reminder 注销清理接口。 */
@RestController
@Tag(name = "内部账号注销", description = "Auth 账号注销编排使用")
public class ReminderAccountCancellationController {

    private static final String AUTH_CALLER = "worthit-auth";
    private final ReminderAccountCancellationService service;

    public ReminderAccountCancellationController(
            ReminderAccountCancellationService service) {
        this.service = service;
    }

    @PostMapping("/internal/v1/reminders/users/{userId}/account-cancellation")
    @Operation(summary = "清理用户 Reminder 数据")
    public ReminderAccountCancellationResponse cancel(
            @PathVariable("userId") String userId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestHeader(SecurityHeaderNames.CALLER_SERVICE) String callerService,
            @Valid @RequestBody ReminderAccountCancellationCommand command) {
        if (!AUTH_CALLER.equals(callerService)) {
            throw new BusinessException(SecurityErrorCode.AUTH_FORBIDDEN);
        }
        if (!idempotencyKey.equals(command.cancellationId())) {
            throw new BusinessException(CommonWebErrorCode.VAL_INVALID_ARGUMENT);
        }
        return service.cancel(parsePositiveLong(userId), command.cancellationId());
    }

    private static long parsePositiveLong(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed > 0) {
                return parsed;
            }
        } catch (NumberFormatException ignored) {
            // 统一转换为稳定参数错误。
        }
        throw new BusinessException(CommonWebErrorCode.VAL_INVALID_ARGUMENT);
    }
}
