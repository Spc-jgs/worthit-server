package com.shaopc.worthit.common.data.audit;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * 使用统一时钟和当前操作者填充 MyBatis-Plus 审计字段。
 */
public final class WorthItMetaObjectHandler implements MetaObjectHandler {

    private final Clock clock;
    private final CurrentAuditor currentAuditor;

    /**
     * 创建审计字段填充器。
     *
     * @param clock          生成审计时间的时钟
     * @param currentAuditor 当前操作者提供器
     */
    public WorthItMetaObjectHandler(Clock clock, CurrentAuditor currentAuditor) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.currentAuditor = Objects.requireNonNull(currentAuditor, "currentAuditor");
    }

    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now(clock);
        strictInsertFill(metaObject, "createTime", LocalDateTime.class, now);
        strictInsertFill(metaObject, "updateTime", LocalDateTime.class, now);

        OptionalLong currentUserId = currentAuditor.currentUserId();
        if (currentUserId.isPresent()) {
            Long userId = currentUserId.getAsLong();
            strictInsertFill(metaObject, "createBy", Long.class, userId);
            strictInsertFill(metaObject, "updateBy", Long.class, userId);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime now = LocalDateTime.now(clock);
        strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, now);

        OptionalLong currentUserId = currentAuditor.currentUserId();
        if (currentUserId.isPresent()) {
            strictUpdateFill(
                    metaObject, "updateBy", Long.class, currentUserId.getAsLong());
        }
    }
}
