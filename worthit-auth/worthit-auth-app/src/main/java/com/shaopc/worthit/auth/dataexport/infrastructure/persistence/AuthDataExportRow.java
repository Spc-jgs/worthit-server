package com.shaopc.worthit.auth.dataexport.infrastructure.persistence;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 仅包含允许进入账号导出分片的 auth_user 字段。 */
@Getter
@Setter
public final class AuthDataExportRow {

    private Long id;
    private String nickname;
    private Long avatarFileId;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
