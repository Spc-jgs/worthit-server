package com.shaopc.worthit.tracking.app.infra;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.http.client.HttpServiceClientFactory;
import com.shaopc.worthit.common.http.context.InternalRequestContext;
import com.shaopc.worthit.common.http.interceptor.InternalRequestHeadersInterceptor;
import com.shaopc.worthit.common.http.trace.TraceIdProvider;
import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import com.shaopc.worthit.common.security.sametoken.SameTokenProvider;
import com.shaopc.worthit.tracking.infrastructure.client.ReminderClientProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import java.lang.reflect.Method;
import java.net.URI;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LocalInfraProbeClientTest {

    @Test
    void localProbeBelongsToApplicationComponentScan() {
        assertThat(LocalInfraProbeController.class.getPackageName())
                .startsWith("com.shaopc.worthit.tracking.app.");
    }

    @Test
    void createsClientOnlyWithLocalInfraProfile() {
        ApplicationContextRunner contextRunner =
                new ApplicationContextRunner()
                        .withUserConfiguration(
                                LocalInfraReminderProbeConfiguration.class,
                                ClientDependencies.class);

        contextRunner.run(context ->
                assertThat(context).doesNotHaveBean(
                        LocalInfraReminderProbeClient.class));
        contextRunner
                .withInitializer(context ->
                        context.getEnvironment()
                                .setActiveProfiles("local-infra"))
                .run(context -> assertThat(context)
                        .hasSingleBean(LocalInfraReminderProbeClient.class));
    }

    @Test
    void declaresDedicatedInternalHttpInterface() throws Exception {
        HttpExchange exchange =
                LocalInfraReminderProbeClient.class.getAnnotation(
                        HttpExchange.class);
        Method ping =
                LocalInfraReminderProbeClient.class.getMethod("ping");
        GetExchange getExchange = ping.getAnnotation(GetExchange.class);

        assertThat(exchange.url()).isEqualTo("/internal/__infra");
        assertThat(getExchange.url()).isEqualTo("/ping");
        assertThat(LocalInfraReminderProbeClient.class.getInterfaces())
                .isEmpty();
    }

    @Test
    void usesVirtualServiceNameAndInjectsTrustedHeaders() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        HttpServiceClientFactory factory =
                mock(HttpServiceClientFactory.class);
        LocalInfraReminderProbeClient expectedClient =
                mock(LocalInfraReminderProbeClient.class);
        when(factory.create(
                eq(LocalInfraReminderProbeClient.class),
                eq("worthit-reminder"),
                eq(URI.create("http://worthit-reminder")),
                eq(builder),
                any(),
                any())).thenReturn(expectedClient);

        LocalInfraReminderProbeClient client =
                new LocalInfraReminderProbeConfiguration()
                        .localInfraReminderProbeClient(
                                factory,
                                builder,
                                new ReminderClientProperties(
                                        "ignored-by-probe",
                                        Duration.ofSeconds(1),
                                        Duration.ofSeconds(1)),
                                () -> "same-token-test",
                                () -> "trace-test");

        ArgumentCaptor<InternalRequestContext> contextCaptor =
                ArgumentCaptor.forClass(InternalRequestContext.class);
        verify(factory).create(
                eq(LocalInfraReminderProbeClient.class),
                eq("worthit-reminder"),
                eq(URI.create("http://worthit-reminder")),
                eq(builder),
                any(),
                contextCaptor.capture());
        assertThat(client).isSameAs(expectedClient);

        HttpHeaders headers = new HttpHeaders();
        HttpRequest request = mock(HttpRequest.class);
        ClientHttpRequestExecution execution =
                mock(ClientHttpRequestExecution.class);
        ClientHttpResponse response = mock(ClientHttpResponse.class);
        when(request.getHeaders()).thenReturn(headers);
        when(execution.execute(any(), any())).thenReturn(response);

        new InternalRequestHeadersInterceptor(contextCaptor.getValue())
                .intercept(request, new byte[0], execution);

        assertThat(headers.getFirst(SecurityHeaderNames.SAME_TOKEN))
                .isEqualTo("same-token-test");
        assertThat(headers.getFirst(SecurityHeaderNames.CALLER_SERVICE))
                .isEqualTo("worthit-tracking");
        assertThat(headers.getFirst(SecurityHeaderNames.TRACE_ID))
                .isEqualTo("trace-test");
    }

    @Configuration(proxyBeanMethods = false)
    static class ClientDependencies {

        @Bean
        HttpServiceClientFactory httpServiceClientFactory() {
            return new HttpServiceClientFactory(new ObjectMapper());
        }

        @Bean
        @LoadBalanced
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        ReminderClientProperties reminderClientProperties() {
            return new ReminderClientProperties(
                    "worthit-reminder",
                    Duration.ofSeconds(1),
                    Duration.ofSeconds(1));
        }

        @Bean
        SameTokenProvider sameTokenProvider() {
            return () -> "same-token-test";
        }

        @Bean
        TraceIdProvider traceIdProvider() {
            return () -> "trace-test";
        }
    }
}
