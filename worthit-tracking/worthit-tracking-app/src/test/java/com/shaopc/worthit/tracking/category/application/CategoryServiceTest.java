package com.shaopc.worthit.tracking.category.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.tracking.category.domain.Category;
import com.shaopc.worthit.tracking.category.domain.CategoryErrorCode;
import com.shaopc.worthit.tracking.category.domain.CategoryRepository;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

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
        service = new CategoryService(
                repository, () -> new UserContext(USER_ID));
    }

    @Test
    void listsOnlyCurrentUsersActiveCategories() {
        repository.categories.addAll(List.of(
                new Category(1L, USER_ID, "未分类", "UNCATEGORIZED"),
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
    void rejectsDeletingSystemCategory() {
        repository.categories.add(
                new Category(
                        1L, USER_ID, "未分类", "UNCATEGORIZED"));

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
    }

    private static final class InMemoryCategoryRepository
            implements CategoryRepository {

        private final List<Category> categories = new ArrayList<>();
        private long nextId = 10L;
        private boolean inUse;

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
        public Category getOrCreateUncategorized(long userId) {
            return categories.stream()
                    .filter(category -> category.userId() == userId)
                    .filter(category -> Category.UNCATEGORIZED.equals(
                            category.systemCode()))
                    .findFirst()
                    .orElseGet(() -> {
                        Category created = new Category(
                                nextId++,
                                userId,
                                "未分类",
                                Category.UNCATEGORIZED);
                        categories.add(created);
                        return created;
                    });
        }

        @Override
        public boolean isInUse(long categoryId, long userId) {
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
