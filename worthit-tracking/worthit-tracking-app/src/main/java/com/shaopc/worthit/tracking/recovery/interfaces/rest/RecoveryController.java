package com.shaopc.worthit.tracking.recovery.interfaces.rest;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.core.pagination.PageResult;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.common.web.response.ApiResponse;
import com.shaopc.worthit.tracking.interfaces.rest.TrackingHeaderNames;
import com.shaopc.worthit.tracking.interfaces.rest.UuidFormat;
import com.shaopc.worthit.tracking.recovery.application.RecoveryResourceSummary;
import com.shaopc.worthit.tracking.recovery.application.RecoveryResult;
import com.shaopc.worthit.tracking.recovery.application.RecoveryService;
import com.shaopc.worthit.tracking.recovery.domain.RecoveryResourceType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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

import java.util.List;

/**
 * Tracking M3 完整恢复公网接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/recovery/resources")
@Tag(name = "完整恢复", description = "已删除数据列表与长期恢复")
@RequiredArgsConstructor
public class RecoveryController {

    private final RecoveryService recoveryService;

    /**
     * 分页查询当前用户已删除资源。
     */
    @GetMapping
    @Operation(summary = "查询已删除资源")
    public ApiResponse<RecoveryPageResponse> list(
            @RequestParam(
                    name = "resourceType",
                    required = false)
            RecoveryResourceType resourceType,
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
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        PageResult<RecoveryResourceSummary> result =
                recoveryService.list(resourceType, page, size);
        List<DeletedRecoveryResourceResponse> items = result
                .getItems()
                .stream()
                .map(RecoveryController::toDeletedResponse)
                .toList();
        return ApiResponse.success(
                new RecoveryPageResponse(
                        items,
                        result.getPage(),
                        result.getSize(),
                        result.getTotal(),
                        result.isHasMore()),
                traceId);
    }

    /**
     * 按删除后版本幂等执行长期恢复。
     */
    @PostMapping("/{resourceType}/{id}/restore")
    @Operation(summary = "长期恢复已删除资源")
    public ApiResponse<FullRestoreResponse> restore(
            @PathVariable("resourceType")
            RecoveryResourceType resourceType,
            @Positive @PathVariable("id")
            long resourceId,
            @RequestHeader(
                    value = TrackingHeaderNames.IDEMPOTENCY_KEY,
                    required = false)
            String idempotencyKey,
            @Valid @RequestBody FullRestoreRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        requireUuidIdempotencyKey(idempotencyKey);
        return ApiResponse.success(
                toRestoreResponse(recoveryService.restore(
                        resourceType,
                        resourceId,
                        request.version(),
                        idempotencyKey)),
                traceId);
    }

    private static DeletedRecoveryResourceResponse
    toDeletedResponse(RecoveryResourceSummary item) {
        return new DeletedRecoveryResourceResponse(
                Long.toString(item.id()),
                item.resourceType(),
                item.name(),
                Long.toString(item.categoryId()),
                item.categoryName(),
                item.categoryAvailable(),
                item.status(),
                item.version(),
                item.deletedAt());
    }

    private static FullRestoreResponse toRestoreResponse(
            RecoveryResult result) {
        return new FullRestoreResponse(
                Long.toString(result.id()),
                result.resourceType(),
                result.name(),
                Long.toString(result.categoryId()),
                result.categoryName(),
                result.status(),
                result.version(),
                result.categoryFallbackApplied());
    }

    private static void requireUuidIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT,
                    "幂等键不能为空");
        }
        if (!UuidFormat.isValid(key)) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT,
                    "幂等键必须是UUID");
        }
    }
}
