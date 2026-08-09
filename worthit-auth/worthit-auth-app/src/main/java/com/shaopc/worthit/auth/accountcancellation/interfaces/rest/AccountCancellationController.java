package com.shaopc.worthit.auth.accountcancellation.interfaces.rest;

import com.shaopc.worthit.auth.accountcancellation.application.AccountCancellationService;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Auth 公网账号注销接口。 */
@RestController
@RequestMapping("/api/v1/auth/cancellation")
@Tag(name = "账号注销", description = "申请、查询与冷静期撤销")
public class AccountCancellationController {

    private final AccountCancellationService service;

    public AccountCancellationController(AccountCancellationService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "申请账号注销")
    public ApiResponse<AccountCancellationResponse> apply(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID) String traceId) {
        return ApiResponse.success(service.apply(idempotencyKey), traceId);
    }

    @GetMapping
    @Operation(summary = "查询账号注销状态")
    public ApiResponse<AccountCancellationStatusResponse> status(
            @RequestAttribute(SecurityHeaderNames.TRACE_ID) String traceId) {
        return ApiResponse.success(service.status(), traceId);
    }

    @PostMapping("/revoke")
    @Operation(summary = "撤销账号注销")
    public ApiResponse<AccountCancellationResponse> revoke(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RevokeAccountCancellationRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID) String traceId) {
        return ApiResponse.success(
                service.revoke(
                        idempotencyKey,
                        request.cancellationId(),
                        request.version()),
                traceId);
    }
}
