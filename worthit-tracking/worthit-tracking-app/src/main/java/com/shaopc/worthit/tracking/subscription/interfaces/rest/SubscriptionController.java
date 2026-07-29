package com.shaopc.worthit.tracking.subscription.interfaces.rest;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.common.web.response.ApiResponse;
import com.shaopc.worthit.tracking.interfaces.rest.PositiveLongIdParser;
import com.shaopc.worthit.tracking.subscription.application.CreateSubscriptionCommand;
import com.shaopc.worthit.tracking.subscription.application.DeleteSubscriptionResult;
import com.shaopc.worthit.tracking.subscription.application.ResumeSubscriptionCommand;
import com.shaopc.worthit.tracking.subscription.application.SubscriptionDetail;
import com.shaopc.worthit.tracking.subscription.application.SubscriptionService;
import com.shaopc.worthit.tracking.subscription.application.SubscriptionSummary;
import com.shaopc.worthit.tracking.subscription.application.UpdateSubscriptionCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * Subscription 公网接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/subscriptions")
@Tag(name = "订阅", description = "订阅成本与状态管理")
@RequiredArgsConstructor
public class SubscriptionController {

    private static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                    + "[1-5][0-9a-fA-F]{3}-"
                    + "[89abAB][0-9a-fA-F]{3}-"
                    + "[0-9a-fA-F]{12}";
    private final SubscriptionService subscriptionService;

    @PostMapping
    @Operation(summary = "新建订阅")
    public ApiResponse<SubscriptionDetailResponse> create(
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false)
            @NotBlank(message = "幂等键不能为空")
            @Pattern(
                    regexp = UUID_PATTERN,
                    message = "幂等键必须是UUID")
            String idempotencyKey,
            @Valid @RequestBody
            CreateSubscriptionRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        requireIdempotencyKey(idempotencyKey);
        return ApiResponse.success(
                toResponse(subscriptionService.create(
                        idempotencyKey,
                        toCommand(request))),
                traceId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询订阅详情")
    public ApiResponse<SubscriptionDetailResponse> detail(
            @Positive @PathVariable("id")
            long subscriptionId,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        return ApiResponse.success(
                toResponse(subscriptionService.detail(
                        subscriptionId)),
                traceId);
    }

    @GetMapping
    @Operation(summary = "分页查询订阅")
    public ApiResponse<SubscriptionPageResponse> list(
            @RequestParam(
                    name = "page",
                    defaultValue = "1")
            @Min(value = 1, message = "页码不能小于1")
            int page,
            @RequestParam(
                    name = "size",
                    defaultValue = "20")
            @Min(value = 1, message = "每页条数不能小于1")
            @Max(value = 50, message = "每页条数不能大于50")
            int size,
            @RequestParam(
                    name = "keyword",
                    required = false)
            String keyword,
            @RequestParam(
                    name = "categoryId",
                    required = false)
            @Pattern(
                    regexp = "[1-9]\\d{0,18}",
                    message = "分类标识格式不正确")
            String categoryId,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        PageResult<SubscriptionSummary> result =
                subscriptionService.list(
                        page,
                        size,
                        keyword,
                        PositiveLongIdParser.parseNullable(
                                categoryId));
        List<SubscriptionSummaryResponse> items = result
                .getItems()
                .stream()
                .map(this::toSummaryResponse)
                .toList();
        return ApiResponse.success(
                new SubscriptionPageResponse(
                        items,
                        result.getPage(),
                        result.getSize(),
                        result.getTotal(),
                        result.isHasMore()),
                traceId);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "更新订阅")
    public ApiResponse<SubscriptionDetailResponse> update(
            @Positive @PathVariable("id")
            long subscriptionId,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false)
            @NotBlank(message = "幂等键不能为空")
            @Pattern(
                    regexp = UUID_PATTERN,
                    message = "幂等键必须是UUID")
            String idempotencyKey,
            @Valid @RequestBody
            UpdateSubscriptionRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        requireIdempotencyKey(idempotencyKey);
        return ApiResponse.success(
                toResponse(subscriptionService.update(
                        subscriptionId,
                        idempotencyKey,
                        toCommand(request))),
                traceId);
    }

