package com.shaopc.worthit.tracking.item.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * 短时恢复物品请求。
 *
 * @param version 删除成功后的物品版本
 * @param restoreToken 删除接口返回的恢复令牌
 */
@Schema(description = "短时恢复物品请求")
public record RestoreItemRequest(
        @Positive(message = "版本必须大于0")
        long version,
        @NotBlank(message = "恢复令牌不能为空")
        @Pattern(
                regexp = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-"
                        + "[1-5][0-9a-fA-F]{3}-"
                        + "[89abAB][0-9a-fA-F]{3}-"
                        + "[0-9a-fA-F]{12}",
                message = "恢复令牌格式不正确")
        String restoreToken) {
}
