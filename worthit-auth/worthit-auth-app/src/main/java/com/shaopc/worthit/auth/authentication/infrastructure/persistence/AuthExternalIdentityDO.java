package com.shaopc.worthit.auth.authentication.infrastructure.persistence;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * {@code auth_external_identity} 持久化对象。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("auth_external_identity")
public class AuthExternalIdentityDO {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String identityType;
    private String appId;
    private String externalSubject;
    private String unionId;
    private Boolean verified;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
