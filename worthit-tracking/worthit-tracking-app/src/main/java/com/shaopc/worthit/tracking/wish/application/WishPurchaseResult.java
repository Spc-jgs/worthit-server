package com.shaopc.worthit.tracking.wish.application;

import com.shaopc.worthit.tracking.item.application.ItemDetail;

/** 购买想买并转换物品的结果。 */
public record WishPurchaseResult(
        WishDetail wish,
        ItemDetail item) {
}
