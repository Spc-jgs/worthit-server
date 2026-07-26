package com.shaopc.worthit.auth.authentication.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * {@code auth_user} 持久化对象。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("auth_user")
public class AuthUserDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String nickname;
    private Long avatarFileId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
