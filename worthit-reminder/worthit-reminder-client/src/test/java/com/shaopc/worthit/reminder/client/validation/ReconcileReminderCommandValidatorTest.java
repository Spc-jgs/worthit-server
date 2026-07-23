package com.shaopc.worthit.reminder.client.validation;

import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
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
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ReconcileReminderCommandValidatorTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 1);

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
    void shouldAcceptOnlyMatchingBusinessAndReminderTypes() {
        for (ReminderBusinessType businessType : ReminderBusinessType.values()) {
            ReminderType supportedType = supportedReminderType(businessType);
            assertThat(validator.validate(validCommand(businessType))).isEmpty();

            for (ReminderType reminderType : ReminderType.values()) {
                if (reminderType != supportedType) {
                    assertInvalidProperty(
                            command(
                                    businessType,
                                    reminderType,
                                    BUSINESS_DATE,
                                    expectedRemindAt(reminderType),
                                    true,
                                    ReminderOperationType.INITIAL_SYNC),
                            "reminderType");
                }
            }
        }
    }

    @Test
    void shouldEnforceReminderDateCompleteness() {
        assertInvalidProperty(
                command(
                        ReminderBusinessType.SUBSCRIPTION,
                        ReminderType.RENEWAL,
                        null,
                        BUSINESS_DATE.minusDays(1).atStartOfDay(),
                        true,
                        ReminderOperationType.ENABLE_REMINDER),
                "businessDate");
        assertInvalidProperty(
                command(
                        ReminderBusinessType.SUBSCRIPTION,
                        ReminderType.RENEWAL,
                        BUSINESS_DATE,
                        null,
                        true,
                        ReminderOperationType.ENABLE_REMINDER),
                "remindAt");
        assertThat(validator.validate(command(
                ReminderBusinessType.SUBSCRIPTION,
                ReminderType.RENEWAL,
                null,
                null,
                false,
                ReminderOperationType.DISABLE_REMINDER))).isEmpty();
        assertInvalidProperty(
                command(
                        ReminderBusinessType.SUBSCRIPTION,
                        ReminderType.RENEWAL,
                        null,
                        BUSINESS_DATE.minusDays(1).atStartOfDay(),
                        false,
                        ReminderOperationType.DISABLE_REMINDER),
                "remindAt");
        assertThat(validator.validate(command(
                ReminderBusinessType.SUBSCRIPTION,
                ReminderType.RENEWAL,
                BUSINESS_DATE,
                null,
                false,
                ReminderOperationType.DISABLE_REMINDER))).isEmpty();
    }

    @Test
    void shouldEnforceExactReminderScheduleForEveryReminderType() {
        for (ReminderBusinessType businessType : ReminderBusinessType.values()) {
            ReminderType reminderType = supportedReminderType(businessType);
            LocalDateTime expected = expectedRemindAt(reminderType);
            assertThat(validator.validate(command(
                    businessType,
                    reminderType,
                    BUSINESS_DATE,
                    expected,
                    true,
                    ReminderOperationType.INITIAL_SYNC))).isEmpty();
            assertInvalidProperty(
                    command(
                            businessType,
                            reminderType,
                            BUSINESS_DATE,
                            expected.plusMinutes(1),
                            true,
                            ReminderOperationType.INITIAL_SYNC),
                    "remindAt");
        }
    }

    @Test
    void shouldAcceptCommonOperationsForEveryBusinessType() {
        EnumSet<ReminderOperationType> commonOperations = EnumSet.of(
                ReminderOperationType.INITIAL_SYNC,
                ReminderOperationType.ENABLE_REMINDER,
                ReminderOperationType.DISABLE_REMINDER,
                ReminderOperationType.UPDATE_BUSINESS_DATE,
                ReminderOperationType.CORRECT_BUSINESS_DATE,
                ReminderOperationType.DELETE_OBJECT);

        for (ReminderBusinessType businessType : ReminderBusinessType.values()) {
            for (ReminderOperationType operationType : commonOperations) {
                assertThat(validator.validate(withOperation(validCommand(businessType), operationType)))
                        .isEmpty();
            }
        }
    }

    @Test
    void shouldAcceptExclusiveOperationsForTheirBusinessTypeOnly() {
        assertExclusiveOperations(
                ReminderBusinessType.SUBSCRIPTION,
                EnumSet.of(
                        ReminderOperationType.ADVANCE_NEXT_RENEWAL_DATE,
                        ReminderOperationType.PAUSE_SUBSCRIPTION,
                        ReminderOperationType.END_SUBSCRIPTION,
                        ReminderOperationType.RESUME_SUBSCRIPTION));
        assertExclusiveOperations(
                ReminderBusinessType.WISH,
                EnumSet.of(
                        ReminderOperationType.PURCHASE_WISH,
                        ReminderOperationType.ABANDON_WISH,
                        ReminderOperationType.CONTINUE_CONSIDERING));
        assertExclusiveOperations(
                ReminderBusinessType.ITEM,
                EnumSet.of(ReminderOperationType.DISPOSE_ITEM));
    }

    private static void assertExclusiveOperations(
            ReminderBusinessType owner,
            Set<ReminderOperationType> operations) {
        for (ReminderOperationType operationType : operations) {
            assertThat(validator.validate(withOperation(validCommand(owner), operationType))).isEmpty();
            for (ReminderBusinessType other : ReminderBusinessType.values()) {
                if (other != owner) {
                    assertInvalidProperty(
                            withOperation(validCommand(other), operationType),
                            "operationType");
                }
            }
        }
    }

    private static void assertInvalidProperty(
            ReconcileReminderCommand command,
            String property) {
        Set<ConstraintViolation<ReconcileReminderCommand>> violations = validator.validate(command);
        assertThat(violations)
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains(property);
    }

    private static ReconcileReminderCommand validCommand(ReminderBusinessType businessType) {
        ReminderType reminderType = supportedReminderType(businessType);
        return command(
                businessType,
                reminderType,
                BUSINESS_DATE,
                expectedRemindAt(reminderType),
                true,
                ReminderOperationType.INITIAL_SYNC);
    }

    private static ReconcileReminderCommand withOperation(
            ReconcileReminderCommand source,
            ReminderOperationType operationType) {
        return command(
                source.businessType(),
                source.reminderType(),
                source.businessDate(),
                source.remindAt(),
                source.reminderEnabled(),
                operationType);
    }

    private static ReconcileReminderCommand command(
            ReminderBusinessType businessType,
            ReminderType reminderType,
            LocalDate businessDate,
            LocalDateTime remindAt,
            boolean reminderEnabled,
            ReminderOperationType operationType) {
        return new ReconcileReminderCommand(
                1001L,
                businessType,
                2001L,
                reminderType,
                3L,
                businessDate,
                remindAt,
                reminderEnabled,
                "ACTIVE",
                operationType,
                ReminderClientContract.SCHEMA_VERSION);
    }

    private static ReminderType supportedReminderType(ReminderBusinessType businessType) {
        return switch (businessType) {
            case ITEM -> ReminderType.WARRANTY;
            case SUBSCRIPTION -> ReminderType.RENEWAL;
            case WISH -> ReminderType.WATCH;
        };
    }

    private static LocalDateTime expectedRemindAt(ReminderType reminderType) {
        return switch (reminderType) {
            case RENEWAL -> BUSINESS_DATE.minusDays(1).atStartOfDay();
            case WARRANTY -> BUSINESS_DATE.minusDays(7).atStartOfDay();
            case WATCH -> BUSINESS_DATE.atStartOfDay();
        };
    }
}
