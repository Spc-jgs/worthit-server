package com.shaopc.worthit.tracking.category.domain;

import java.util.List;
import java.util.Optional;

/**
 * 分类聚合的持久化边界。
 */
public interface CategoryRepository {

    /**
     * 查询用户的全部有效分类。
     *
     * @param userId 用户标识
     * @return 有效分类列表
     */
    List<Category> findAllByUserId(long userId);

    /**
     * 按用户和分类标识查询有效分类。
     *
     * @param categoryId 分类标识
     * @param userId 用户标识
     * @return 分类；不存在、已删除或不属于用户时为空
     */
    Optional<Category> findByIdAndUserId(long categoryId, long userId);

    /**
     * 创建自定义分类。
     *
     * @param userId 用户标识
     * @param name 规范化后的分类名称
     * @return 已创建分类
     */
    Category create(long userId, String name);

    /**
     * 判断分类是否仍被有效业务数据引用。
     *
     * @param categoryId 分类标识
     * @param userId 用户标识
     * @return 有任一有效引用时为 true
     */
    boolean isInUse(long categoryId, long userId);

    /**
     * 逻辑删除用户分类。
     *
     * @param categoryId 分类标识
     * @param userId 用户标识
     */
    void delete(long categoryId, long userId);
}
