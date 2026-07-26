package com.shaopc.worthit.tracking.item.interfaces.rest;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.common.web.response.ApiResponse;
import com.shaopc.worthit.tracking.item.application.CreateItemCommand;
import com.shaopc.worthit.tracking.item.application.ItemDetail;
import com.shaopc.worthit.tracking.item.application.ItemService;
import com.shaopc.worthit.tracking.item.application.ItemSummary;
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
import org.springframework.web.bind.annotation.GetMapping;
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
 * Item 公网接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/items")
@Tag(name = "物品", description = "物品记录与成本查询")
@RequiredArgsConstructor
public class ItemController {

    private static final String UUID_PATTERN =
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                    + "[1-5][0-9a-fA-F]{3}-"
                    + "[89abAB][0-9a-fA-F]{3}-"
                    + "[0-9a-fA-F]{12}";
    private final ItemService itemService;

    /**
     * 幂等新建物品。
     */
    @PostMapping
    @Operation(summary = "新建物品")
    public ApiResponse<ItemDetailResponse> create(
            @RequestHeader(
                    value = "Idempotency-Key",
                    required = false)
            @NotBlank(message = "幂等键不能为空")
            @Pattern(
                    regexp = UUID_PATTERN,
                    message = "幂等键必须是UUID")
            String idempotencyKey,
            @Valid @RequestBody CreateItemRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        if (idempotencyKey == null
                || idempotencyKey.isBlank()) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT,
                    "幂等键不能为空");
        }
        return ApiResponse.success(
                toResponse(itemService.create(
                        idempotencyKey, toCommand(request))),
                traceId);
    }

    /**
     * 查询物品详情。
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询物品详情")
    public ApiResponse<ItemDetailResponse> detail(
            @Positive @PathVariable("id") long itemId,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        return ApiResponse.success(
                toResponse(itemService.detail(itemId)),
                traceId);
    }

    /**
     * 分页查询物品。
     */
    @GetMapping
    @Operation(summary = "分页查询物品")
    public ApiResponse<ItemPageResponse> list(
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
        PageResult<ItemSummary> result = itemService.list(
                page,
                size,
                keyword,
                categoryId == null
                        ? null
                        : Long.valueOf(categoryId));
        List<ItemSummaryResponse> items = result.getItems()
                .stream()
                .map(this::toSummaryResponse)
                .toList();
        return ApiResponse.success(
                new ItemPageResponse(
                        items,
                        result.getPage(),
                        result.getSize(),
                        result.getTotal(),
                        result.isHasMore()),
                traceId);
    }

    private static CreateItemCommand toCommand(
            CreateItemRequest request) {
        return new CreateItemCommand(
                request.name(),
                request.categoryId() == null
                        ? null
                        : Long.valueOf(request.categoryId()),
                new BigDecimal(request.purchasePrice()),
                new BigDecimal(request.expectedYears()),
                request.residualValue() == null
                        ? null
                        : new BigDecimal(request.residualValue()),
                request.purchaseDate(),
                request.warrantyExpireDate(),
                request.warrantyReminderEnabled(),
                request.brandModel(),
                request.remark());
    }

    private ItemDetailResponse toResponse(ItemDetail detail) {
        return new ItemDetailResponse(
                Long.toString(detail.id()),
                detail.name(),
                Long.toString(detail.categoryId()),
                detail.categoryName(),
                detail.purchasePrice(),
                detail.expectedYears(),
                detail.residualValue(),
                detail.residualUnset(),
                detail.purchaseDate(),
                detail.warrantyExpireDate(),
                detail.warrantyReminderEnabled(),
                detail.brandModel(),
                detail.remark(),
                detail.lifecycleStatus(),
                detail.expectedUseDays(),
                detail.planDailyCost(),
                detail.planDailyCostDisplay(),
                detail.planDailyCostTiny(),
                detail.holdingDays(),
                detail.holdingDailyCost(),
                detail.holdingDailyCostDisplay(),
                detail.version(),
                detail.createTime(),
                detail.updateTime());
    }

    private ItemSummaryResponse toSummaryResponse(
            ItemSummary summary) {
        return new ItemSummaryResponse(
                Long.toString(summary.id()),
                summary.name(),
                summary.categoryName(),
                summary.planDailyCostDisplay(),
                summary.residualUnset(),
                summary.lifecycleStatus(),
                summary.createTime());
    }
}
