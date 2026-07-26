package com.shaopc.worthit.auth.authentication.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账号密码凭证 Mapper。
 */
@Mapper
public interface AuthPasswordCredentialMapper
        extends BaseMapper<AuthPasswordCredentialDO> {
}
