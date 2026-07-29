package com.shaopc.worthit.tracking.wish.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.context.UserContext;
import com.shaopc.worthit.tracking.category.application.CategoryReferenceResolver;
import com.shaopc.worthit.tracking.idempotency.application.IdempotencyStore;
import com.shaopc.worthit.tracking.idempotency.application.RequestDigest;
import com.shaopc.worthit.tracking.idempotency.application.RestoreTokenStore;
import com.shaopc.worthit.tracking.item.domain.ItemRepository;
import com.shaopc.worthit.tracking.outbox.application.ReminderOutboxWriter;
import com.shaopc.worthit.tracking.restore.application.RestoreWindowPolicy;
import com.shaopc.worthit.tracking.security.CurrentUserProvider;
import com.shaopc.worthit.tracking.wish.domain.WishRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WishServiceTest {

    private static final LocalDate TODAY =
            LocalDate.of(2026, 7, 29);

    private WishRepository wishRepository;
    private ItemRepository itemRepository;
    private IdempotencyStore idempotencyStore;
    private RequestDigest requestDigest;
    private WishService service;

    @BeforeEach
    void setUp() {
        wishRepository = mock(WishRepository.class);
        itemRepository = mock(ItemRepository.class);
        CategoryReferenceResolver categoryReferenceResolver =
                mock(CategoryReferenceResolver.class);
        idempotencyStore = mock(IdempotencyStore.class);
        requestDigest = mock(RequestDigest.class);
        RestoreTokenStore restoreTokenStore =
                mock(RestoreTokenStore.class);
        ReminderOutboxWriter outboxWriter =
                mock(ReminderOutboxWriter.class);
        CurrentUserProvider currentUserProvider =
                mock(CurrentUserProvider.class);
        when(currentUserProvider.currentUser())
                .thenReturn(new UserContext(1001L));
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-28T16:00:00Z"),
                ZoneId.of("Asia/Shanghai"));
        service = new WishService(
                wishRepository,
                itemRepository,
                categoryReferenceResolver,
                idempotencyStore,
                requestDigest,
                restoreTokenStore,
                outboxWriter,
                currentUserProvider,
                clock,
                new RestoreWindowPolicy());
    }

    @Test
    void rejectsPastDefaultReminderBeforeClaimingIdempotency() {
        CreateWishCommand command = createCommand(
                TODAY.minusDays(1), null);

        assertThatThrownBy(() -> service.create("key", command))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(
                wishRepository, idempotencyStore, requestDigest);
    }

    @Test
    void rejectsEnabledReminderWithoutDeadline() {
        CreateWishCommand command = createCommand(null, true);

        assertThatThrownBy(() -> service.create("key", command))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(
                wishRepository, idempotencyStore, requestDigest);
    }

    @Test
    void rejectsInvalidPurchaseVersionBeforeRepositoryAccess() {
        assertThatThrownBy(
                () -> service.purchase(10L, 0L, "key"))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(
                wishRepository, itemRepository,
                idempotencyStore, requestDigest);
    }

    private static CreateWishCommand createCommand(
            LocalDate deadline, Boolean reminderEnabled) {
        return new CreateWishCommand(
                "显示器",
                null,
                new BigDecimal("1000"),
                BigDecimal.ONE,
                null,
                "提升效率",
                null,
                deadline,
                reminderEnabled);
    }
}
