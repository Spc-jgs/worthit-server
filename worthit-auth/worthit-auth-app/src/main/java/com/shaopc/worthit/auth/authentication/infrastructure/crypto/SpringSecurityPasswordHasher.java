package com.shaopc.worthit.auth.authentication.infrastructure.crypto;

import com.shaopc.worthit.auth.authentication.application.port.PasswordHasher;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 基于 Spring Security {@link PasswordEncoder} 的密码哈希适配器。
 */
@RequiredArgsConstructor
public class SpringSecurityPasswordHasher implements PasswordHasher {

    private final PasswordEncoder passwordEncoder;

    @Override
    public String encode(CharSequence rawPassword) {
        return passwordEncoder.encode(rawPassword);
    }

    @Override
    public boolean matches(
            CharSequence rawPassword, String encodedPassword) {
        return passwordEncoder.matches(rawPassword, encodedPassword);
    }
}
