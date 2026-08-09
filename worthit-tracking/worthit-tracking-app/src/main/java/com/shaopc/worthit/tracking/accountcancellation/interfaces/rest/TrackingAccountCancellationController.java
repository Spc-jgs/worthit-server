package com.shaopc.worthit.tracking.accountcancellation.interfaces.rest;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.error.SecurityErrorCode;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.tracking.accountcancellation.application.TrackingAccountCancellationService;
import com.shaopc.worthit.tracking.client.command.TrackingAccountCancellationCommand;
import com.shaopc.worthit.tracking.client.response.TrackingAccountCancellationResponse;
import com.shaopc.worthit.tracking.interfaces.rest.PositiveLongIdParser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/** 仅供 Auth 在可信内部边界内调用的 Tracking 注销清理接口。 */
@RestController
@Tag(name = "内部账号注销", description = "Auth 账号注销编排使用")
public class TrackingAccountCancellationController {

    private static final String AUTH_CALLER = "worthit-auth";
    private final TrackingAccountCancellationService service;

    public TrackingAccountCancellationController(
            TrackingAccountCancellationService service) {
        this.service = service;
    }

    @PostMapping("/internal/v1/tracking/users/{userId}/account-cancellation")
    @Operation(summary = "清理用户 Tracking 数据")
    public TrackingAccountCancellationResponse cancel(
            @PathVariable("userId") String userId,
            @RequestHeader("X-Idempotency-Key") String idempotencyKey,
            @RequestHeader(SecurityHeaderNames.CALLER_SERVICE) String callerService,
            @Valid @RequestBody TrackingAccountCancellationCommand command) {
        if (!AUTH_CALLER.equals(callerService)) {
            throw new BusinessException(SecurityErrorCode.AUTH_FORBIDDEN);
        }
        if (!idempotencyKey.equals(command.cancellationId())) {
            throw new BusinessException(CommonWebErrorCode.VAL_INVALID_ARGUMENT);
        }
        return service.cancel(
                Objects.requireNonNull(PositiveLongIdParser.parseNullable(userId)),
                command.cancellationId());
    }
}
