package com.shaopc.worthit.tracking.category.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 分类公网响应。
 *
 * @param id 分类标识，使用字符串避免前端精度丢失
 * @param name 分类名称
 * @param systemCode 系统分类编码；自定义分类为空
 * @param deletable 是否允许删除
 */
@Schema(description = "分类")
public record CategoryResponse(
        @Schema(description = "分类标识", example = "1938")
        String id,
        @Schema(description = "分类名称", example = "未分类")
        String name,
        @Schema(
                description = "系统分类编码；自定义分类为空",
                example = "UNCATEGORIZED",
                nullable = true)
        String systemCode,
        @Schema(description = "是否允许删除", example = "false")
        boolean deletable) {
}
