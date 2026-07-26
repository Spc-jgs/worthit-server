package com.shaopc.worthit.auth.authentication.application;

import com.shaopc.worthit.auth.authentication.application.port.PasswordCredentialRepository;
import com.shaopc.worthit.auth.authentication.application.port.PasswordHasher;
import com.shaopc.worthit.auth.authentication.domain.AuthUser;
import com.shaopc.worthit.auth.authentication.domain.PasswordCredential;
import com.shaopc.worthit.auth.authentication.infrastructure.local.LocalAccountInitializer;
import com.shaopc.worthit.auth.authentication.infrastructure.local.LocalAccountProperties;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalAccountInitializerTest {

    @Test
    void createsConfiguredLocalAccountOnceWithEncodedPassword() {
        FakeRepository repository = new FakeRepository();
        LocalAccountProperties properties = new LocalAccountProperties();
        properties.setUsername(" Local.User ");
        properties.setPassword("local-password");
        properties.setNickname("本地测试用户");
        LocalAccountInitializer initializer = new LocalAccountInitializer(
                properties, repository, new FakePasswordHasher());

        initializer.run(null);
        initializer.run(null);

        assertThat(repository.createdCount).isEqualTo(1);
        assertThat(repository.username).isEqualTo("local.user");
        assertThat(repository.passwordHash)
                .isEqualTo("encoded:local-password");
        assertThat(repository.nickname).isEqualTo("本地测试用户");
    }

    @Test
    void failsFastWhenEnabledAccountHasNoPassword() {
        LocalAccountProperties properties = new LocalAccountProperties();
        properties.setUsername("local.user");

        LocalAccountInitializer initializer = new LocalAccountInitializer(
                properties, new FakeRepository(), new FakePasswordHasher());

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("本地测试账号必须配置用户名和密码");
    }

    @Test
    void rejectsLocalAccountThatCannotUsePasswordLoginContract() {
        LocalAccountProperties properties = new LocalAccountProperties();
        properties.setUsername("含空格 account");
        properties.setPassword("short");
        LocalAccountInitializer initializer = new LocalAccountInitializer(
                properties, new FakeRepository(), new FakePasswordHasher());

        assertThatThrownBy(() -> initializer.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("本地测试账号格式不合法");
    }

    private static final class FakeRepository
            implements PasswordCredentialRepository {

        private int createdCount;
        private String username;
        private String passwordHash;
        private String nickname;

        @Override
        public Optional<PasswordCredential> findByUsername(String username) {
            return Optional.empty();
        }

        @Override
        public boolean existsByUsername(String username) {
            return createdCount > 0;
        }

        @Override
        public AuthUser createAccount(
                String username, String passwordHash, String nickname) {
            createdCount++;
            this.username = username;
            this.passwordHash = passwordHash;
            this.nickname = nickname;
            return new AuthUser(1001L, nickname, null, true);
        }
    }

    private static final class FakePasswordHasher implements PasswordHasher {

        @Override
        public String encode(CharSequence rawPassword) {
            return "encoded:" + rawPassword;
        }

        @Override
        public boolean matches(
                CharSequence rawPassword, String encodedPassword) {
            return encodedPassword.equals("encoded:" + rawPassword);
        }
    }
}
