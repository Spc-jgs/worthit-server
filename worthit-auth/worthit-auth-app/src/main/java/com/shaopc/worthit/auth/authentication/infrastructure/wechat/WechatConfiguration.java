package com.shaopc.worthit.auth.authentication.infrastructure.wechat;

import com.shaopc.worthit.auth.authentication.application.port.WechatCodeExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * 微信小程序服务端 API 适配配置。
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WechatProperties.class)
public class WechatConfiguration {

    /**
     * 创建微信服务端 HTTP 客户端。
     *
     * @param builder    Spring Boot 管理的客户端构建器
     * @param properties 微信配置
     * @return 微信 RestClient
     */
    @Bean
    RestClient wechatRestClient(
            RestClient.Builder builder,
            WechatProperties properties) {
        return builder
                .baseUrl(properties.getBaseUrl().toString())
                .build();
    }

    /**
     * 创建微信登录 code 交换适配器。
     *
     * @param wechatRestClient 微信 HTTP 客户端
     * @param properties       微信配置
     * @return code 交换端口
     */
    @Bean
    WechatCodeExchange wechatCodeExchange(
            RestClient wechatRestClient,
            WechatProperties properties) {
        return new WechatCodeExchangeAdapter(
                wechatRestClient, properties);
    }
}
