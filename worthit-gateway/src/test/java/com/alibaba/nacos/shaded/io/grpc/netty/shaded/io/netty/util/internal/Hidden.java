package com.alibaba.nacos.shaded.io.grpc.netty.shaded.io.netty.util.internal;

import reactor.blockhound.BlockHound;
import reactor.blockhound.integration.BlockHoundIntegration;

/**
 * Test-classpath compatibility shim for the stale BlockHound SPI entry packaged in
 * {@code nacos-client:3.0.3}.
 *
 * <p>Nacos relocates its gRPC Netty classes and keeps the Netty BlockHound service
 * declaration, but the declared provider class is absent from the client JAR. The
 * JUnit Platform listener loads every declared provider before test discovery, so a
 * no-op provider is required to let the real Reactor Netty and project integrations
 * load. This class is test-only and must be removed when the upstream JAR supplies
 * the declared provider or removes the stale service entry.</p>
 */
final class Hidden {

    private Hidden() {
    }

    public static final class NettyBlockHoundIntegration implements BlockHoundIntegration {

        @Override
        public void applyTo(BlockHound.Builder builder) {
            // Nacos's relocated Netty runs on its own client threads. No allowlist is
            // added here, so unexpected blocking on Reactor threads remains visible.
        }
    }
}
