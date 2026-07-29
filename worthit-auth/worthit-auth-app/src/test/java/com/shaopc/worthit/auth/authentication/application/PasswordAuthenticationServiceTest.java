package com.shaopc.worthit.auth.authentication.application;

import com.shaopc.worthit.auth.authentication.application.port.IssuedToken;
import com.shaopc.worthit.auth.authentication.application.port.PasswordCredentialRepository;
import com.shaopc.worthit.auth.authentication.application.port.PasswordHasher;
import com.shaopc.worthit.auth.authentication.application.port.UserSession;
import com.shaopc.worthit.auth.authentication.domain.AuthUser;
import com.shaopc.worthit.auth.authentication.domain.PasswordCredential;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.error.SecurityErrorCode;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordAuthenticationServiceTest {

    @Test
    void issuesTokenWhenUsernameAndPasswordMatch() {
        FakePasswordCredentialRepository repository =
                new FakePasswordCredentialRepository();
        repository.credential = new PasswordCredential(
                activeUser(), "encoded:correct-password");
        FakeUserSession session = new FakeUserSession();
        PasswordAuthenticationService service =
                new PasswordAuthenticationServiceImpl(
                        repository, new FakePasswordHasher(), session);

        AuthenticationResult result = service.login(
                new PasswordLoginCommand(
                        " Local.User ", "correct-password"));

        assertThat(repository.lastUsername).isEqualTo("local.user");
        assertThat(result.user()).isEqualTo(activeUser());
        assertThat(result.newUser()).isFalse();
        assertThat(result.token())
                .isEqualTo(new IssuedToken("token-1001", 2_592_000L));
    }

    @Test
    void rejectsUnknownUsernameAndWrongPasswordWithSameError() {
        FakePasswordCredentialRepository repository =
                new FakePasswordCredentialRepository();
        FakePasswordHasher hasher = new FakePasswordHasher();
        PasswordAuthenticationService service =
                new PasswordAuthenticationServiceImpl(
                        repository, hasher, new FakeUserSession());

        assertUnauthorized(() -> service.login(
                new PasswordLoginCommand("missing", "wrong-password")));
        assertThat(hasher.matchCount).isEqualTo(1);

        repository.credential = new PasswordCredential(
                activeUser(), "encoded:correct-password");
        assertUnauthorized(() -> service.login(
                new PasswordLoginCommand("local.user", "wrong-password")));
    }

    @Test
    void rejectsInactivePasswordAccountBeforeCreatingSession() {
        FakePasswordCredentialRepository repository =
                new FakePasswordCredentialRepository();
        repository.credential = new PasswordCredential(
                new AuthUser(1001L, null, null, false),
                "encoded:correct-password");
        FakeUserSession session = new FakeUserSession();
        PasswordAuthenticationService service =
                new PasswordAuthenticationServiceImpl(
                        repository, new FakePasswordHasher(), session);

        assertThatThrownBy(() -> service.login(
                new PasswordLoginCommand(
                        "local.user", "correct-password")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(SecurityErrorCode.AUTH_FORBIDDEN));
        assertThat(session.loggedInUserId).isZero();
    }

    private void assertUnauthorized(ThrowingOperation operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(
                                        SecurityErrorCode.AUTH_UNAUTHORIZED));
    }

    private AuthUser activeUser() {
        return new AuthUser(1001L, "本地用户", null, true);
    }

    @FunctionalInterface
    private interface ThrowingOperation {

        void run();
    }

    private static final class FakePasswordCredentialRepository
            implements PasswordCredentialRepository {

        private PasswordCredential credential;
        private String lastUsername;

        @Override
        public Optional<PasswordCredential> findByUsername(String username) {
            lastUsername = username;
            return Optional.ofNullable(credential);
        }

        @Override
        public boolean existsByUsername(String username) {
            return credential != null;
        }

        @Override
        public AuthUser createAccount(
                String username, String passwordHash, String nickname) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakePasswordHasher implements PasswordHasher {

        private int matchCount;

        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(
                CharSequence rawPassword, String encodedPassword) {
            matchCount++;
            return encodedPassword.equals("encoded:" + rawPassword);
        }
    }

    private static final class FakeUserSession implements UserSession {

        private long loggedInUserId;

        @Override
        public IssuedToken login(long userId) {
            loggedInUserId = userId;
            return new IssuedToken("token-" + userId, 2_592_000L);
        }

        @Override
        public long currentUserId() {
            return loggedInUserId;
        }

        @Override
        public void logout() {
        }
    }
}
