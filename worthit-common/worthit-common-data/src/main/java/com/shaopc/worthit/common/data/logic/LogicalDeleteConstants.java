package com.shaopc.worthit.common.data.logic;

/**
 * 定义数据库逻辑删除字段使用的稳定值。
 */
public final class LogicalDeleteConstants {

    /**
     * 数据有效。
     */
    public static final int ACTIVE = 0;

    /**
     * 数据已逻辑删除。
     */
    public static final int DELETED = 1;

    private LogicalDeleteConstants() {
    }
}
