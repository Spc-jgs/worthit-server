package com.shaopc.worthit.auth.authentication.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Auth 外部身份表 Mapper。
 */
@Mapper
public interface AuthExternalIdentityMapper
        extends BaseMapper<AuthExternalIdentityDO> {
}
