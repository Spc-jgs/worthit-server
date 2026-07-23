package com.shaopc.worthit.reminder.client.command;

import com.shaopc.worthit.reminder.client.contract.ReminderClientContract;
import com.shaopc.worthit.reminder.client.model.ReminderBusinessType;
import com.shaopc.worthit.reminder.client.model.ReminderOperationType;
import com.shaopc.worthit.reminder.client.model.ReminderType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReconcileReminderCommandFieldValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        validatorFactory = Validation.byDefaultProvider()
                .configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidatorFactory() {
        validatorFactory.close();
    }

    @Test
    void shouldAcceptValidFieldValues() {
        assertThat(validator.validate(validCommand())).isEmpty();
    }

    @Test
    void shouldRejectInvalidFieldValuesAtTheirPropertyPaths() {
        assertOnlyPropertyInvalid(command(
                0L, ReminderBusinessType.SUBSCRIPTION, 2001L, ReminderType.RENEWAL,
                3L, "ACTIVE", ReminderOperationType.UPDATE_BUSINESS_DATE, 1), "userId");
        assertOnlyPropertyInvalid(command(
                1001L, null, 2001L, ReminderType.RENEWAL,
                3L, "ACTIVE", ReminderOperationType.UPDATE_BUSINESS_DATE, 1), "businessType");
        assertOnlyPropertyInvalid(command(
                1001L, ReminderBusinessType.SUBSCRIPTION, 0L, ReminderType.RENEWAL,
                3L, "ACTIVE", ReminderOperationType.UPDATE_BUSINESS_DATE, 1), "businessId");
        assertOnlyPropertyInvalid(command(
                1001L, ReminderBusinessType.SUBSCRIPTION, 2001L, null,
                3L, "ACTIVE", ReminderOperationType.UPDATE_BUSINESS_DATE, 1), "reminderType");
        assertOnlyPropertyInvalid(command(
                1001L, ReminderBusinessType.SUBSCRIPTION, 2001L, ReminderType.RENEWAL,
                0L, "ACTIVE", ReminderOperationType.UPDATE_BUSINESS_DATE, 1), "sourceVersion");
        assertOnlyPropertyInvalid(command(
                1001L, ReminderBusinessType.SUBSCRIPTION, 2001L, ReminderType.RENEWAL,
                3L, " ", ReminderOperationType.UPDATE_BUSINESS_DATE, 1), "businessStatusCode");
        assertOnlyPropertyInvalid(command(
                1001L, ReminderBusinessType.SUBSCRIPTION, 2001L, ReminderType.RENEWAL,
                3L, "ACTIVE", null, 1), "operationType");
        assertOnlyPropertyInvalid(command(
                1001L, ReminderBusinessType.SUBSCRIPTION, 2001L, ReminderType.RENEWAL,
                3L, "ACTIVE", ReminderOperationType.UPDATE_BUSINESS_DATE, 0), "schemaVersion");
        assertOnlyPropertyInvalid(command(
                1001L, ReminderBusinessType.SUBSCRIPTION, 2001L, ReminderType.RENEWAL,
                3L, "ACTIVE", ReminderOperationType.UPDATE_BUSINESS_DATE, 2), "schemaVersion");
    }

    private static void assertOnlyPropertyInvalid(
            ReconcileReminderCommand command,
            String property) {
        Set<ConstraintViolation<ReconcileReminderCommand>> violations = validator.validate(command);
        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly(property);
    }

    private static ReconcileReminderCommand validCommand() {
        return command(
                1001L,
                ReminderBusinessType.SUBSCRIPTION,
                2001L,
                ReminderType.RENEWAL,
                3L,
                "ACTIVE",
                ReminderOperationType.UPDATE_BUSINESS_DATE,
                ReminderClientContract.SCHEMA_VERSION);
    }

    private static ReconcileReminderCommand command(
            long userId,
            ReminderBusinessType businessType,
            long businessId,
            ReminderType reminderType,
            long sourceVersion,
            String businessStatusCode,
            ReminderOperationType operationType,
            int schemaVersion) {
        return new ReconcileReminderCommand(
                userId,
                businessType,
                businessId,
                reminderType,
                sourceVersion,
                LocalDate.of(2026, 8, 1),
                LocalDateTime.of(2026, 7, 31, 0, 0),
                true,
                businessStatusCode,
                operationType,
                schemaVersion);
    }
}
