package com.shaopc.worthit.auth.authentication.application;

import com.shaopc.worthit.auth.authentication.application.port.AuthUserRepository;
import com.shaopc.worthit.auth.authentication.domain.WechatIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 在单个本地事务中完成微信身份首次用户建档。
 */
@Service
@RequiredArgsConstructor
public class WechatUserRegistrationService {

    private final AuthUserRepository userRepository;

    /**
     * 查找已有用户，或原子创建用户与外部身份。
     *
     * @param identity 已验证微信身份
     * @return 用户注册结果
     */
    @Transactional
    public UserRegistration register(WechatIdentity identity) {
        return userRepository
                .findByWechatIdentity(
                        identity.appId(), identity.externalSubject())
                .map(user -> new UserRegistration(user, false))
                .orElseGet(() -> new UserRegistration(
                        userRepository.createWechatUser(identity), true));
    }
}
