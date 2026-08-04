package com.shaopc.worthit.auth.dataexport.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Auth 账号导出的显式字段白名单查询。 */
@Mapper
public interface AuthDataExportMapper {

    /** 只读取当前用户及冻结契约允许的六个字段。 */
    @Select("""
            SELECT id, nickname, avatar_file_id, status, create_time, update_time
              FROM auth_user
             WHERE id = #{userId}
            """)
    AuthDataExportRow selectAccount(@Param("userId") long userId);
}
