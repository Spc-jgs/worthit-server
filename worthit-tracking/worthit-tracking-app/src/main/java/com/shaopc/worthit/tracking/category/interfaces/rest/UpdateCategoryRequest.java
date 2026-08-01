package com.shaopc.worthit.tracking.category.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 重命名自定义分类请求。
 *
 * @param name 新分类名称
 */
@Schema(description = "重命名自定义分类请求")
public record UpdateCategoryRequest(
        @NotBlank(message = "分类名称不能为空")
        @Size(max = 32, message = "分类名称不能超过32个字符")
        @Schema(description = "分类名称", example = "办公设备", maxLength = 32)
        String name) {
}
