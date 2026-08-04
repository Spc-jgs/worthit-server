package com.shaopc.worthit.reminder.app.dataexport.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.reminder.client.response.ReminderDataExportResponse;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReminderDataExportServiceTest {

    private static final Instant CAPTURED_AT = Instant.parse("2026-08-04T02:00:00Z");

    @Test
    void appliesOneAggregateLimitAcrossBindingsAndInstances() {
        ReminderDataExportQuery query = mock(ReminderDataExportQuery.class);
        when(query.bindings(42L, 2)).thenReturn(List.of(binding("1")));
        when(query.instances(42L, 1)).thenReturn(List.of(instance("2")));
        ReminderDataExportService service = service(query, 1, 8 * 1024 * 1024);

        assertThatThrownBy(() -> service.exportUserData(42L))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.code())
                                .isEqualTo("DATA_EXPORT_LIMIT_EXCEEDED"));
    }

    @Test
    void returnsAllHistoricalStatesForOnlyTheRequestedUser() {
        ReminderDataExportQuery query = mock(ReminderDataExportQuery.class);
        when(query.bindings(42L, 11)).thenReturn(List.of(binding("1")));
        when(query.instances(42L, 10)).thenReturn(List.of(instance("2")));

        ReminderDataExportResponse response = service(query, 10, 8 * 1024 * 1024)
                .exportUserData(42L);

        assertThat(response.userId()).isEqualTo("42");
        assertThat(response.instances()).singleElement()
                .extracting(ReminderDataExportResponse.Instance::status)
                .isEqualTo("CANCELED");
    }

    private ReminderDataExportService service(
            ReminderDataExportQuery query,
            int maxRecords,
            int maxBytes) {
        return new ReminderDataExportServiceImpl(
                query,
                new ObjectMapper().registerModule(new JavaTimeModule()),
                Clock.fixed(CAPTURED_AT, ZoneId.of("Asia/Shanghai")),
                new ReminderDataExportProperties(maxRecords, maxBytes));
    }

    private ReminderDataExportResponse.Binding binding(String id) {
        return new ReminderDataExportResponse.Binding(
                id, "ITEM", "8", "WARRANTY", true,
                CAPTURED_AT, CAPTURED_AT);
    }

    private ReminderDataExportResponse.Instance instance(String id) {
        return new ReminderDataExportResponse.Instance(
                id, "1", null, CAPTURED_AT, "Asia/Shanghai", "CANCELED",
                CAPTURED_AT, "DISABLED", CAPTURED_AT, CAPTURED_AT);
    }
}
