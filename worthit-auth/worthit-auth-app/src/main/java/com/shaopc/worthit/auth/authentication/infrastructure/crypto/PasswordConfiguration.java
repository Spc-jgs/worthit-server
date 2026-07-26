package com.shaopc.worthit.auth.authentication.infrastructure.crypto;

import com.shaopc.worthit.auth.authentication.application.port.PasswordHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码单向哈希基础设施配置。
 */
@Configuration(proxyBeanMethods = false)
public class PasswordConfiguration {

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    PasswordHasher passwordHasher(PasswordEncoder passwordEncoder) {
        return new SpringSecurityPasswordHasher(passwordEncoder);
    }
}
