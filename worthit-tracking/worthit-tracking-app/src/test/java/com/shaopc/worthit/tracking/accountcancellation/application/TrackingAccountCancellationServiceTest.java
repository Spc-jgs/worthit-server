package com.shaopc.worthit.tracking.accountcancellation.application;

import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.tracking.accountcancellation.infrastructure.persistence.TrackingAccountCancellationMapper;
import com.shaopc.worthit.tracking.accountcancellation.infrastructure.persistence.TrackingUserWriteFenceDO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingAccountCancellationServiceTest {

    @Test
    void deletesAllOwnedTrackingDataBeforeClosingFence() {
        TrackingAccountCancellationMapper mapper =
                mock(TrackingAccountCancellationMapper.class);
        when(mapper.selectForUpdate(1001L)).thenReturn(fence("ACTIVE", null));
        when(mapper.beginCancellation(eq(1001L), eq("9001"), any()))
                .thenReturn(1);
        when(mapper.completeCancellation(eq(1001L), eq("9001"), any()))
                .thenReturn(1);
        TrackingAccountCancellationService service = service(mapper);

        assertThat(service.cancel(1001L, "9001").completed()).isTrue();

        InOrder order = inOrder(mapper);
        order.verify(mapper).beginCancellation(eq(1001L), eq("9001"), any());
        order.verify(mapper).deleteReplacements(1001L);
        order.verify(mapper).deleteDisposals(1001L);
        order.verify(mapper).deleteOutbox(1001L);
        order.verify(mapper).deleteIdempotency(1001L);
        order.verify(mapper).deleteWishes(1001L);
        order.verify(mapper).deleteSubscriptions(1001L);
        order.verify(mapper).deleteItems(1001L);
        order.verify(mapper).deleteCategories(1001L);
        order.verify(mapper).completeCancellation(eq(1001L), eq("9001"), any());
    }

    @Test
    void replaysSameCancellationAndRejectsAnotherIdentifier() {
        TrackingAccountCancellationMapper mapper =
                mock(TrackingAccountCancellationMapper.class);
        when(mapper.selectForUpdate(1001L))
                .thenReturn(fence("CANCELLED", "9001"));
        TrackingAccountCancellationService service = service(mapper);

        assertThat(service.cancel(1001L, "9001").completed()).isTrue();
        assertThatThrownBy(() -> service.cancel(1001L, "9002"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.code())
                                .isEqualTo("VAL_STATE_CONFLICT"));
        verify(mapper, never()).deleteItems(1001L);
    }

    private static TrackingAccountCancellationService service(
            TrackingAccountCancellationMapper mapper) {
        return new TrackingAccountCancellationServiceImpl(
                mapper,
                Clock.fixed(
                        Instant.parse("2026-08-16T04:00:00Z"),
                        ZoneId.of("Asia/Shanghai")),
                new SimpleMeterRegistry());
    }

    private static TrackingUserWriteFenceDO fence(
            String status, String cancellationId) {
        TrackingUserWriteFenceDO value = new TrackingUserWriteFenceDO();
        value.setUserId(1001L);
        value.setStatus(status);
        value.setCancellationId(cancellationId);
        return value;
    }
}
