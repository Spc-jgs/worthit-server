package com.shaopc.worthit.tracking.wish.interfaces.rest;

import jakarta.validation.constraints.Positive;

/** 想买状态命令版本请求。 */
public record WishVersionRequest(
        @Positive(message = "版本必须大于0")
        long version) {
}
