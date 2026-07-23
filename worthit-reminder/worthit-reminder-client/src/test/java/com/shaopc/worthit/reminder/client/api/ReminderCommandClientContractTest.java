package com.shaopc.worthit.reminder.client.api;

import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.client.contract.ReminderClientContract;
import com.shaopc.worthit.reminder.client.response.ReconcileReminderResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ReminderCommandClientContractTest {

    @Test
    void shouldExposeFrozenReconcileHttpContract() throws NoSuchMethodException {
        HttpExchange typeExchange =
                ReminderCommandClient.class.getAnnotation(HttpExchange.class);
        assertThat(typeExchange).isNotNull();
        assertThat(typeExchange.value()).isEqualTo(ReminderClientContract.BASE_PATH);

        Method method = ReminderCommandClient.class.getDeclaredMethod(
                "reconcile", String.class, ReconcileReminderCommand.class);
        PostExchange postExchange = method.getAnnotation(PostExchange.class);
        assertThat(postExchange).isNotNull();
        assertThat(postExchange.value()).isEqualTo(ReminderClientContract.RECONCILE_PATH);
        assertThat(method.getReturnType()).isEqualTo(ReconcileReminderResponse.class);

        Annotation[] eventIdAnnotations = method.getParameterAnnotations()[0];
        RequestHeader requestHeader = annotation(eventIdAnnotations, RequestHeader.class);
        NotBlank notBlank = annotation(eventIdAnnotations, NotBlank.class);
        assertThat(requestHeader.value()).isEqualTo(ReminderClientContract.IDEMPOTENCY_HEADER);
        assertThat(notBlank.message()).isEqualTo("幂等键不能为空");

        Annotation[] commandAnnotations = method.getParameterAnnotations()[1];
        assertThat(annotation(commandAnnotations, Valid.class)).isNotNull();
        assertThat(annotation(commandAnnotations, RequestBody.class)).isNotNull();
    }

    private static <A extends Annotation> A annotation(
            Annotation[] annotations,
            Class<A> annotationType) {
        return Arrays.stream(annotations)
                .filter(annotationType::isInstance)
                .map(annotationType::cast)
                .findFirst()
                .orElseThrow();
    }
}
