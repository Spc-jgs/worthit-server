package com.shaopc.worthit.auth.authentication.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Auth 用户表 Mapper。
 */
@Mapper
public interface AuthUserMapper extends BaseMapper<AuthUserDO> {

    /** 以与注销 claim 一致的用户行锁读取用户。 */
    @Select("""
            SELECT id, nickname, avatar_file_id, status,
                   create_time, update_time
              FROM auth_user
             WHERE id = #{userId}
               FOR UPDATE
            """)
    AuthUserDO selectByIdForUpdate(@Param("userId") long userId);
}
