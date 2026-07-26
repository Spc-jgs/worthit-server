package com.shaopc.worthit.tracking.category.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.tracking.category.domain.Category;
import com.shaopc.worthit.tracking.category.domain.CategoryErrorCode;
import com.shaopc.worthit.tracking.category.domain.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 编排分类查询、新建与删除用例。
 */
@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final CurrentUserProvider currentUserProvider;

    /**
     * 查询当前用户的有效分类。
     *
     * @return 分类列表
     */
    @Transactional(readOnly = true)
    public List<Category> list() {
        return categoryRepository.findAllByUserId(currentUserId());
    }

    /**
     * 为当前用户创建自定义分类。
     *
     * @param name 分类名称
     * @return 已创建分类
     */
    @Transactional
    public Category create(String name) {
        String normalizedName = name.trim();
        try {
            return categoryRepository.create(
                    currentUserId(), normalizedName);
        } catch (DuplicateKeyException exception) {
            throw new BusinessException(
                    CategoryErrorCode.BIZ_CONFLICT,
                    CategoryErrorCode.BIZ_CONFLICT.defaultMessage(),
                    exception);
        }
    }

    /**
     * 删除当前用户未被引用的自定义分类。
     *
     * @param categoryId 分类标识
     */
    @Transactional
    public void delete(long categoryId) {
        long userId = currentUserId();
        Category category = categoryRepository
                .findByIdAndUserId(categoryId, userId)
                .orElseThrow(() -> new BusinessException(
                        CommonWebErrorCode.RES_NOT_FOUND));
        if (!category.deletable()) {
            throw new BusinessException(
                    CategoryErrorCode.BIZ_CATEGORY_SYSTEM_PROTECTED);
        }
        if (categoryRepository.isInUse(categoryId, userId)) {
            throw new BusinessException(
                    CategoryErrorCode.BIZ_CATEGORY_IN_USE);
        }
        categoryRepository.delete(categoryId, userId);
    }

    private long currentUserId() {
        return currentUserProvider.currentUser().userId();
    }
}
