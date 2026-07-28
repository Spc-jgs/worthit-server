package com.shaopc.worthit.reminder.app.reconcile.infrastructure.digest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.shaopc.worthit.reminder.client.command.ReconcileReminderCommand;
import com.shaopc.worthit.reminder.app.reconcile.application.ReminderPayloadDigest;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 以固定字段顺序 canonical JSON 生成 reconcile payload 摘要。
 */
@Component
public class JacksonReminderPayloadDigest
        implements ReminderPayloadDigest {

    private final ObjectMapper objectMapper;

    /**
     * 创建使用统一 ObjectMapper 的摘要实现。
     */
    public JacksonReminderPayloadDigest(
            ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String hash(ReconcileReminderCommand command) {
        ObjectNode canonical = objectMapper.createObjectNode();
        canonical.put(
                "businessType", command.businessType().name());
        canonical.put("businessId", command.businessId());
        canonical.put(
                "reminderType", command.reminderType().name());
        canonical.put("sourceVersion", command.sourceVersion());
        putNullable(
                canonical,
                "businessDate",
                command.businessDate() == null
                        ? null
                        : command.businessDate().toString());
        putNullable(
                canonical,
                "remindAt",
                command.remindAt() == null
                        ? null
                        : command.remindAt().toString());
        canonical.put(
                "reminderEnabled", command.reminderEnabled());
        canonical.put(
                "businessStatusCode",
                command.businessStatusCode());
        canonical.put(
                "operationType",
                command.operationType().name());
        canonical.put(
                "schemaVersion", command.schemaVersion());
        try {
            byte[] payload =
                    objectMapper.writeValueAsBytes(canonical);
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(payload));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Reminder请求摘要序列化失败", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前JDK不支持SHA-256", exception);
        }
    }

    private static void putNullable(
            ObjectNode node,
            String name,
            String value) {
        if (value == null) {
            node.putNull(name);
        } else {
            node.put(name, value);
        }
    }
}
