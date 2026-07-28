package com.shaopc.worthit.tracking.item.application;

/**
 * 删除物品命令。
 *
 * @param itemId 物品标识
 * @param version 客户端读取到的乐观锁版本
 */
public record DeleteItemCommand(
        long itemId,
        long version) {
}