    @PostMapping("/{id}/pause")
    @Operation(summary = "暂停订阅")
    public ApiResponse<SubscriptionDetailResponse> pause(
            @Positive @PathVariable("id")
            long subscriptionId,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false)
            String idempotencyKey,
            @Valid @RequestBody
            SubscriptionVersionRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        requireUuidIdempotencyKey(idempotencyKey);
        return ApiResponse.success(
                toResponse(subscriptionService.pause(
                        subscriptionId,
                        request.version(),
                        idempotencyKey)),
                traceId);
    }

    @PostMapping("/{id}/end")
    @Operation(summary = "结束订阅")
    public ApiResponse<SubscriptionDetailResponse> end(
            @Positive @PathVariable("id")
            long subscriptionId,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false)
            String idempotencyKey,
            @Valid @RequestBody
            SubscriptionVersionRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        requireUuidIdempotencyKey(idempotencyKey);
        return ApiResponse.success(
                toResponse(subscriptionService.end(
                        subscriptionId,
                        request.version(),
                        idempotencyKey)),
                traceId);
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "恢复订阅")
    public ApiResponse<SubscriptionDetailResponse> resume(
            @Positive @PathVariable("id")
            long subscriptionId,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false)
            String idempotencyKey,
            @Valid @RequestBody
            ResumeSubscriptionRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        requireUuidIdempotencyKey(idempotencyKey);
        return ApiResponse.success(
                toResponse(subscriptionService.resume(
                        subscriptionId,
                        idempotencyKey,
                        new ResumeSubscriptionCommand(
                                request.version(),
                                request.nextRenewalDate(),
                                request.renewalReminderEnabled()))),
                traceId);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除订阅")
    public ApiResponse<DeleteSubscriptionResponse> delete(
            @Positive @PathVariable("id")
            long subscriptionId,
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false)
            String idempotencyKey,
            @Positive @RequestParam("version")
            long version,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        requireUuidIdempotencyKey(idempotencyKey);
        DeleteSubscriptionResult result =
                subscriptionService.delete(
                        subscriptionId,
                        version,
                        idempotencyKey);
        return ApiResponse.success(
                new DeleteSubscriptionResponse(
                        Long.toString(result.id()),
                        result.restoreDeadline(),
                        result.restoreToken()),
                traceId);
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "短时恢复订阅")
    public ApiResponse<SubscriptionDetailResponse> restore(
            @Positive @PathVariable("id")
            long subscriptionId,
            @Valid @RequestBody
            RestoreSubscriptionRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        return ApiResponse.success(
                toResponse(subscriptionService.restore(
                        subscriptionId,
                        request.version(),
                        request.restoreToken())),
                traceId);
    }

    private static CreateSubscriptionCommand toCommand(
            CreateSubscriptionRequest request) {
        return new CreateSubscriptionCommand(
                request.name(),
                PositiveLongIdParser.parseNullable(
                        request.categoryId()),
                new BigDecimal(request.amount()),
                request.currency(),
                request.billingCycleType(),
                request.billingCycleValue(),
                parseDecimal(request.cnyReferenceAmount()),
                request.nextRenewalDate(),
                request.autoRenew(),
                request.renewalReminderEnabled(),
                request.remark());
    }

    private static UpdateSubscriptionCommand toCommand(
            UpdateSubscriptionRequest request) {
        return new UpdateSubscriptionCommand(
                request.version(),
                request.name(),
                PositiveLongIdParser.parseNullable(
                        request.categoryId()),
                new BigDecimal(request.amount()),
                request.currency(),
                request.billingCycleType(),
                request.billingCycleValue(),
                parseDecimal(request.cnyReferenceAmount()),
                request.nextRenewalDate(),
                request.autoRenew(),
                request.renewalReminderEnabled(),
                request.remark());
    }

    private static BigDecimal parseDecimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static void requireIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT,
                    "幂等键不能为空");
        }
    }

    private static void requireUuidIdempotencyKey(String key) {
        requireIdempotencyKey(key);
        if (!key.matches(UUID_PATTERN)) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT,
                    "幂等键必须是UUID");
        }
    }

    private SubscriptionDetailResponse toResponse(
            SubscriptionDetail detail) {
        return new SubscriptionDetailResponse(
                Long.toString(detail.id()),
                detail.name(),
                Long.toString(detail.categoryId()),
                detail.categoryName(),
                detail.amount(),
                detail.currency(),
                detail.billingCycleType(),
                detail.billingCycleValue(),
                detail.cnyReferenceAmount(),
                detail.nextRenewalDate(),
                detail.autoRenew(),
                detail.renewalReminderEnabled(),
                detail.status(),
                detail.remark(),
                detail.originalMonthlyCost(),
                detail.originalMonthlyCostDisplay(),
                detail.cnyMonthlyCost(),
                detail.cnyMonthlyCostDisplay(),
                detail.cnyApproximate(),
                detail.includeInCnyTotal(),
                detail.version(),
                detail.createTime(),
                detail.updateTime());
    }

    private SubscriptionSummaryResponse toSummaryResponse(
            SubscriptionSummary summary) {
        return new SubscriptionSummaryResponse(
                Long.toString(summary.id()),
                summary.name(),
                summary.categoryName(),
                summary.amount(),
                summary.currency(),
                summary.originalMonthlyCostDisplay(),
                summary.cnyMonthlyCostDisplay(),
                summary.status(),
                summary.nextRenewalDate(),
                summary.version(),
                summary.createTime());
    }
}
