package com.shaopc.worthit.reminder.client.model;

import com.shaopc.worthit.reminder.client.contract.ReminderClientContract;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderClientModelContractTest {

    @Test
    void shouldExposeFrozenProtocolConstants() {
        assertThat(ReminderClientContract.BASE_PATH).isEqualTo("/internal/v1/reminders");
        assertThat(ReminderClientContract.RECONCILE_PATH).isEqualTo("/reconcile");
        assertThat(ReminderClientContract.IDEMPOTENCY_HEADER).isEqualTo("X-Idempotency-Key");
        assertThat(ReminderClientContract.SCHEMA_VERSION).isEqualTo(1);
    }

    @Test
    void shouldExposeFrozenEnumValuesOnly() {
        assertThat(ReminderBusinessType.values())
                .extracting(Enum::name)
                .containsExactly("ITEM", "SUBSCRIPTION", "WISH");
        assertThat(ReminderType.values())
                .extracting(Enum::name)
                .containsExactly("RENEWAL", "WARRANTY", "WATCH");
        assertThat(ReminderOperationType.values())
                .extracting(Enum::name)
                .containsExactly(
                        "INITIAL_SYNC",
                        "ENABLE_REMINDER",
                        "DISABLE_REMINDER",
                        "UPDATE_BUSINESS_DATE",
                        "ADVANCE_NEXT_RENEWAL_DATE",
                        "CORRECT_BUSINESS_DATE",
                        "PAUSE_SUBSCRIPTION",
                        "END_SUBSCRIPTION",
                        "RESUME_SUBSCRIPTION",
                        "PURCHASE_WISH",
                        "ABANDON_WISH",
                        "CONTINUE_CONSIDERING",
                        "DISPOSE_ITEM",
                        "DELETE_OBJECT");
        assertThat(ReconcileResultCode.values())
                .extracting(Enum::name)
                .containsExactly("APPLIED", "IGNORED_OLD");
    }
}
