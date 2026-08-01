package com.shaopc.worthit.tracking.recovery.application;

import com.shaopc.worthit.tracking.category.domain.Category;
import com.shaopc.worthit.tracking.category.domain.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 在完整恢复时保留有效原分类，或回落到系统“未分类”。
 */
@Component
@RequiredArgsConstructor
public class RecoveryCategoryResolver {

    private final CategoryRepository categoryRepository;

    /**
     * 按分类删除并发协议解析恢复目标分类。
     */
    public RecoveryCategoryResolution resolve(
            long originalCategoryId,
            long userId) {
        Category observed = categoryRepository
                .findByIdAndUserId(originalCategoryId, userId)
                .orElse(null);
        if (observed != null && !observed.deletable()) {
            return new RecoveryCategoryResolution(
                    observed, false);
        }
        if (observed != null) {
            Category locked = categoryRepository
                    .findCustomByIdAndUserIdForUpdate(
                            originalCategoryId, userId)
                    .orElse(null);
            if (locked != null) {
                return new RecoveryCategoryResolution(
                        locked, false);
            }
        }
        return new RecoveryCategoryResolution(
                categoryRepository
                        .getOrCreateUncategorized(userId),
                true);
    }
}
