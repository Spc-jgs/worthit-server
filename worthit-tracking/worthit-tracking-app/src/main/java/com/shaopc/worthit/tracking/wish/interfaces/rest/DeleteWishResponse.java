package com.shaopc.worthit.tracking.wish.interfaces.rest;

import java.time.LocalDateTime;

/** 删除想买响应。 */
public record DeleteWishResponse(
        String id,
        LocalDateTime restoreDeadline,
        String restoreToken) {
}
