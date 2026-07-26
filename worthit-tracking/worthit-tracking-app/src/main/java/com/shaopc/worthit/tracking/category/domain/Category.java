package com.shaopc.worthit.tracking.category.domain;

/**
 * 用户分类。
 *
 * @param id 分类标识
 * @param userId 所属用户标识
 * @param name 分类名称
 * @param systemCode 系统分类编码；自定义分类为空
 */
public record Category(
        long id,
        long userId,
        String name,
        String systemCode) {

    /** 系统“未分类”的稳定编码。 */
    public static final String UNCATEGORIZED = "UNCATEGORIZED";

    /**
     * 判断分类是否允许用户删除。
     *
     * @return 自定义分类返回 true，系统分类返回 false
     */
    public boolean deletable() {
        return systemCode == null;
    }
}
