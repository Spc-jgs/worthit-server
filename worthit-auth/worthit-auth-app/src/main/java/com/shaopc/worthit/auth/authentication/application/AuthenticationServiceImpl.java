package com.shaopc.worthit.auth.authentication.application;

import com.shaopc.worthit.auth.authentication.application.port.AuthUserRepository;
import com.shaopc.worthit.auth.authentication.application.port.UserSession;
import com.shaopc.worthit.auth.authentication.application.port.WechatCodeExchange;
import com.shaopc.worthit.auth.authentication.domain.AuthUser;
import com.shaopc.worthit.auth.authentication.domain.WechatIdentity;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.error.SecurityErrorCode;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 编排微信登录、当前用户查询和登出用例。
 */
@Service
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    private final WechatCodeExchange wechatCodeExchange;
    private final WechatUserRegistrationService registrationService;
    private final AuthUserRepository userRepository;
    private final UserSession userSession;
    private final LoginTokenIssuer tokenIssuer;

    /**
     * 使用微信一次性 code 登录。
     *
     * @param command 微信登录命令
     * @return 登录结果
     */
    @Override
    public AuthenticationResult login(WechatLoginCommand command) {
        WechatIdentity identity = wechatCodeExchange.exchange(command.code());
        UserRegistration registration = registerOrReload(identity);
        return tokenIssuer.issue(
                registration.user().id(), registration.newUser());
    }

    /**
     * 查询当前登录用户。
     *
     * @return 当前用户
     */
    @Override
    public AuthUser currentUser() {
        long userId = userSession.currentUserId();
        AuthUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(
                        CommonWebErrorCode.RES_NOT_FOUND));
        ensureActive(user);
        return user;
    }

    /**
     * 注销当前登录态。
     */
    @Override
    public void logout() {
        userSession.logout();
    }

    /**
     * 通过数据库唯一约束收敛微信首次并发登录，满足 TECH-SEC-005：同一微信身份只能创建一个用户。
     */
    private UserRegistration registerOrReload(WechatIdentity identity) {
        try {
            return registrationService.register(identity);
        } catch (DuplicateKeyException exception) {
            AuthUser winner = userRepository
                    .findByWechatIdentity(
                            identity.appId(), identity.externalSubject())
                    .orElseThrow(() -> exception);
            return new UserRegistration(winner, false);
        }
    }

    private void ensureActive(AuthUser user) {
        if (!user.active()) {
            throw new BusinessException(SecurityErrorCode.AUTH_FORBIDDEN);
        }
    }
}
