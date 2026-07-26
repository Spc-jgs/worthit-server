package com.shaopc.worthit.auth.authentication.infrastructure.crypto;

import com.shaopc.worthit.auth.authentication.application.port.PasswordHasher;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;

import static org.assertj.core.api.Assertions.assertThat;

class SpringSecurityPasswordHasherTest {

    @Test
    void storesDelegatingBcryptHashAndNeverRawPassword() {
        PasswordHasher hasher = new SpringSecurityPasswordHasher(
                PasswordEncoderFactories.createDelegatingPasswordEncoder());

        String encoded = hasher.encode("correct-password");

        assertThat(encoded)
                .startsWith("{bcrypt}")
                .doesNotContain("correct-password");
        assertThat(hasher.matches("correct-password", encoded)).isTrue();
        assertThat(hasher.matches("wrong-password", encoded)).isFalse();
    }
}
