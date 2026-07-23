package com.shaopc.worthit.reminder.client.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.reminder.client.model.ReconcileResultCode;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReconcileReminderResponseJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeFrozenResponseFieldsAndAppliedResult() {
        ReconcileReminderResponse response = new ReconcileReminderResponse(
                true,
                ReconcileResultCode.APPLIED,
                false,
                3001L,
                3L);

        JsonNode json = objectMapper.valueToTree(response);
        Set<String> fields = new HashSet<>();
        json.fieldNames().forEachRemaining(fields::add);

        assertThat(fields).containsExactlyInAnyOrder(
                "applied",
                "resultCode",
                "idempotent",
                "bindingId",
                "lastSourceVersion");
        assertThat(json.path("applied").asBoolean()).isTrue();
        assertThat(json.path("resultCode").asText()).isEqualTo("APPLIED");
        assertThat(json.path("idempotent").asBoolean()).isFalse();
        assertThat(json.path("bindingId").asLong()).isEqualTo(3001L);
        assertThat(json.path("lastSourceVersion").asLong()).isEqualTo(3L);
    }

    @Test
    void shouldSerializeIgnoredOldAsTheOnlyOtherResultCode() {
        ReconcileReminderResponse response = new ReconcileReminderResponse(
                false,
                ReconcileResultCode.IGNORED_OLD,
                false,
                3001L,
                4L);

        JsonNode json = objectMapper.valueToTree(response);

        assertThat(json.path("resultCode").asText()).isEqualTo("IGNORED_OLD");
        assertThat(ReconcileResultCode.values())
                .extracting(Enum::name)
                .doesNotContain("IDEMPOTENT", "CONFLICT");
    }
}
