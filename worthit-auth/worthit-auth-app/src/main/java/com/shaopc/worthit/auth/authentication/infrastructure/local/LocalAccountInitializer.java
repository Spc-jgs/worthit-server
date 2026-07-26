package com.shaopc.worthit.auth.authentication.infrastructure.local;

import com.shaopc.worthit.auth.authentication.application.port.PasswordCredentialRepository;
import com.shaopc.worthit.auth.authentication.application.port.PasswordHasher;
import com.shaopc.worthit.auth.authentication.domain.UsernameNormalizer;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.regex.Pattern;

/**
 * 通过环境变量幂等创建本地测试账号。
 */
@RequiredArgsConstructor
public class LocalAccountInitializer implements ApplicationRunner {

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("[a-z0-9._-]{3,64}");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final int MAX_PASSWORD_LENGTH = 128;

    private final LocalAccountProperties properties;
    private final PasswordCredentialRepository credentialRepository;
    private final PasswordHasher passwordHasher;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (properties.getUsername() == null
                || properties.getUsername().isBlank()
                || properties.getPassword() == null
                || properties.getPassword().isBlank()) {
            throw new IllegalStateException(
                    "本地测试账号必须配置用户名和密码");
        }
        String username = UsernameNormalizer.normalize(
                properties.getUsername());
        int passwordLength = properties.getPassword().length();
        if (!USERNAME_PATTERN.matcher(username).matches()
                || passwordLength < MIN_PASSWORD_LENGTH
                || passwordLength > MAX_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "本地测试账号格式不合法");
        }
        if (credentialRepository.existsByUsername(username)) {
            return;
        }
        credentialRepository.createAccount(
                username,
                passwordHasher.encode(properties.getPassword()),
                properties.getNickname());
    }
}
