package com.shaopc.worthit.auth.authentication.infrastructure.wechat;

import com.shaopc.worthit.auth.authentication.domain.WechatIdentity;
import com.shaopc.worthit.common.core.error.BusinessException;
import com.shaopc.worthit.common.web.error.CommonWebErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WechatCodeExchangeAdapterTest {

    private MockRestServiceServer server;
    private WechatCodeExchangeAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        WechatProperties properties = new WechatProperties(
                "wx-app", "test-secret", URI.create("https://wechat.test"));
        adapter = new WechatCodeExchangeAdapter(
                builder.baseUrl(properties.getBaseUrl().toString()).build(),
                properties);
    }

    @Test
    void exchangesCodeForWechatIdentityWithoutRetainingSessionKey() {
        server.expect(requestTo(
                        "https://wechat.test/sns/jscode2session"
                                + "?appid=wx-app"
                                + "&secret=test-secret"
                                + "&js_code=login-code"
                                + "&grant_type=authorization_code"))
                .andRespond(withSuccess("""
                        {
                          "openid":"openid-001",
                          "unionid":"union-001",
                          "session_key":"must-not-be-retained"
                        }
                        """, MediaType.APPLICATION_JSON));

        WechatIdentity identity = adapter.exchange("login-code");

        assertThat(identity)
                .isEqualTo(new WechatIdentity(
                        "wx-app", "openid-001", "union-001"));
        server.verify();
    }

    @Test
    void mapsRejectedWechatCodeToExistingValidationErrorCode() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString(
                        "/sns/jscode2session")))
                .andRespond(withSuccess("""
                        {"errcode":40029,"errmsg":"invalid code"}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter.exchange("bad-code"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(CommonWebErrorCode
                                        .VAL_INVALID_ARGUMENT));
    }

    @Test
    void hidesWechatTransportFailureBehindUpstreamErrorCode() {
        server.expect(requestTo(org.hamcrest.Matchers.containsString(
                        "/sns/jscode2session")))
                .andRespond(withServerError());

        assertThatThrownBy(() -> adapter.exchange("login-code"))
                .isInstanceOfSatisfying(BusinessException.class, exception ->
                        assertThat(exception.errorCode())
                                .isEqualTo(CommonWebErrorCode.SYS_UPSTREAM));
    }
}
