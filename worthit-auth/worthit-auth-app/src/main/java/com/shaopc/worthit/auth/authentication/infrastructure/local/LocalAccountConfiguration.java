package com.shaopc.worthit.auth.authentication.infrastructure.local;

import com.shaopc.worthit.auth.authentication.application.port.PasswordCredentialRepository;
import com.shaopc.worthit.auth.authentication.application.port.PasswordHasher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * local-infra 测试账号初始化装配。
 */
@Profile("local-infra")
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LocalAccountProperties.class)
public class LocalAccountConfiguration {

    @Bean
    @ConditionalOnProperty(
            prefix = "worthit.auth.local-account",
            name = "enabled",
            havingValue = "true")
    LocalAccountInitializer localAccountInitializer(
            LocalAccountProperties properties,
            PasswordCredentialRepository credentialRepository,
            PasswordHasher passwordHasher) {
        return new LocalAccountInitializer(
                properties, credentialRepository, passwordHasher);
    }
}
