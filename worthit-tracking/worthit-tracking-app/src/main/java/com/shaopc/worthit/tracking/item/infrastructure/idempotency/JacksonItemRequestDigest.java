package com.shaopc.worthit.tracking.item.infrastructure.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.tracking.item.application.CreateItemCommand;
import com.shaopc.worthit.tracking.item.application.DeleteItemCommand;
import com.shaopc.worthit.tracking.item.application.ItemRequestDigest;
import com.shaopc.worthit.tracking.item.application.UpdateItemCommand;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 以规范化命令 JSON 生成 Item 请求摘要。
 */
@Component
@RequiredArgsConstructor
public class JacksonItemRequestDigest
        implements ItemRequestDigest {

    private final ObjectMapper objectMapper;

    @Override
    public String hash(CreateItemCommand command) {
        return hashValue(command);
    }

    @Override
    public String hash(UpdateItemCommand command) {
        return hashValue(command);
    }

    @Override
    public String hash(DeleteItemCommand command) {
        return hashValue(command);
    }

    private String hashValue(Object command) {
        try {
            byte[] json = objectMapper.writeValueAsString(command)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(json));
        } catch (JsonProcessingException
                 | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "无法生成Item请求摘要", exception);
        }
    }
}
