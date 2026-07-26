package com.shaopc.worthit.auth.authentication.application;

import com.shaopc.worthit.auth.authentication.domain.AuthUser;
import com.shaopc.worthit.auth.authentication.domain.WechatIdentity;
import com.shaopc.worthit.auth.authentication.application.port.AuthUserRepository;
import com.shaopc.worthit.auth.authentication.application.port.IssuedToken;
import com.shaopc.worthit.auth.authentication.application.port.UserSession;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.error.SecurityErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AuthenticationServiceTest {

    private static final WechatIdentity WECHAT_IDENTITY =
            new WechatIdentity("wx-app", "openid-001", "union-001");

    @Test
    void createsUserAndIssuesTokenOnFirstWechatLogin() {
        FakeAuthUserRepository repository = new FakeAuthUserRepository();
        FakeUserSession session = new FakeUserSession();
        AuthenticationService service = service(repository, session);

        AuthenticationResult result =
                service.login(new WechatLoginCommand("login-code"));

        assertThat(result.newUser()).isTrue();
        assertThat(result.user().id()).isEqualTo(1001L);
        assertThat(result.token())
                .isEqualTo(new IssuedToken("token-1001", 2_592_000L));
        assertThat(repository.createdUsers).isEqualTo(1);
        assertThat(session.loggedInUserId).isEqualTo(1001L);
    }

    @Test
    void reusesExistingUserOnLaterWechatLogin() {
        FakeAuthUserRepository repository = new FakeAuthUserRepository();
        repository.saveExisting(activeUser());
        FakeUserSession session = new FakeUserSession();
        AuthenticationService service = service(repository, session);

        AuthenticationResult result =
                service.login(new WechatLoginCommand("login-code"));

        assertThat(result.newUser()).isFalse();
        assertThat(result.user()).isEqualTo(activeUser());
        assertThat(repository.createdUsers).isZero();
    }

    @Test
    void reloadsWinnerWhenConcurrentFirstLoginHitsUniqueConstraint() {
        FakeAuthUserRepository repository = new FakeAuthUserRepository();
        repository.duplicateOnCreate = true;
        AuthenticationService service =
                service(repository, new FakeUserSession());

        AuthenticationResult result =
                service.login(new WechatLoginCommand("login-code"));

        assertThat(result.newUser()).isFalse();
        assertThat(result.user()).isEqualTo(activeUser());
        assertThat(repository.createdUsers).isEqualTo(1);
    }

    @Test
    void rejectsInactiveUserBeforeCreatingSession() {
        FakeAuthUserRepository repository = new FakeAuthUserRepository();
        repository.saveExisting(new AuthUser(1001L, null, null, false));
        FakeUserSession session = new FakeUserSession();
        AuthenticationService service = service(repository, session);

        assertThatThrownBy(() ->
                service.login(new WechatLoginCommand("login-code")))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(SecurityErrorCode.AUTH_FORBIDDEN));
        assertThat(session.loggedInUserId).isZero();
    }

    @Test
    void returnsCurrentUserAndLogsOutCurrentSession() {
        FakeAuthUserRepository repository = new FakeAuthUserRepository();
        repository.saveExisting(activeUser());
        FakeUserSession session = new FakeUserSession();
        session.loggedInUserId = 1001L;
        AuthenticationService service = service(repository, session);

        assertThat(service.currentUser()).isEqualTo(activeUser());

        service.logout();

        assertThat(session.logoutCount).isEqualTo(1);
    }

    private AuthenticationService service(
            FakeAuthUserRepository repository,
            FakeUserSession session) {
        return new AuthenticationService(
                code -> WECHAT_IDENTITY,
                new WechatUserRegistrationService(repository),
                repository,
                session);
    }

    private AuthUser activeUser() {
        return new AuthUser(1001L, null, null, true);
    }

    private final class FakeAuthUserRepository implements AuthUserRepository {

        private final Map<Long, AuthUser> users = new HashMap<>();
        private AuthUser identityUser;
        private int createdUsers;
        private boolean duplicateOnCreate;

        @Override
        public Optional<AuthUser> findByWechatIdentity(
                String appId, String externalSubject) {
            return Optional.ofNullable(identityUser);
        }

        @Override
        public Optional<AuthUser> findById(long userId) {
            return Optional.ofNullable(users.get(userId));
        }

        @Override
        public AuthUser createWechatUser(WechatIdentity identity) {
            createdUsers++;
            if (duplicateOnCreate) {
                saveExisting(activeUser());
                throw new DuplicateKeyException("并发身份唯一键冲突");
            }
            AuthUser user = activeUser();
            saveExisting(user);
            return user;
        }

        private void saveExisting(AuthUser user) {
            identityUser = user;
            users.put(user.id(), user);
        }
    }

    private static final class FakeUserSession implements UserSession {

        private long loggedInUserId;
        private int logoutCount;

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
            logoutCount++;
        }
    }
}
