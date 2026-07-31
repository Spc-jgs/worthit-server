package com.shaopc.worthit.tracking.lifecycle.interfaces.rest;

import com.shaopc.worthit.tracking.interfaces.rest.PositiveLongIdParser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 建立替换关系请求。
 */
public record ReplaceItemRequest(
        @NotBlank(message = "新物品标识不能为空")
        @Pattern(
                regexp = PositiveLongIdParser.PATTERN,
                message = "新物品标识必须是正整数")
        String newItemId) {
}
