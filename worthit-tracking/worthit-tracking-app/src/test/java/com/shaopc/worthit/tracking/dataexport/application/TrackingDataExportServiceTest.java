package com.shaopc.worthit.tracking.dataexport.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.tracking.client.response.TrackingDataExportResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrackingDataExportServiceTest {

    private static final long USER_ID = 42L;
    private static final Instant CAPTURED_AT = Instant.parse("2026-08-04T02:00:00Z");

    @Test
    void rejectsTheFirstRecordBeyondTheAggregateLimitWithoutQueryingLaterTables() {
        TrackingDataExportQuery query = mock(TrackingDataExportQuery.class);
        when(query.categories(USER_ID, 2)).thenReturn(List.of(
                category("1"), category("2")));
        TrackingDataExportService service = service(query, 1, 8 * 1024 * 1024);

        assertThatThrownBy(() -> service.exportUserData(USER_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("DATA_EXPORT_LIMIT_EXCEEDED"));
        verify(query, never()).items(anyLong(), anyInt());
    }

    @Test
    void returnsStableStringIdsAndIncludesLogicallyDeletedRows() {
        TrackingDataExportQuery query = mock(TrackingDataExportQuery.class);
        when(query.categories(USER_ID, 11)).thenReturn(List.of(category("9")));
        when(query.items(USER_ID, 10)).thenReturn(List.of());
        when(query.subscriptions(USER_ID, 10)).thenReturn(List.of());
        when(query.wishes(USER_ID, 10)).thenReturn(List.of());
        when(query.disposals(USER_ID, 10)).thenReturn(List.of());
        when(query.replacements(USER_ID, 10)).thenReturn(List.of());

        TrackingDataExportResponse response = service(query, 10, 8 * 1024 * 1024)
                .exportUserData(USER_ID);

        assertThat(response.userId()).isEqualTo("42");
        assertThat(response.capturedAt()).isEqualTo(CAPTURED_AT);
        assertThat(response.categories()).singleElement()
                .satisfies(category -> {
                    assertThat(category.id()).isEqualTo("9");
                    assertThat(category.deleted()).isTrue();
                });
    }

    private TrackingDataExportService service(
            TrackingDataExportQuery query,
            int maxRecords,
            int maxBytes) {
        return new TrackingDataExportServiceImpl(
                query,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                Clock.fixed(CAPTURED_AT, ZoneId.of("Asia/Shanghai")),
                new TrackingDataExportProperties(maxRecords, maxBytes));
    }

    private TrackingDataExportResponse.Category category(String id) {
        return new TrackingDataExportResponse.Category(
                id, "分类", null, 1L, CAPTURED_AT, CAPTURED_AT,
                true, CAPTURED_AT);
    }
}
