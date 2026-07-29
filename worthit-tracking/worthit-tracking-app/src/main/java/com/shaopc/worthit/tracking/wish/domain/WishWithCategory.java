package com.shaopc.worthit.tracking.wish.domain;

/**
 * 想买事实及其分类展示名。
 */
public record WishWithCategory(
        Wish wish,
        String categoryName) {
}
