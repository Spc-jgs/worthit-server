package com.shaopc.worthit.tracking.item.interfaces.rest;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 删除物品响应。
 *
 * @param id 已删除物品标识
 * @param restoreDeadline 恢复截止时间
 * @param restoreToken 恢复令牌
 */
@Schema(description = "删除物品后的短时恢复凭据")
public record DeleteItemResponse(
        @Schema(description = "物品标识")
        String id,
        @Schema(description = "恢复截止时间")
        LocalDateTime restoreDeadline,
        @Schema(description = "服务端生成的恢复令牌")
        String restoreToken) {
}
