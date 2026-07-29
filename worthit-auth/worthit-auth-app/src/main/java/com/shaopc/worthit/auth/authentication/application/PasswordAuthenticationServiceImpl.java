package com.shaopc.worthit.auth.authentication.application;

import com.shaopc.worthit.auth.authentication.application.port.IssuedToken;
import com.shaopc.worthit.auth.authentication.application.port.PasswordCredentialRepository;
import com.shaopc.worthit.auth.authentication.application.port.PasswordHasher;
import com.shaopc.worthit.auth.authentication.application.port.UserSession;
import com.shaopc.worthit.auth.authentication.domain.AuthUser;
import com.shaopc.worthit.auth.authentication.domain.PasswordCredential;
import com.shaopc.worthit.auth.authentication.domain.UsernameNormalizer;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.error.SecurityErrorCode;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 账号密码登录用例。
 */
@Service
public class PasswordAuthenticationServiceImpl
        implements PasswordAuthenticationService {

    private static final String DUMMY_PASSWORD =
            "worthit-password-verification-placeholder";

    private final PasswordCredentialRepository credentialRepository;
    private final PasswordHasher passwordHasher;
    private final UserSession userSession;
    private final String dummyPasswordHash;

    public PasswordAuthenticationServiceImpl(
            PasswordCredentialRepository credentialRepository,
            PasswordHasher passwordHasher,
            UserSession userSession) {
        this.credentialRepository = credentialRepository;
        this.passwordHasher = passwordHasher;
        this.userSession = userSession;
        this.dummyPasswordHash = passwordHasher.encode(DUMMY_PASSWORD);
    }

    /**
     * 校验账号密码并签发与微信登录一致的登录态。
     */
    @Override
    public AuthenticationResult login(PasswordLoginCommand command) {
        String username = UsernameNormalizer.normalize(command.username());
        Optional<PasswordCredential> credential =
                credentialRepository.findByUsername(username);
        String passwordHash = credential
                .map(PasswordCredential::passwordHash)
                .orElse(dummyPasswordHash);
        boolean passwordMatches = passwordHasher.matches(
                command.password(), passwordHash);
        if (credential.isEmpty() || !passwordMatches) {
            throw new BusinessException(
                    SecurityErrorCode.AUTH_UNAUTHORIZED,
                    "账号或密码错误");
        }

        AuthUser user = credential.orElseThrow().user();
        if (!user.active()) {
            throw new BusinessException(
                    SecurityErrorCode.AUTH_FORBIDDEN);
        }
        IssuedToken token = userSession.login(user.id());
        return new AuthenticationResult(token, user, false);
    }
}
