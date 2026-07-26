package com.shaopc.worthit.auth.authentication.infrastructure.local;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 仅用于 local-infra 环境的测试账号配置。
 */
@Getter
@Setter
@NoArgsConstructor
@ConfigurationProperties("worthit.auth.local-account")
public class LocalAccountProperties {

    private String username;
    private String password;
    private String nickname = "本地测试用户";
}
