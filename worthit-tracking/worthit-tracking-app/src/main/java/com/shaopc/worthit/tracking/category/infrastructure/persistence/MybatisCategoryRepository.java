package com.shaopc.worthit.tracking.category.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shaopc.worthit.tracking.category.domain.Category;
import com.shaopc.worthit.tracking.category.domain.CategoryRepository;
import com.shaopc.worthit.tracking.category.domain.CategorySystemCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 使用 MyBatis-Plus 持久化用户分类。
 */
@Repository
@RequiredArgsConstructor
public class MybatisCategoryRepository implements CategoryRepository {

    private final CategoryMapper categoryMapper;
    private final Clock trackingClock;

    @Override
    public List<Category> findAllByUserId(long userId) {
        return categoryMapper.selectList(
                        Wrappers.<CategoryDO>lambdaQuery()
                                .eq(CategoryDO::getUserId, userId)
                                .eq(CategoryDO::getDelFlag, false)
                                .orderByAsc(CategoryDO::getCreateTime)
                                .orderByAsc(CategoryDO::getId))
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<Category> findByIdAndUserId(
            long categoryId, long userId) {
        CategoryDO category = categoryMapper.selectOne(
                Wrappers.<CategoryDO>lambdaQuery()
                        .eq(CategoryDO::getId, categoryId)
                        .eq(CategoryDO::getUserId, userId)
                        .eq(CategoryDO::getDelFlag, false));
        return Optional.ofNullable(category).map(this::toDomain);
    }

    @Override
    public Optional<Category> findByIdAndUserIdForUpdate(
            long categoryId, long userId) {
        return Optional.ofNullable(
                        categoryMapper.selectByIdAndUserIdForUpdate(
                                categoryId, userId))
                .map(this::toDomain);
    }

    @Override
    public Optional<Category> findCustomByIdAndUserIdForUpdate(
            long categoryId, long userId) {
        return Optional.ofNullable(
                        categoryMapper
                                .selectCustomByIdAndUserIdForUpdate(
                                        categoryId, userId))
                .map(this::toDomain);
    }

    @Override
    public Category create(long userId, String name) {
        return create(userId, name, null);
    }

    @Override
    public Category getOrCreateUncategorized(long userId) {
        Optional<Category> existing =
                findBySystemCode(
                        userId, CategorySystemCode.UNCATEGORIZED);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        try {
            return create(
                    userId,
                    "未分类",
                    CategorySystemCode.UNCATEGORIZED);
        } catch (DuplicateKeyException exception) {
            return findBySystemCode(
                            userId, CategorySystemCode.UNCATEGORIZED)
                    .orElseThrow(() -> exception);
        }
    }

    private Category create(
            long userId,
            String name,
            CategorySystemCode systemCode) {
        LocalDateTime now = LocalDateTime.now(trackingClock);
        CategoryDO category = new CategoryDO();
        category.setUserId(userId);
        category.setName(name);
        category.setSystemCode(systemCode == null
                ? null : systemCode.code());
        category.setVersion(1L);
        category.setCreateBy(userId);
        category.setCreateTime(now);
        category.setUpdateBy(userId);
        category.setUpdateTime(now);
        category.setDelFlag(false);
        categoryMapper.insert(category);
        return toDomain(category);
    }

    private Optional<Category> findBySystemCode(
            long userId, CategorySystemCode systemCode) {
        CategoryDO category = categoryMapper.selectOne(
                Wrappers.<CategoryDO>lambdaQuery()
                        .eq(CategoryDO::getUserId, userId)
                        .eq(CategoryDO::getSystemCode,
                                systemCode.code())
                        .eq(CategoryDO::getDelFlag, false));
        return Optional.ofNullable(category).map(this::toDomain);
    }

    @Override
    public boolean isInUse(
            long categoryId,
            long userId,
            LocalDateTime earliestRestorableDeletion) {
        return categoryMapper.existsReferenceWithinRestoreWindow(
                categoryId, userId, earliestRestorableDeletion);
    }

    @Override
    public void delete(long categoryId, long userId) {
        LocalDateTime now = LocalDateTime.now(trackingClock);
        CategoryDO changes = new CategoryDO();
        changes.setDelFlag(true);
        changes.setDeleteTime(now);
        changes.setUpdateBy(userId);
        changes.setUpdateTime(now);
        categoryMapper.update(
                changes,
                Wrappers.<CategoryDO>lambdaUpdate()
                        .eq(CategoryDO::getId, categoryId)
                        .eq(CategoryDO::getUserId, userId)
                        .eq(CategoryDO::getDelFlag, false)
                        .setSql("version = version + 1"));
    }

    private Category toDomain(CategoryDO category) {
        return new Category(
                category.getId(),
                category.getUserId(),
                category.getName(),
                category.getSystemCode() == null
                        ? null
                        : CategorySystemCode.fromCode(
                                category.getSystemCode()));
    }
}
