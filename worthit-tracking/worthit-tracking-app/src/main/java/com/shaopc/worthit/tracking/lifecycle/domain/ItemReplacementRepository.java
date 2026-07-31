package com.shaopc.worthit.tracking.lifecycle.domain;

/**
 * 物品替换关系持久化边界。
 */
public interface ItemReplacementRepository {

    /**
     * 保存不可变替换关系。
     */
    ItemReplacement save(ItemReplacement replacement);
}
