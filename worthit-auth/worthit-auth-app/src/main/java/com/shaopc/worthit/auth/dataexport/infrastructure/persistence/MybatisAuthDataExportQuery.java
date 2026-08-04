package com.shaopc.worthit.auth.dataexport.infrastructure.persistence;

import com.shaopc.worthit.auth.dataexport.application.AuthDataExportAccount;
import com.shaopc.worthit.auth.dataexport.application.AuthDataExportQuery;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

/** 基于 Auth 自有数据库实现的账号导出查询。 */
@Repository
public class MybatisAuthDataExportQuery implements AuthDataExportQuery {

    private static final String TIME_ZONE = "Asia/Shanghai";

    private final AuthDataExportMapper mapper;
    private final Clock clock;

    /** 创建账号导出查询。 */
    public MybatisAuthDataExportQuery(
            AuthDataExportMapper mapper,
            Clock clock) {
        this.mapper = Objects.requireNonNull(mapper);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AuthDataExportAccount exportAccount(long userId) {
        AuthDataExportRow row = mapper.selectAccount(userId);
        if (row == null) {
            throw new BusinessException(CommonWebErrorCode.RES_NOT_FOUND);
        }
        return new AuthDataExportAccount(
                1,
                clock.instant(),
                TIME_ZONE,
                Long.toString(userId),
                new AuthDataExportAccount.Account(
                        Long.toString(row.getId()),
                        row.getNickname(),
                        nullableId(row.getAvatarFileId()),
                        row.getStatus(),
                        toInstant(row.getCreateTime()),
                        toInstant(row.getUpdateTime())));
    }

    private Instant toInstant(LocalDateTime value) {
        return value.atZone(clock.getZone()).toInstant();
    }

    private static String nullableId(Long value) {
        return value == null ? null : Long.toString(value);
    }
}
