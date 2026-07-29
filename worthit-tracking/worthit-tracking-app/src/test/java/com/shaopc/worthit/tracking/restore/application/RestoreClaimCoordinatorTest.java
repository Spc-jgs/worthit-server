package com.shaopc.worthit.tracking.restore.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import com.shaopc.worthit.tracking.category.application.CategoryReferenceResolver;
import com.shaopc.worthit.tracking.idempotency.application.RestoreTokenClaim;
import com.shaopc.worthit.tracking.idempotency.application.RestoreTokenStore;
import com.shaopc.worthit.tracking.idempotency.application.TrackingOperation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RestoreClaimCoordinatorTest {

    private static final long USER_ID = 1001L;
    private static final long CATEGORY_ID = 2001L;
    private static final long RESOURCE_ID = 3001L;
    private static final long DELETED_VERSION = 2L;
    private static final TrackingOperation OPERATION =
            TrackingOperation.ITEM_RESTORE;
    private static final String RESTORE_TOKEN = "restore-token";
    private static final LocalDateTime NOW =
            LocalDateTime.of(2026, 7, 29, 17, 0, 59);

    private CategoryReferenceResolver categoryReferenceResolver;
    private RestoreTokenStore restoreTokenStore;
    private RestoreClaimCoordinator coordinator;

    @BeforeEach
    void setUp() {
        categoryReferenceResolver =
                mock(CategoryReferenceResolver.class);
        restoreTokenStore = mock(RestoreTokenStore.class);
        coordinator = new RestoreClaimCoordinator(
                categoryReferenceResolver, restoreTokenStore);
    }

    @Test
    void reservesCategoryBeforeClaimingRestoreToken() {
        RestoreTokenClaim<String> available =
                new RestoreTokenClaim<>(
                        RestoreTokenClaim.Status.AVAILABLE,
                        null);
        when(restoreTokenStore.claim(
                USER_ID,
                OPERATION,
                RESOURCE_ID,
                DELETED_VERSION,
                RESTORE_TOKEN,
                NOW,
                String.class)).thenReturn(available);

        RestoreTokenClaim<String> actual =
                coordinator.claimWithCategoryReservation(
                        USER_ID,
                        CATEGORY_ID,
                        OPERATION,
                        RESOURCE_ID,
                        DELETED_VERSION,
                        RESTORE_TOKEN,
                        NOW,
                        String.class);

        assertThat(actual).isSameAs(available);
        InOrder order = inOrder(
                categoryReferenceResolver,
                restoreTokenStore);
        order.verify(categoryReferenceResolver)
                .resolve(CATEGORY_ID, USER_ID);
        order.verify(restoreTokenStore).claim(
                USER_ID,
                OPERATION,
                RESOURCE_ID,
                DELETED_VERSION,
                RESTORE_TOKEN,
                NOW,
                String.class);
    }

    @Test
    void doesNotClaimTokenWhenCategoryCannotBeReserved() {
        when(categoryReferenceResolver.resolve(
                CATEGORY_ID, USER_ID))
                .thenThrow(new BusinessException(
                        CommonWebErrorCode.RES_NOT_FOUND));

        assertThatThrownBy(() ->
                coordinator.claimWithCategoryReservation(
                        USER_ID,
                        CATEGORY_ID,
                        OPERATION,
                        RESOURCE_ID,
                        DELETED_VERSION,
                        RESTORE_TOKEN,
                        NOW,
                        String.class))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(restoreTokenStore);
    }
}
