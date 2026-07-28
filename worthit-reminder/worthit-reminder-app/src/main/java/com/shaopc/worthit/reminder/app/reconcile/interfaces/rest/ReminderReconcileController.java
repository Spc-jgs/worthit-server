package com.shaopc.worthit.reminder.app.reconcile.interfaces.rest;

import com.shaopc.worthit.reminder.app.reconcile.application.ReminderReconcileService;
import com.shaopc.worthit.reminder.client.api.ReminderCommandClient;
import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.response.ReconcileReminderResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reminder Client 冻结内部 reconcile 契约实现。
 */
@Validated
@RestController
public class ReminderReconcileController
        implements ReminderCommandClient {

    private final ReminderReconcileService reconcileService;

    /**
     * 创建内部 reconcile Controller。
     */
    public ReminderReconcileController(
            ReminderReconcileService reconcileService) {
        this.reconcileService = reconcileService;
    }

    @Override
    public ReconcileReminderResponse reconcile(
            String eventId,
            ReconcileReminderCommand command) {
        return reconcileService.reconcile(eventId, command);
    }
}
