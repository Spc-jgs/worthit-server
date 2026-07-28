package com.shaopc.worthit.reminder.app.reconcile.interfaces.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.reminder.app.reconcile.application.ReminderReconcileService;
import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.model.ReconcileResultCode;
import com.shaopc.worthit.reminder.client.response.ReconcileReminderResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ReminderReconcileControllerTest {

    private final ReminderReconcileService service =
            mock(ReminderReconcileService.class);
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new ReminderReconcileController(service))
                .build();
    }

    @Test
    void exposesFrozenInternalClientContract() throws Exception {
        when(service.reconcile(
                eq("event-001"),
                any(ReconcileReminderCommand.class)))
                .thenReturn(new ReconcileReminderResponse(
                        true,
                        ReconcileResultCode.APPLIED,
                        false,
                        41L,
                        3L));

        mockMvc.perform(post(
                        "/internal/v1/reminders/reconcile")
                        .header(
                                "X-Idempotency-Key",
                                "event-001")
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userId": 11,
                                  "businessType": "ITEM",
                                  "businessId": 21,
                                  "reminderType": "WARRANTY",
                                  "sourceVersion": 3,
                                  "businessDate": "2026-08-01",
                                  "remindAt": "2026-07-25T00:00:00",
                                  "reminderEnabled": true,
                                  "businessStatusCode": "HOLDING",
                                  "operationType": "INITIAL_SYNC",
                                  "schemaVersion": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applied").value(true))
                .andExpect(jsonPath("$.resultCode")
                        .value("APPLIED"))
                .andExpect(jsonPath("$.idempotent")
                        .value(false))
                .andExpect(jsonPath("$.bindingId")
                        .value(41))
                .andExpect(jsonPath("$.lastSourceVersion")
                        .value(3));

        verify(service).reconcile(
                eq("event-001"),
                any(ReconcileReminderCommand.class));
    }

    @Test
    void rejectsMissingIdempotencyHeader() throws Exception {
        mockMvc.perform(post(
                        "/internal/v1/reminders/reconcile")
                        .contentType(
                                MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of())))
                .andExpect(status().isBadRequest());
    }
}
