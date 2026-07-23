package com.shaopc.worthit.common.web.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 描述单个请求字段的校验问题。
 *
 * @param field 违反约束的字段名
 * @param issue 中文问题描述
 */
@Schema(description = "请求字段校验详情")
public record FieldViolation(
        @Schema(description = "违反约束的字段名") String field,
        @Schema(description = "中文问题描述") String issue) {

    /**
     * 校验字段名和问题描述均不为空。
     */
    public FieldViolation {
        field = requireText(field, "字段名");
        issue = requireText(issue, "校验问题");
    }

    /**
     * 校验字段校验信息不为空。
     */
    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value;
    }
}
