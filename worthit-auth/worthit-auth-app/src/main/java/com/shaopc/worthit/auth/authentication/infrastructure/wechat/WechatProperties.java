package com.shaopc.worthit.auth.authentication.infrastructure.wechat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.net.URI;

/**
 * 微信小程序服务端凭证配置。
 *
 * <p>该类型刻意不生成 {@code toString}，避免 AppSecret 进入日志。</p>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Validated
@ConfigurationProperties(prefix = "worthit.auth.wechat")
public class WechatProperties {

    @NotBlank
    private String appId;

    @NotBlank
    private String appSecret;

    @NotNull
    private URI baseUrl = URI.create("https://api.weixin.qq.com");
}
