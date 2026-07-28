package com.shaopc.worthit.tracking.idempotency.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.tracking.idempotency.application.RequestDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 以规范化命令 JSON 生成 Tracking 请求摘要。
 */
@Component
@RequiredArgsConstructor
public class JacksonRequestDigest implements RequestDigest {

    private final ObjectMapper objectMapper;

    @Override
    public String hash(Object command) {
        try {
            byte[] json = objectMapper.writeValueAsString(command)
                    .getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(json));
        } catch (JsonProcessingException
                 | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "无法生成Tracking请求摘要", exception);
        }
    }
}
