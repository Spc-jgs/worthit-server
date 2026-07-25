package com.shaopc.worthit.common.http.error;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Objects;

/**
 * 将有界的统一响应信封解码为稳定远端服务异常。
 */
public final class ApiResponseErrorHandler
        implements RestClient.ResponseSpec.ErrorHandler {

    static final int MAX_ERROR_BODY_BYTES = 65_536;
    private static final String FALLBACK_CODE = "REMOTE_HTTP_ERROR";
    private static final String FALLBACK_MESSAGE = "远端服务请求失败";

    private final ObjectMapper objectMapper;
    private final String targetService;

    /**
     * 创建统一响应错误处理器。
     *
     * @param objectMapper  JSON 解码器
     * @param targetService 目标服务名称
     */
    public ApiResponseErrorHandler(ObjectMapper objectMapper, String targetService) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper不能为空");
        this.targetService = requireText(targetService, "目标服务名称");
    }

    @Override
    public void handle(HttpRequest request, ClientHttpResponse response)
            throws IOException {
        int statusCode = response.getStatusCode().value();
        DecodedError decodedError = decode(response);
        throw new RemoteServiceException(
                targetService,
                statusCode,
                decodedError.code(),
                decodedError.traceId(),
                decodedError.message());
    }

    private DecodedError decode(ClientHttpResponse response) throws IOException {
        byte[] body = response.getBody().readNBytes(MAX_ERROR_BODY_BYTES + 1);
        if (body.length == 0 || body.length > MAX_ERROR_BODY_BYTES) {
            return DecodedError.fallback();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            if (root == null || !root.isObject()) {
                return DecodedError.fallback();
            }
            return new DecodedError(
                    text(root, "code", FALLBACK_CODE),
                    text(root, "message", FALLBACK_MESSAGE),
                    optionalText(root, "traceId"));
        } catch (IOException | RuntimeException ignored) {
            return DecodedError.fallback();
        }
    }

    private static String text(JsonNode root, String field, String fallback) {
        String value = optionalText(root, field);
        return value == null ? fallback : value;
    }

    private static String optionalText(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isTextual() || node.textValue().isBlank()) {
            return null;
        }
        return node.textValue();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "不能为空");
        }
        return value;
    }

    private record DecodedError(String code, String message, String traceId) {

        private static DecodedError fallback() {
            return new DecodedError(FALLBACK_CODE, FALLBACK_MESSAGE, null);
        }
    }
}
