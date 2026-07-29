package com.shaopc.worthit.tracking.wish.interfaces.rest;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.common.web.response.ApiResponse;
import com.shaopc.worthit.tracking.interfaces.rest.PositiveLongIdParser;
import com.shaopc.worthit.tracking.interfaces.rest.TrackingHeaderNames;
import com.shaopc.worthit.tracking.interfaces.rest.UuidFormat;
import com.shaopc.worthit.tracking.item.application.ItemDetail;
import com.shaopc.worthit.tracking.item.interfaces.rest.ItemDetailResponse;
import com.shaopc.worthit.tracking.wish.application.CreateWishCommand;
import com.shaopc.worthit.tracking.wish.application.DeleteWishResult;
import com.shaopc.worthit.tracking.wish.application.UpdateWishCommand;
import com.shaopc.worthit.tracking.wish.application.WishDetail;
import com.shaopc.worthit.tracking.wish.application.WishPurchaseResult;
import com.shaopc.worthit.tracking.wish.application.WishService;
import com.shaopc.worthit.tracking.wish.application.WishSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
 * Wish 公网接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/wishes")
@Tag(name = "想买", description = "想买决策与购买转物品")
@RequiredArgsConstructor
public class WishController {

    private final WishService wishService;

    @PostMapping
    @Operation(summary = "新建想买")
    public ApiResponse<WishDetailResponse> create(
            @RequestHeader(
                    value = TrackingHeaderNames.IDEMPOTENCY_KEY,
                    required = false)
            @Pattern(
                    regexp = UuidFormat.PATTERN,
                    message = "幂等键必须是UUID")
            String idempotencyKey,
            @Valid @RequestBody CreateWishRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        requireUuidKey(idempotencyKey);
        return ApiResponse.success(
                toResponse(wishService.create(
                        idempotencyKey,
                        new CreateWishCommand(
                                request.name(),
                                PositiveLongIdParser.parseNullable(
                                        request.categoryId()),
                                new BigDecimal(
                                        request.expectedPrice()),
                                new BigDecimal(
                                        request.expectedYears()),
                                decimal(request.residualValue()),
                                request.reason(), request.remark(),
                                request.watchDeadline(),
                                request.watchReminderEnabled()))),
                traceId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询想买详情")
    public ApiResponse<WishDetailResponse> detail(
            @Positive @PathVariable("id") long wishId,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        return ApiResponse.success(
                toResponse(wishService.detail(wishId)), traceId);
    }

    @GetMapping
    @Operation(summary = "分页查询想买")
    public ApiResponse<WishPageResponse> list(
            @RequestParam(
                    name = "page", defaultValue = "1")
            @Min(value = 1, message = "页码不能小于1")
            int page,
            @RequestParam(
                    name = "size", defaultValue = "20")
            @Min(value = 1, message = "每页条数不能小于1")
            @Max(value = 50, message = "每页条数不能大于50")
            int size,
            @RequestParam(
                    name = "keyword", required = false)
            String keyword,
            @RequestParam(
                    name = "categoryId", required = false)
            @Pattern(
                    regexp = PositiveLongIdParser.PATTERN,
                    message = "分类标识格式不正确")
            String categoryId,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        PageResult<WishSummary> result = wishService.list(
                page, size, keyword,
                PositiveLongIdParser.parseNullable(categoryId));
        List<WishSummaryResponse> items = result.getItems()
                .stream().map(this::toSummaryResponse).toList();
        return ApiResponse.success(
                new WishPageResponse(
                        items, result.getPage(), result.getSize(),
                        result.getTotal(), result.isHasMore()),
                traceId);
    }

    @PatchMapping("/{id}")
    @Operation(summary = "更新想买")
    public ApiResponse<WishDetailResponse> update(
            @Positive @PathVariable("id") long wishId,
            @RequestHeader(
                    value = TrackingHeaderNames.IDEMPOTENCY_KEY,
                    required = false)
            String idempotencyKey,
            @Valid @RequestBody UpdateWishRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        requireUuidKey(idempotencyKey);
        return ApiResponse.success(
                toResponse(wishService.update(
                        wishId, idempotencyKey,
                        new UpdateWishCommand(
                                request.version(), request.name(),
                                PositiveLongIdParser.parseNullable(
                                        request.categoryId()),
                                new BigDecimal(
                                        request.expectedPrice()),
                                new BigDecimal(
                                        request.expectedYears()),
                                decimal(request.residualValue()),
                                request.reason(), request.remark(),
                                request.watchDeadline(),
                                request.watchReminderEnabled()))),
                traceId);
    }

    @PostMapping("/{id}/purchase")
    @Operation(summary = "购买并转换为物品")
    public ApiResponse<WishPurchaseResponse> purchase(
            @Positive @PathVariable("id") long wishId,
            @RequestHeader(
                    value = TrackingHeaderNames.IDEMPOTENCY_KEY,
                    required = false)
            String idempotencyKey,
            @Valid @RequestBody WishVersionRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        requireUuidKey(idempotencyKey);
        WishPurchaseResult result = wishService.purchase(
                wishId, request.version(), idempotencyKey);
        return ApiResponse.success(
                new WishPurchaseResponse(
                        toResponse(result.wish()),
                        toItemResponse(result.item())),
                traceId);
    }

    @PostMapping("/{id}/abandon")
    @Operation(summary = "放弃想买")
    public ApiResponse<WishDetailResponse> abandon(
            @Positive @PathVariable("id") long wishId,
            @RequestHeader(
                    value = TrackingHeaderNames.IDEMPOTENCY_KEY,
                    required = false)
            String idempotencyKey,
            @Valid @RequestBody AbandonWishRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        requireUuidKey(idempotencyKey);
        return ApiResponse.success(
                toResponse(wishService.abandon(
                        wishId, request.version(),
                        request.reason(), idempotencyKey)),
                traceId);
    }

    @PostMapping("/{id}/reconsider")
    @Operation(summary = "重新考虑想买")
    public ApiResponse<WishDetailResponse> reconsider(
            @Positive @PathVariable("id") long wishId,
            @RequestHeader(
                    value = TrackingHeaderNames.IDEMPOTENCY_KEY,
                    required = false)
            String idempotencyKey,
            @Valid @RequestBody WishVersionRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        requireUuidKey(idempotencyKey);
        return ApiResponse.success(
                toResponse(wishService.reconsider(
                        wishId, request.version(),
                        idempotencyKey)),
                traceId);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除想买")
    public ApiResponse<DeleteWishResponse> delete(
            @Positive @PathVariable("id") long wishId,
            @RequestHeader(
                    value = TrackingHeaderNames.IDEMPOTENCY_KEY,
                    required = false)
            String idempotencyKey,
            @Positive @RequestParam("version") long version,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        requireUuidKey(idempotencyKey);
        DeleteWishResult result = wishService.delete(
                wishId, version, idempotencyKey);
        return ApiResponse.success(
                new DeleteWishResponse(
                        Long.toString(result.id()),
                        result.restoreDeadline(),
                        result.restoreToken()),
                traceId);
    }

    @PostMapping("/{id}/restore")
    @Operation(summary = "短时恢复想买")
    public ApiResponse<WishDetailResponse> restore(
            @Positive @PathVariable("id") long wishId,
            @Valid @RequestBody RestoreWishRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        return ApiResponse.success(
                toResponse(wishService.restore(
                        wishId, request.version(),
                        request.restoreToken())),
                traceId);
    }

    private static BigDecimal decimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static void requireUuidKey(String key) {
        if (!UuidFormat.isValid(key)) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT,
                    "幂等键必须是UUID");
        }
    }

    private WishDetailResponse toResponse(WishDetail detail) {
        return new WishDetailResponse(
                Long.toString(detail.id()), detail.name(),
                Long.toString(detail.categoryId()),
                detail.categoryName(), detail.expectedPrice(),
                detail.expectedYears(), detail.residualValue(),
                detail.residualUnset(), detail.reason(),
                detail.remark(), detail.watchDeadline(),
                detail.watchReminderEnabled(), detail.status(),
                detail.lastAbandonReason(),
                detail.lastAbandonAt(),
                detail.convertedItemId() == null
                        ? null
                        : Long.toString(
                                detail.convertedItemId()),
                detail.expectedUseDays(),
                detail.planDailyCost(),
                detail.planDailyCostDisplay(),
                detail.planDailyCostTiny(), detail.version(),
                detail.createTime(), detail.updateTime());
    }

    private WishSummaryResponse toSummaryResponse(
            WishSummary summary) {
        return new WishSummaryResponse(
                Long.toString(summary.id()), summary.name(),
                summary.categoryName(), summary.expectedPrice(),
                summary.planDailyCostDisplay(),
                summary.residualUnset(),
                summary.watchDeadline(), summary.status(),
                summary.version(), summary.createTime());
    }

    private ItemDetailResponse toItemResponse(ItemDetail detail) {
        return new ItemDetailResponse(
                Long.toString(detail.id()), detail.name(),
                Long.toString(detail.categoryId()),
                detail.categoryName(), detail.purchasePrice(),
                detail.expectedYears(), detail.residualValue(),
                detail.residualUnset(), detail.purchaseDate(),
                detail.warrantyExpireDate(),
                detail.warrantyReminderEnabled(),
                detail.brandModel(), detail.remark(),
                detail.lifecycleStatus(),
                detail.expectedUseDays(),
                detail.planDailyCost(),
                detail.planDailyCostDisplay(),
                detail.planDailyCostTiny(),
                detail.holdingDays(),
                detail.holdingDailyCost(),
                detail.holdingDailyCostDisplay(),
                detail.version(), detail.createTime(),
                detail.updateTime());
    }
}
