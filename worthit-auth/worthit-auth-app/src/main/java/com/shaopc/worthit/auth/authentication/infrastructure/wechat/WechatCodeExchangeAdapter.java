package com.shaopc.worthit.auth.authentication.infrastructure.wechat;

import com.shaopc.worthit.auth.authentication.application.port.WechatCodeExchange;
import com.shaopc.worthit.auth.authentication.domain.WechatIdentity;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 调用微信 {@code jscode2session} 接口交换小程序身份。
 */
@RequiredArgsConstructor
public class WechatCodeExchangeAdapter implements WechatCodeExchange {

    private static final String AUTHORIZATION_CODE =
            "authorization_code";

    private final RestClient restClient;
    private final WechatProperties properties;

    @Override
    public WechatIdentity exchange(String code) {
        WechatSessionResponse response;
        try {
            response = restClient
                    .get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/sns/jscode2session")
                            .queryParam("appid", properties.getAppId())
                            .queryParam(
                                    "secret",
                                    properties.getAppSecret())
                            .queryParam("js_code", code)
                            .queryParam(
                                    "grant_type",
                                    AUTHORIZATION_CODE)
                            .build())
                    .retrieve()
                    .body(WechatSessionResponse.class);
        } catch (RestClientException exception) {
            throw new BusinessException(
                    CommonWebErrorCode.SYS_UPSTREAM,
                    CommonWebErrorCode.SYS_UPSTREAM.defaultMessage(),
                    exception);
        }
        if (response == null) {
            throw new BusinessException(
                    CommonWebErrorCode.SYS_UPSTREAM);
        }
        if (response.errcode() != null
                && response.errcode() != 0) {
            throw new BusinessException(
                    CommonWebErrorCode.VAL_INVALID_ARGUMENT,
                    "微信登录凭证无效");
        }
        if (response.openid() == null
                || response.openid().isBlank()) {
            throw new BusinessException(
                    CommonWebErrorCode.SYS_UPSTREAM);
        }
        return new WechatIdentity(
                properties.getAppId(),
                response.openid(),
                response.unionid());
    }

    /**
     * 微信登录响应只映射身份与错误字段，不保留 session_key。
     */
    private record WechatSessionResponse(
            String openid,
            String unionid,
            Integer errcode) {
    }
}
