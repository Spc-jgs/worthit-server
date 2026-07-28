package com.shaopc.worthit.tracking.item.application;

import java.time.LocalDateTime;

/**
 * 删除物品后提供的短时恢复凭据。
 *
 * @param id 已删除物品标识
 * @param restoreDeadline 恢复截止时间
 * @param restoreToken 服务端生成的恢复令牌
 */
public record DeleteItemResult(
        long id,
        LocalDateTime restoreDeadline,
        String restoreToken) {
}
