package com.shaopc.worthit.auth.authentication.infrastructure.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.shaopc.worthit.auth.authentication.application.port.AuthUserRepository;
import com.shaopc.worthit.auth.authentication.domain.AuthUser;
import com.shaopc.worthit.auth.authentication.domain.WechatIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 使用 MyBatis-Plus 持久化 Auth 用户与微信身份。
 */
@Repository
@RequiredArgsConstructor
public class MybatisAuthUserRepository implements AuthUserRepository {

    /** 微信小程序身份类型稳定值。 */
    private static final String WECHAT_MINI = "WECHAT_MINI";

    /** 可登录账号状态稳定值。 */
    private static final String ACTIVE = "ACTIVE";

    private final AuthUserMapper userMapper;
    private final AuthExternalIdentityMapper identityMapper;
    private final Clock clock;

    @Override
    public Optional<AuthUser> findByWechatIdentity(
            String appId, String externalSubject) {
        AuthExternalIdentityDO identity = identityMapper.selectOne(
                Wrappers.<AuthExternalIdentityDO>lambdaQuery()
                        .eq(AuthExternalIdentityDO::getIdentityType,
                                WECHAT_MINI)
                        .eq(AuthExternalIdentityDO::getAppId, appId)
                        .eq(AuthExternalIdentityDO::getExternalSubject,
                                externalSubject));
        if (identity == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(userMapper.selectById(
                        identity.getUserId()))
                .map(this::toDomain);
    }

    @Override
    public Optional<AuthUser> findById(long userId) {
        return Optional.ofNullable(userMapper.selectById(userId))
                .map(this::toDomain);
    }

    @Override
    public AuthUser createWechatUser(WechatIdentity identity) {
        LocalDateTime now = LocalDateTime.now(clock);
        AuthUserDO user = new AuthUserDO();
        user.setStatus(ACTIVE);
        user.setCreateTime(now);
        user.setUpdateTime(now);
        userMapper.insert(user);

        AuthExternalIdentityDO externalIdentity =
                new AuthExternalIdentityDO();
        externalIdentity.setUserId(user.getId());
        externalIdentity.setIdentityType(WECHAT_MINI);
        externalIdentity.setAppId(identity.appId());
        externalIdentity.setExternalSubject(
                identity.externalSubject());
        externalIdentity.setUnionId(identity.unionId());
        externalIdentity.setVerified(true);
        externalIdentity.setCreateTime(now);
        externalIdentity.setUpdateTime(now);
        identityMapper.insert(externalIdentity);
        return toDomain(user);
    }

    private AuthUser toDomain(AuthUserDO user) {
        return new AuthUser(
                user.getId(),
                user.getNickname(),
                null,
                ACTIVE.equals(user.getStatus()));
    }
}
