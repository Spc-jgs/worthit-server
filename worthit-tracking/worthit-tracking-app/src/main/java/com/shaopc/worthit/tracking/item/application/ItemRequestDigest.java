package com.shaopc.worthit.tracking.item.application;

/**
 * 生成 Item 请求的稳定摘要。
 */
public interface ItemRequestDigest {

    /**
     * 生成十六进制 SHA-256 摘要。
     */
    String hash(CreateItemCommand command);

    /**
     * 生成更新物品命令摘要。
     */
    String hash(UpdateItemCommand command);

    /**
     * 生成删除物品命令摘要。
     */
    String hash(DeleteItemCommand command);
}
