package com.shaopc.worthit.tracking.item.domain;

/**
 * 带分类名称的物品查询结果。
 *
 * @param item 物品事实
 * @param categoryName 分类名称
 */
public record ItemWithCategory(
        Item item,
        String categoryName) {
}
