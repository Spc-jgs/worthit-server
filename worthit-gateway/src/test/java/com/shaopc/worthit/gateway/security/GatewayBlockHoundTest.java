package com.shaopc.worthit.gateway.security;

import com.shaopc.worthit.common.security.header.SecurityHeaderNames;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.blockhound.BlockingOperationError;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class GatewayBlockHoundTest {

    @Test
    void detectsBlockingCallOnParallelScheduler() {
        Mono<Void> blocking = Mono.fromRunnable(
                        GatewayBlockHoundTest::sleepBriefly)
                .subscribeOn(Schedulers.parallel())
                .then();

        StepVerifier.create(blocking)
                .expectErrorSatisfies(error ->
                        assertThat(error).isInstanceOf(BlockingOperationError.class))
                .verify();
    }

    @Test
    void trustedHeaderFilterWithFakeProvidersRemainsNonBlocking() {
        TrustedHeadersGlobalFilter filter = new TrustedHeadersGlobalFilter(
                () -> "trace-trusted",
                () -> "same-token-trusted");
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/v1/items").build());
        AtomicBoolean reached = new AtomicBoolean();

        Mono<Void> invocation = Mono.defer(() -> filter.filter(
                        exchange,
                        downstream -> {
                            assertThat(downstream.getRequest().getHeaders()
                                    .getFirst(SecurityHeaderNames.TRACE_ID))
                                    .isEqualTo("trace-trusted");
                            reached.set(true);
                            return Mono.empty();
                        }))
                .subscribeOn(Schedulers.parallel());

        StepVerifier.create(invocation).verifyComplete();
        assertThat(reached).isTrue();
    }

    private static void sleepBriefly() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("测试线程被中断", exception);
        }
    }
}
