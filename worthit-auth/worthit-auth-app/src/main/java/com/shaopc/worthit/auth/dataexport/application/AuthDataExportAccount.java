package com.shaopc.worthit.auth.dataexport.application;

import java.time.Instant;

/** Auth 按数据所有权生成的账号导出分片。 */
public record AuthDataExportAccount(
        int schemaVersion,
        Instant capturedAt,
        String timeZone,
        String userId,
        Account account) {

    /** 不含凭证、外部身份和会话信息的账号字段。 */
    public record Account(
            String id,
            String nickname,
            String avatarFileId,
            String status,
            Instant createdAt,
            Instant updatedAt) {
    }
}
