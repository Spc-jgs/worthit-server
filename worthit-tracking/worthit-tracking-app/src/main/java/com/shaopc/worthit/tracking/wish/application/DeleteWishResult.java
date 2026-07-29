package com.shaopc.worthit.tracking.wish.application;

import java.time.LocalDateTime;

/** 删除想买后的短时恢复凭据。 */
public record DeleteWishResult(
        long id,
        LocalDateTime restoreDeadline,
        String restoreToken) {
}
