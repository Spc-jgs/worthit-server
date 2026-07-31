package com.shaopc.worthit.tracking.lifecycle.interfaces.rest;

import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.response.ApiResponse;
import com.shaopc.worthit.tracking.lifecycle.application.LifecycleDisposalReview;
import com.shaopc.worthit.tracking.lifecycle.application.LifecycleItemBrief;
import com.shaopc.worthit.tracking.lifecycle.application.LifecycleReplacementReview;
import com.shaopc.worthit.tracking.lifecycle.application.LifecycleReviewEntry;
import com.shaopc.worthit.tracking.lifecycle.application.ItemLifecycleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 生命周期复盘公网读接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/lifecycle")
@Tag(name = "生命周期复盘", description = "处置与替换事实联合复盘")
@RequiredArgsConstructor
public class LifecycleReviewController {

    private final ItemLifecycleService lifecycleService;

    /**
     * 稳定倒序分页查询生命周期复盘。
     */
    @GetMapping("/review")
    @Operation(summary = "查询生命周期复盘")
    public ApiResponse<LifecycleReviewPageResponse> review(
            @RequestParam(name = "page", defaultValue = "1")
            @Min(value = 1, message = "页码不能小于1")
            int page,
            @RequestParam(name = "size", defaultValue = "20")
            @Min(value = 1, message = "每页条数不能小于1")
            @Max(value = 50, message = "每页条数不能大于50")
            int size,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        PageResult<LifecycleReviewEntry> result =
                lifecycleService.review(page, size);
        return ApiResponse.success(
                new LifecycleReviewPageResponse(
                        result.getItems()
                                .stream()
                                .map(LifecycleReviewController::toResponse)
                                .toList(),
                        result.getPage(),
                        result.getSize(),
                        result.getTotal(),
                        result.isHasMore()),
                traceId);
    }

    private static LifecycleReviewEntryResponse toResponse(
            LifecycleReviewEntry entry) {
        return new LifecycleReviewEntryResponse(
                Long.toString(entry.id()),
                entry.entryType().name(),
                entry.eventDate(),
                entry.createTime(),
                toResponse(entry.disposal()),
                toResponse(entry.replacement()));
    }

    private static LifecycleDisposalReviewResponse toResponse(
            LifecycleDisposalReview disposal) {
        if (disposal == null) {
            return null;
        }
        return new LifecycleDisposalReviewResponse(
                toResponse(disposal.item()),
                disposal.type(),
                disposal.date(),
                disposal.saleAmount(),
                disposal.netCost());
    }

    private static LifecycleReplacementReviewResponse toResponse(
            LifecycleReplacementReview replacement) {
        if (replacement == null) {
            return null;
        }
        return new LifecycleReplacementReviewResponse(
                toResponse(replacement.oldItem()),
                toResponse(replacement.newItem()));
    }

    private static LifecycleItemBriefResponse toResponse(
            LifecycleItemBrief item) {
        return new LifecycleItemBriefResponse(
                Long.toString(item.id()),
                item.name());
    }
}
