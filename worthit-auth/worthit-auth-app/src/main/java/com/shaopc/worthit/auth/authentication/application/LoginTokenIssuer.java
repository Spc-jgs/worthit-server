package com.shaopc.worthit.auth.authentication.application;

import com.shaopc.worthit.auth.authentication.application.port.AuthUserRepository;
import com.shaopc.worthit.auth.authentication.application.port.IssuedToken;
import com.shaopc.worthit.auth.authentication.application.port.UserSession;
import com.shaopc.worthit.auth.authentication.domain.AuthUser;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.security.error.SecurityErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在用户行锁保护下检查账号状态并签发会话。 */
@Service
@RequiredArgsConstructor
public class LoginTokenIssuer {

    private final AuthUserRepository userRepository;
    private final UserSession userSession;

    /** 与注销 claim 共享 auth_user 行锁，不允许执行态漏发 Token。 */
    @Transactional
    public AuthenticationResult issue(long userId, boolean newUser) {
        AuthUser user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(
                        SecurityErrorCode.AUTH_FORBIDDEN));
        if (!user.active()) {
            throw new BusinessException(SecurityErrorCode.AUTH_FORBIDDEN);
        }
        IssuedToken token = userSession.login(user.id());
        return new AuthenticationResult(token, user, newUser);
    }
}
