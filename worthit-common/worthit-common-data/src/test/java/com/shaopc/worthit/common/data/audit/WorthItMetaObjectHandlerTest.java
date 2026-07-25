package com.shaopc.worthit.common.data.audit;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.OptionalLong;

import static org.assertj.core.api.Assertions.assertThat;

class WorthItMetaObjectHandlerTest {

    private static final Instant NOW = Instant.parse("2026-07-25T08:00:00Z");
    private static final LocalDateTime EXPECTED_TIME =
            LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    @BeforeAll
    static void initializeTableMetadata() {
        MapperBuilderAssistant assistant =
                new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AuditEntity.class);
    }

    @Test
    void fillsInsertAndUpdateAuditFieldsUsingFixedClockAndCurrentUser() {
        WorthItMetaObjectHandler handler = new WorthItMetaObjectHandler(
                Clock.fixed(NOW, ZoneOffset.UTC),
                () -> OptionalLong.of(1001L));
        AuditEntity inserted = new AuditEntity();
        AuditEntity updated = new AuditEntity();

        handler.insertFill(metaObject(inserted));
        handler.updateFill(metaObject(updated));

        assertThat(inserted.createTime).isEqualTo(EXPECTED_TIME);
        assertThat(inserted.updateTime).isEqualTo(EXPECTED_TIME);
        assertThat(inserted.createBy).isEqualTo(1001L);
        assertThat(inserted.updateBy).isEqualTo(1001L);
        assertThat(updated.updateTime).isEqualTo(EXPECTED_TIME);
        assertThat(updated.updateBy).isEqualTo(1001L);
    }

    @Test
    void leavesActorFieldsEmptyWhenNoCurrentUserExists() {
        WorthItMetaObjectHandler handler = new WorthItMetaObjectHandler(
                Clock.fixed(NOW, ZoneOffset.UTC),
                OptionalLong::empty);
        AuditEntity entity = new AuditEntity();

        handler.insertFill(metaObject(entity));

        assertThat(entity.createTime).isEqualTo(EXPECTED_TIME);
        assertThat(entity.updateTime).isEqualTo(EXPECTED_TIME);
        assertThat(entity.createBy).isNull();
        assertThat(entity.updateBy).isNull();
    }

    private static MetaObject metaObject(AuditEntity entity) {
        return SystemMetaObject.forObject(entity);
    }

    @TableName("audit_entity")
    private static final class AuditEntity {

        @TableId
        private Long id;

        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime createTime;

        @TableField(fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updateTime;

        @TableField(fill = FieldFill.INSERT)
        private Long createBy;

        @TableField(fill = FieldFill.INSERT_UPDATE)
        private Long updateBy;
    }
}
