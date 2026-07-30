package com.shaopc.worthit.tracking.lifecycle.interfaces.rest;

import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.response.ApiResponse;
import com.shaopc.worthit.tracking.interfaces.rest.TrackingHeaderNames;
import com.shaopc.worthit.tracking.interfaces.rest.UuidFormat;
import com.shaopc.worthit.tracking.lifecycle.application.ItemDisposalDetail;
import com.shaopc.worthit.tracking.lifecycle.application.ItemLifecycleResult;
import com.shaopc.worthit.tracking.lifecycle.application.ItemLifecycleService;
import com.shaopc.worthit.tracking.lifecycle.application.ReturnItemCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 物品生命周期公网写接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/items")
@Tag(name = "物品生命周期", description = "退货、卖出与报废")
@RequiredArgsConstructor
public class ItemLifecycleController {

    private final ItemLifecycleService lifecycleService;

    /**
     * 退货处置。
     */
    @PostMapping("/{id}/return")
    @Operation(summary = "退货")
    public ApiResponse<ItemLifecycleResponse> returnItem(
            @Positive @PathVariable("id") long itemId,
            @RequestHeader(
                    value = TrackingHeaderNames.IDEMPOTENCY_KEY,
                    required = false)
            @NotBlank(message = "幂等键不能为空")
            @Pattern(
                    regexp = UuidFormat.PATTERN,
                    message = "幂等键必须是UUID")
            String idempotencyKey,
            @Valid @RequestBody ReturnItemRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        ItemLifecycleResult result =
                lifecycleService.returnItem(
                        itemId,
                        idempotencyKey,
                        new ReturnItemCommand(
                                request.version(),
                                request.returnDate(),
                                request.remark()));
        return ApiResponse.success(toResponse(result), traceId);
    }

    private static ItemLifecycleResponse toResponse(
            ItemLifecycleResult result) {
        return new ItemLifecycleResponse(
                Long.toString(result.itemId()),
                result.lifecycleStatus(),
                toResponse(result.disposal()),
                result.version(),
                result.updateTime());
    }

    private static ItemDisposalResponse toResponse(
            ItemDisposalDetail disposal) {
        return new ItemDisposalResponse(
                disposal.type(),
                disposal.date(),
                disposal.saleAmount(),
                disposal.remark(),
                disposal.netCost());
    }
}
