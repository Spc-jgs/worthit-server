package com.shaopc.worthit.common.http.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaopc.worthit.common.http.config.HttpClientTimeouts;
import com.shaopc.worthit.common.http.context.InternalRequestContext;
import com.shaopc.worthit.common.http.error.ApiResponseErrorHandler;
import com.shaopc.worthit.common.http.interceptor.InternalRequestHeadersInterceptor;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.util.Objects;

/**
 * 创建基于 Spring HTTP Interface 的阻塞式内部服务客户端。
 */
public final class HttpServiceClientFactory {

    private final ObjectMapper objectMapper;

    /**
     * 创建内部 HTTP 代理工厂。
     *
     * @param objectMapper 统一响应错误信封解码器
     */
    public HttpServiceClientFactory(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(
                objectMapper, "ObjectMapper不能为空");
    }

    /**
     * 创建指定 HTTP Interface 的代理。
     *
     * @param clientType     HTTP Interface 类型
     * @param targetService  目标服务名称
     * @param baseUrl        目标服务基础地址
     * @param builder        调用方提供的 RestClient Builder
     * @param timeouts       连接和读取超时
     * @param requestContext 可信内部请求上下文
     * @param <T>            HTTP Interface 类型
     * @return 可直接调用的 HTTP Interface 代理
     */
    public <T> T create(
            Class<T> clientType,
            String targetService,
            URI baseUrl,
            RestClient.Builder builder,
            HttpClientTimeouts timeouts,
            InternalRequestContext requestContext) {
        Objects.requireNonNull(clientType, "客户端类型不能为空");
        Objects.requireNonNull(baseUrl, "基础地址不能为空");
        Objects.requireNonNull(builder, "RestClient Builder不能为空");
        Objects.requireNonNull(timeouts, "HTTP超时配置不能为空");
        Objects.requireNonNull(requestContext, "内部请求上下文不能为空");

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(timeouts.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(timeouts.readTimeout());

        RestClient restClient = builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .requestInterceptor(
                        new InternalRequestHeadersInterceptor(requestContext))
                .defaultStatusHandler(
                        HttpStatusCode::isError,
                        new ApiResponseErrorHandler(objectMapper, targetService))
                .build();

        return HttpServiceProxyFactory
                .builderFor(RestClientAdapter.create(restClient))
                .build()
                .createClient(clientType);
    }
}
