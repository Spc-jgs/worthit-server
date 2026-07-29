package com.shaopc.worthit.tracking.wish.interfaces.rest;

import com.shaopc.worthit.tracking.item.interfaces.rest.ItemDetailResponse;

/** 想买购买转物品响应。 */
public record WishPurchaseResponse(
        WishDetailResponse wish,
        ItemDetailResponse item) {
}
