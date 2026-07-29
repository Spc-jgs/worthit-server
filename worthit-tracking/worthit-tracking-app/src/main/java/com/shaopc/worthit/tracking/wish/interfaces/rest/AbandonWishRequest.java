package com.shaopc.worthit.tracking.wish.interfaces.rest;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 放弃想买请求。 */
public record AbandonWishRequest(
        @Positive(message = "版本必须大于0")
        long version,
        @Size(max = 256, message = "放弃原因不能超过256个字符")
        String reason) {
}
