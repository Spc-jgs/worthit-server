package com.shaopc.worthit.tracking.category.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.tracking.category.domain.Category;
import com.shaopc.worthit.tracking.category.domain.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 解析业务对象的目标分类，并为可删除分类建立事务级引用保留。
 */
@Component
@RequiredArgsConstructor
public class CategoryReferenceResolver {

    private final CategoryRepository categoryRepository;

    /**
     * 解析当前用户的目标分类。
     *
     * <p>自定义分类使用行锁与分类删除串行化；不可删除的系统分类
     * 只验证有效性，避免成为同一用户全部默认写入的热点。</p>
     *
     * @param categoryId 分类标识；为空时使用系统“未分类”
     * @param userId 用户标识
     * @return 有效目标分类
     */
    public Category resolve(Long categoryId, long userId) {
        if (categoryId == null) {
            return categoryRepository
                    .getOrCreateUncategorized(userId);
        }
        Category observed = categoryRepository
                .findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new BusinessException(
                        CommonWebErrorCode.RES_NOT_FOUND));
        if (!observed.deletable()) {
            return observed;
        }
        return categoryRepository
                .findCustomByIdAndUserIdForUpdate(
                        categoryId, userId)
                .orElseThrow(() -> new BusinessException(
                        CommonWebErrorCode.RES_NOT_FOUND));
    }
}
