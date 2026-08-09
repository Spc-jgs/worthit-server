package com.shaopc.worthit.tracking.client.api;

import com.shaopc.worthit.tracking.client.command.TrackingAccountCancellationCommand;
import com.shaopc.worthit.tracking.client.response.TrackingAccountCancellationResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** Auth 调用 Tracking 执行账号注销清理的运行时中立契约。 */
@HttpExchange(accept = "application/json", contentType = "application/json")
public interface TrackingAccountCancellationClient {

    /** 幂等清理指定用户的全部 Tracking 数据。 */
    @PostExchange("/internal/v1/tracking/users/{userId}/account-cancellation")
    TrackingAccountCancellationResponse cancelAccount(
            @PathVariable("userId") long userId,
            @NotBlank @RequestHeader("X-Idempotency-Key") String cancellationId,
            @Valid @RequestBody TrackingAccountCancellationCommand command);
}
