package com.shaopc.worthit.reminder.client.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.shaopc.worthit.reminder.client.contract.ReminderClientContract;
import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderOperationType;
import com.shaopc.worthit.reminder.client.model.ReminderType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReconcileReminderCommandJsonTest {

    @Test
    void shouldSerializeFrozenCommandFieldsAndValues() {
        JsonMapper objectMapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();

        JsonNode json = objectMapper.valueToTree(validCommand());
        Set<String> fields = new HashSet<>();
        json.fieldNames().forEachRemaining(fields::add);

        assertThat(fields).containsExactlyInAnyOrder(
                "userId",
                "businessType",
                "businessId",
                "reminderType",
                "sourceVersion",
                "businessDate",
                "remindAt",
                "reminderEnabled",
                "businessStatusCode",
                "operationType",
                "schemaVersion");
        assertThat(json.path("businessType").asText()).isEqualTo("SUBSCRIPTION");
        assertThat(json.path("reminderType").asText()).isEqualTo("RENEWAL");
        assertThat(json.path("operationType").asText()).isEqualTo("UPDATE_BUSINESS_DATE");
        assertThat(json.path("businessDate").asText()).isEqualTo("2026-08-01");
        assertThat(json.path("remindAt").asText()).isEqualTo("2026-07-31T00:00:00");
        assertThat(json.path("schemaVersion").asInt()).isEqualTo(1);
        assertThat(json.has("cause")).isFalse();
        assertThat(json.has("resolutionCause")).isFalse();
        assertThat(json.has("reconcileCause")).isFalse();
        assertThat(json.has("correction")).isFalse();
        assertThat(json.has("displayName")).isFalse();
    }

    private static ReconcileReminderCommand validCommand() {
        return new ReconcileReminderCommand(
                1001L,
                ReminderBusinessType.SUBSCRIPTION,
                2001L,
                ReminderType.RENEWAL,
                3L,
                LocalDate.of(2026, 8, 1),
                LocalDateTime.of(2026, 7, 31, 0, 0),
                true,
                "ACTIVE",
                ReminderOperationType.UPDATE_BUSINESS_DATE,
                ReminderClientContract.SCHEMA_VERSION);
    }
}
