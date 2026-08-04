package com.shaopc.worthit.reminder.app.dataexport.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 按 user_id、主键升序、有界读取 Reminder 导出数据。
 */
@Mapper
public interface ReminderDataExportMapper {

    @Select("""
            SELECT id, business_type, business_id, reminder_type,
                   reminder_enabled, create_time, update_time
              FROM rem_binding
             WHERE user_id = #{userId}
             ORDER BY id ASC
             LIMIT #{limit}
            """)
    List<ReminderDataExportRows.BindingRow> selectBindings(
            @Param("userId") long userId, @Param("limit") int limit);

    @Select("""
            SELECT id, binding_id, business_date, remind_at, timezone, status,
                   resolved_at, resolution_reason, create_time, update_time
              FROM rem_instance
             WHERE user_id = #{userId}
             ORDER BY id ASC
             LIMIT #{limit}
            """)
    List<ReminderDataExportRows.InstanceRow> selectInstances(
            @Param("userId") long userId, @Param("limit") int limit);
}
