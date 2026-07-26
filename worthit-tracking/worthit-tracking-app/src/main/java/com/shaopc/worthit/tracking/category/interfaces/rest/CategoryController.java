package com.shaopc.worthit.tracking.category.interfaces.rest;

import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.web.response.ApiResponse;
import com.shaopc.worthit.tracking.category.application.CategoryService;
import com.shaopc.worthit.tracking.category.domain.Category;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 分类公网接口。
 */
@Validated
@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "分类", description = "用户自定义分类管理")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /**
     * 查询当前用户分类。
     */
    @GetMapping
    @Operation(summary = "查询分类列表")
    public ApiResponse<List<CategoryResponse>> list(
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        List<CategoryResponse> categories = categoryService.list()
                .stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.success(categories, traceId);
    }

    /**
     * 新建当前用户的自定义分类。
     */
    @PostMapping
    @Operation(summary = "新建自定义分类")
    public ApiResponse<CategoryResponse> create(
            @Valid @RequestBody CreateCategoryRequest request,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        return ApiResponse.success(
                toResponse(categoryService.create(request.name())),
                traceId);
    }

    /**
     * 删除当前用户未被引用的自定义分类。
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "删除自定义分类")
    public ApiResponse<Void> delete(
            @Positive @PathVariable("id") long categoryId,
            @RequestAttribute(SecurityHeaderNames.TRACE_ID)
            String traceId) {
        categoryService.delete(categoryId);
        return ApiResponse.success(null, traceId);
    }

    private CategoryResponse toResponse(Category category) {
        return new CategoryResponse(
                Long.toString(category.id()),
                category.name(),
                category.systemCode(),
                category.deletable());
    }
}
