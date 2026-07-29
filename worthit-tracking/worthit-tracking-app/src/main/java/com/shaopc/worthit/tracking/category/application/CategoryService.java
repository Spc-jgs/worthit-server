package com.shaopc.worthit.tracking.category.application;

import com.shaopc.worthit.tracking.category.domain.Category;

import java.util.List;

/**
 * 分类公开应用用例。
 */
public interface CategoryService {

    /**
     * 查询当前用户的有效分类。
     */
    List<Category> list();

    /**
     * 为当前用户创建自定义分类。
     */
    Category create(String name);

    /**
     * 删除当前用户未被引用的自定义分类。
     */
    void delete(long categoryId);
}
