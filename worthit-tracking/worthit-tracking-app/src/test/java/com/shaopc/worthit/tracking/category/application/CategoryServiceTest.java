package com.shaopc.worthit.tracking.category.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.tracking.category.domain.Category;
import com.shaopc.worthit.tracking.category.domain.CategoryErrorCode;
import com.shaopc.worthit.tracking.category.domain.CategoryRepository;
import com.shaopc.worthit.tracking.category.domain.CategorySystemCode;
import com.shaopc.worthit.tracking.restore.application.RestoreWindowPolicy;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CategoryServiceTest {

    private static final long USER_ID = 1001L;

    private InMemoryCategoryRepository repository;
    private CategoryService service;

    @BeforeEach
    void setUp() {
        repository = new InMemoryCategoryRepository();
        service = new CategoryServiceImpl(
                repository,
                () -> new UserContext(USER_ID),
                new RestoreWindowPolicy(),
                Clock.fixed(
                        Instant.parse("2026-07-29T04:00:00Z"),
                        ZoneOffset.UTC));
    }

    @Test
    void listsOnlyCurrentUsersActiveCategories() {
        repository.categories.addAll(List.of(
                new Category(
                        1L, USER_ID, "未分类",
                        CategorySystemCode.UNCATEGORIZED),
                new Category(2L, USER_ID, "数码", null),
                new Category(3L, 2002L, "他人分类", null)));

        assertThat(service.list())
                .extracting(Category::id)
                .containsExactly(1L, 2L);
    }

    @Test
    void createsTrimmedCustomCategoryForCurrentUser() {
        Category created = service.create("  数码  ");

        assertThat(created.userId()).isEqualTo(USER_ID);
        assertThat(created.name()).isEqualTo("数码");
        assertThat(created.systemCode()).isNull();
    }

    @Test
    void mapsDuplicateActiveNameToBusinessConflict() {
        service.create("数码");

        assertThatThrownBy(() -> service.create("数码"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("BIZ_CONFLICT"));
    }

    @Test
    void renamesCustomCategoryWithTrimmedName() {
        repository.categories.add(
                new Category(2L, USER_ID, "数码", null));

        Category renamed = service.rename(2L, "  办公设备  ");

        assertThat(renamed.name()).isEqualTo("办公设备");
        assertThat(repository.lockedCategoryId).isEqualTo(2L);
    }

    @Test
    void rejectsRenamingSystemCategory() {
        repository.categories.add(
                new Category(
                        1L, USER_ID, "未分类",
                        CategorySystemCode.UNCATEGORIZED));

        assertThatThrownBy(() -> service.rename(1L, "其他"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(CategoryErrorCode
                                        .BIZ_CATEGORY_SYSTEM_PROTECTED));
    }

    @Test
    void hidesOtherUsersCategoryWhenRenaming() {
        repository.categories.add(
                new Category(3L, 2002L, "他人分类", null));

        assertThatThrownBy(() -> service.rename(3L, "其他"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(CommonWebErrorCode.RES_NOT_FOUND));
    }

    @Test
    void mapsDuplicateRenameToBusinessConflict() {
        repository.categories.addAll(List.of(
                new Category(2L, USER_ID, "数码", null),
                new Category(3L, USER_ID, "办公", null)));

        assertThatThrownBy(() -> service.rename(2L, "办公"))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("BIZ_CONFLICT"));
    }

    @Test
    void rejectsDeletingSystemCategory() {
        repository.categories.add(
                new Category(
                        1L, USER_ID, "未分类",
                        CategorySystemCode.UNCATEGORIZED));

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(CategoryErrorCode
                                        .BIZ_CATEGORY_SYSTEM_PROTECTED));
    }

    @Test
    void rejectsDeletingCategoryReferencedByBusinessData() {
        repository.categories.add(
                new Category(2L, USER_ID, "数码", null));
        repository.inUse = true;

        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(CategoryErrorCode
                                        .BIZ_CATEGORY_IN_USE));
    }

    @Test
    void hidesOtherUsersCategoryAsNotFound() {
        repository.categories.add(
                new Category(3L, 2002L, "他人分类", null));

        assertThatThrownBy(() -> service.delete(3L))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(CommonWebErrorCode.RES_NOT_FOUND));
    }

    @Test
    void deletesUnusedCustomCategory() {
        repository.categories.add(
                new Category(2L, USER_ID, "数码", null));

        service.delete(2L);

        assertThat(repository.findByIdAndUserId(2L, USER_ID)).isEmpty();
        assertThat(repository.lockedCategoryId).isEqualTo(2L);
        assertThat(repository.earliestRestorableDeletion)
                .isEqualTo(LocalDateTime.of(
                        2026, 7, 29, 3, 59));
    }

    private static final class InMemoryCategoryRepository
            implements CategoryRepository {

        private final List<Category> categories = new ArrayList<>();
        private long nextId = 10L;
        private boolean inUse;
        private Long lockedCategoryId;
        private LocalDateTime earliestRestorableDeletion;

        @Override
        public List<Category> findAllByUserId(long userId) {
            return categories.stream()
                    .filter(category -> category.userId() == userId)
                    .sorted(Comparator.comparingLong(Category::id))
                    .toList();
        }

        @Override
        public Optional<Category> findByIdAndUserId(
                long categoryId, long userId) {
            return categories.stream()
                    .filter(category -> category.id() == categoryId)
                    .filter(category -> category.userId() == userId)
                    .findFirst();
        }

        @Override
        public Optional<Category> findByIdAndUserIdForUpdate(
                long categoryId, long userId) {
            lockedCategoryId = categoryId;
            return findByIdAndUserId(categoryId, userId);
        }

        @Override
        public Optional<Category> findCustomByIdAndUserIdForUpdate(
                long categoryId, long userId) {
            return findByIdAndUserId(categoryId, userId)
                    .filter(Category::deletable);
        }

        @Override
        public Category create(long userId, String name) {
            if (categories.stream()
                    .anyMatch(category -> category.userId() == userId
                            && category.name().equals(name))) {
                throw new DuplicateKeyException("duplicate");
            }
            Category created =
                    new Category(nextId++, userId, name, null);
            categories.add(created);
            return created;
        }

        @Override
        public Category rename(
                long categoryId, long userId, String name) {
            if (categories.stream()
                    .anyMatch(category -> category.userId() == userId
                            && category.id() != categoryId
                            && category.name().equals(name))) {
                throw new DuplicateKeyException("duplicate");
            }
            Category existing = findByIdAndUserId(categoryId, userId)
                    .orElseThrow();
            Category renamed = new Category(
                    existing.id(),
                    existing.userId(),
                    name,
                    existing.systemCode());
            categories.remove(existing);
            categories.add(renamed);
            return renamed;
        }

        @Override
        public Category getOrCreateUncategorized(long userId) {
            return categories.stream()
                    .filter(category -> category.userId() == userId)
                    .filter(category ->
                            CategorySystemCode.UNCATEGORIZED.equals(
                            category.systemCode()))
                    .findFirst()
                    .orElseGet(() -> {
                        Category created = new Category(
                                nextId++,
                                userId,
                                "未分类",
                                CategorySystemCode.UNCATEGORIZED);
                        categories.add(created);
                        return created;
                    });
        }

        @Override
        public boolean isInUse(
                long categoryId,
                long userId,
                LocalDateTime earliestRestorableDeletion) {
            this.earliestRestorableDeletion =
                    earliestRestorableDeletion;
            return inUse;
        }

        @Override
        public void delete(long categoryId, long userId) {
            categories.removeIf(category ->
                    category.id() == categoryId
                            && category.userId() == userId);
        }
    }
}
