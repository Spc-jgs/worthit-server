package com.shaopc.worthit.tracking.category.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.tracking.category.domain.Category;
import com.shaopc.worthit.tracking.category.domain.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CategoryReferenceResolverTest {

    private static final long USER_ID = 1001L;

    private CategoryRepository repository;
    private CategoryReferenceResolver resolver;

    @BeforeEach
    void setUp() {
        repository = mock(CategoryRepository.class);
        resolver = new CategoryReferenceResolver(repository);
    }

    @Test
    void resolvesDefaultCategoryWithoutLockingSystemRow() {
        Category uncategorized = new Category(
                1L, USER_ID, "未分类", Category.UNCATEGORIZED);
        when(repository.getOrCreateUncategorized(USER_ID))
                .thenReturn(uncategorized);

        assertThat(resolver.resolve(null, USER_ID))
                .isSameAs(uncategorized);
        verify(repository, never())
                .findCustomByIdAndUserIdForUpdate(
                        uncategorized.id(), USER_ID);
    }

    @Test
    void locksCustomCategory() {
        Category custom = new Category(
                2L, USER_ID, "数码", null);
        when(repository.findByIdAndUserId(
                custom.id(), USER_ID))
                .thenReturn(Optional.of(custom));
        when(repository.findCustomByIdAndUserIdForUpdate(
                custom.id(), USER_ID))
                .thenReturn(Optional.of(custom));

        assertThat(resolver.resolve(custom.id(), USER_ID))
                .isSameAs(custom);
        verify(repository)
                .findCustomByIdAndUserIdForUpdate(
                        custom.id(), USER_ID);
    }

    @Test
    void validatesExplicitSystemCategoryWithoutKeepingRowLock() {
        Category uncategorized = new Category(
                1L, USER_ID, "未分类", Category.UNCATEGORIZED);
        when(repository.findByIdAndUserId(
                uncategorized.id(), USER_ID))
                .thenReturn(Optional.of(uncategorized));

        assertThat(resolver.resolve(
                uncategorized.id(), USER_ID))
                .isSameAs(uncategorized);
        verify(repository, never())
                .findCustomByIdAndUserIdForUpdate(
                        uncategorized.id(), USER_ID);
    }

    @Test
    void doesNotAcceptUnlockedCustomCategoryAfterLockMiss() {
        Category custom = new Category(
                2L, USER_ID, "数码", null);
        when(repository.findByIdAndUserId(custom.id(), USER_ID))
                .thenReturn(Optional.of(custom));
        when(repository.findCustomByIdAndUserIdForUpdate(
                custom.id(), USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                () -> resolver.resolve(custom.id(), USER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(CommonWebErrorCode
                                        .RES_NOT_FOUND));
    }

    @Test
    void hidesMissingOrForeignCategoryAsNotFound() {
        when(repository.findByIdAndUserId(3L, USER_ID))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(3L, USER_ID))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception -> assertThat(exception.errorCode())
                                .isEqualTo(CommonWebErrorCode
                                        .RES_NOT_FOUND));
        verify(repository, never())
                .findCustomByIdAndUserIdForUpdate(3L, USER_ID);
    }
}
