package com.petcare.user.auth;

import com.petcare.common.config.WechatProperties;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/**
 * Unit tests for {@link RealWechatLoginProvider} using {@link MockRestServiceServer}.
 * Verifies jscode2session parsing, errcode mapping, and that raw upstream bodies /
 * stack traces never leak into exception messages (AGENTS.md §4).
 */
class RealWechatLoginProviderTest {

    private static final String JSCODE2SESSION_URL =
            "https://api.weixin.qq.com/sns/jscode2session";

    // Provider sets baseUrl = JSCODE2SESSION_URL, so the actual request URI is the
    // full absolute URL. Match by startsWith so query params are ignored.

    private RestClient.Builder builder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
    }

    private WechatProperties props() {
        return new WechatProperties("real", "wx_test_appid", "wx_test_secret", 5000, 5000);
    }

    @Test
    @DisplayName("Successful response with openid + unionid is parsed correctly")
    void success_parsesOpenidAndUnionid() {
        RealWechatLoginProvider provider = new RealWechatLoginProvider(props(), builder);

        String body = """
                {"openid":"oABC123","session_key":"sk","unionid":"uXYZ"}
                """;
        server.expect(requestTo(startsWith(JSCODE2SESSION_URL)))
                .andExpect(method(org.springframework.http.HttpMethod.GET))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        WechatLoginResult result = provider.login("valid-code");

        assertEquals("oABC123", result.openid());
        assertEquals("uXYZ", result.unionid());
        assertNull(result.userId());
        server.verify();
    }

    @Test
    @DisplayName("Successful response without unionid yields null unionid")
    void success_withoutUnionid() {
        RealWechatLoginProvider provider = new RealWechatLoginProvider(props(), builder);

        String body = """
                {"openid":"oABC123","session_key":"sk"}
                """;
        server.expect(requestTo(startsWith(JSCODE2SESSION_URL)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        WechatLoginResult result = provider.login("valid-code");

        assertEquals("oABC123", result.openid());
        assertNull(result.unionid());
    }

    @Test
    @DisplayName("errcode != 0 throws WECHAT_JS_CODE_INVALID without leaking errmsg")
    void errcode_throwsInvalidCode_noBodyLeak() {
        RealWechatLoginProvider provider = new RealWechatLoginProvider(props(), builder);

        String body = """
                {"errcode":40029,"errmsg":"invalid code secret adjacent hint"}
                """;
        server.expect(requestTo(startsWith(JSCODE2SESSION_URL)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.login("bad-code"));

        assertEquals(ErrorCode.WECHAT_JS_CODE_INVALID, ex.getCode());
        // Raw upstream errmsg must NOT leak into the exception message.
        assertFalse(ex.getMessage().contains("invalid code secret adjacent hint"));
        assertFalse(ex.getMessage().contains("40029"));
        // Secret must never appear anywhere.
        assertFalse(ex.getMessage().contains("wx_test_secret"));
    }

    @Test
    @DisplayName("Upstream 5xx maps to WECHAT_UNAVAILABLE")
    void http5xx_mapsToUnavailable() {
        RealWechatLoginProvider provider = new RealWechatLoginProvider(props(), builder);

        server.expect(requestTo(startsWith(JSCODE2SESSION_URL)))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("upstream-boom")
                        .contentType(MediaType.APPLICATION_JSON));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.login("any-code"));

        assertEquals(ErrorCode.WECHAT_UNAVAILABLE, ex.getCode());
        assertFalse(ex.getMessage().contains("upstream-boom"));
    }

    @Test
    @DisplayName("Upstream 4xx maps to WECHAT_UNAVAILABLE")
    void http4xx_mapsToUnavailable() {
        RealWechatLoginProvider provider = new RealWechatLoginProvider(props(), builder);

        server.expect(requestTo(startsWith(JSCODE2SESSION_URL)))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("bad request body leak")
                        .contentType(MediaType.APPLICATION_JSON));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.login("any-code"));

        assertEquals(ErrorCode.WECHAT_UNAVAILABLE, ex.getCode());
        assertFalse(ex.getMessage().contains("bad request body leak"));
    }

    @Test
    @DisplayName("Blank code throws WECHAT_JS_CODE_INVALID without any HTTP call")
    void blankCode_throwsInvalidWithoutCall() {
        RealWechatLoginProvider provider = new RealWechatLoginProvider(props(), builder);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.login(""));

        assertEquals(ErrorCode.WECHAT_JS_CODE_INVALID, ex.getCode());
        // No server.expect set up — verify() would fail if any call had been made.
        server.verify();
    }

    @Test
    @DisplayName("Missing appid/secret maps to WECHAT_UNAVAILABLE without leaking secret")
    void missingConfig_throwsUnavailable() {
        WechatProperties badProps = new WechatProperties("real", "", "", null, null);
        RealWechatLoginProvider provider = new RealWechatLoginProvider(badProps, builder);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.login("some-code"));

        assertEquals(ErrorCode.WECHAT_UNAVAILABLE, ex.getCode());
        server.verify();
    }

    @Test
    @DisplayName("Malformed response body maps to WECHAT_UNAVAILABLE")
    void malformedBody_mapsToUnavailable() {
        RealWechatLoginProvider provider = new RealWechatLoginProvider(props(), builder);

        server.expect(requestTo(startsWith(JSCODE2SESSION_URL)))
                .andRespond(withSuccess("not json at all", MediaType.APPLICATION_JSON));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.login("any-code"));

        assertEquals(ErrorCode.WECHAT_UNAVAILABLE, ex.getCode());
    }

    @Test
    @DisplayName("Success response missing openid maps to WECHAT_UNAVAILABLE")
    void successWithoutOpenid_mapsToUnavailable() {
        RealWechatLoginProvider provider = new RealWechatLoginProvider(props(), builder);

        server.expect(requestTo(startsWith(JSCODE2SESSION_URL)))
                .andRespond(withSuccess("{\"openid\":\"\",\"session_key\":\"sk\"}", MediaType.APPLICATION_JSON));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.login("any-code"));

        assertEquals(ErrorCode.WECHAT_UNAVAILABLE, ex.getCode());
    }

    @Test
    @DisplayName("isEnabled returns true for the real provider")
    void isEnabled_returnsTrue() {
        RealWechatLoginProvider provider = new RealWechatLoginProvider(props(), builder);
        assertTrue(provider.isEnabled());
    }
}
