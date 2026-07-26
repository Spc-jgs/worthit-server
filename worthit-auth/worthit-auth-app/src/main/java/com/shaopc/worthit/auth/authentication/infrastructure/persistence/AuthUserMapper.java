package com.shaopc.worthit.auth.authentication.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * Auth 用户表 Mapper。
 */
@Mapper
public interface AuthUserMapper extends BaseMapper<AuthUserDO> {
}
