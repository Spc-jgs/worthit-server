package com.shaopc.worthit.auth.authentication.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * {@code auth_password_credential} 持久化对象。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("auth_password_credential")
public class AuthPasswordCredentialDO {

    @TableId
    private Long userId;
    private String username;
    private String passwordHash;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
