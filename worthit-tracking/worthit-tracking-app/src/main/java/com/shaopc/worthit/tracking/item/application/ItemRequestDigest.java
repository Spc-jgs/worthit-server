package com.shaopc.worthit.tracking.item.application;

/**
 * 生成 Item 请求的稳定摘要。
 */
@FunctionalInterface
public interface ItemRequestDigest {

    /**
     * 生成十六进制 SHA-256 摘要。
     */
    String hash(CreateItemCommand command);
}
