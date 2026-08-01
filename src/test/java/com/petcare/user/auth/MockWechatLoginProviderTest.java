package com.petcare.user.auth;

import com.petcare.common.config.WechatProperties;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure unit tests for {@link MockWechatLoginProvider}.
 * Verifies deterministic openid derivation, prefix, enabled flag, and blank-code rejection.
 */
class MockWechatLoginProviderTest {

    private MockWechatLoginProvider provider;

    @BeforeEach
    void setUp() {
        provider = new MockWechatLoginProvider(new WechatProperties("mock", null, null, null, null));
    }

    @Test
    @DisplayName("Same code resolves to the same openid")
    void sameCode_yieldsSameOpenid() {
        WechatLoginResult first = provider.login("code-abc");
        WechatLoginResult second = provider.login("code-abc");

        assertEquals(first.openid(), second.openid());
        assertNotNull(first.openid());
    }

    @Test
    @DisplayName("Different codes resolve to different openids")
    void differentCodes_yieldDifferentOpenids() {
        WechatLoginResult a = provider.login("code-one");
        WechatLoginResult b = provider.login("code-two");

        assertNotEquals(a.openid(), b.openid());
    }

    @Test
    @DisplayName("Derived openid starts with mock_ prefix")
    void openid_startsWitMockPrefix() {
        WechatLoginResult result = provider.login("any-code");

        assertTrue(result.openid().startsWith("mock_"));
    }

    @Test
    @DisplayName("isEnabled returns true for the mock provider")
    void isEnabled_returnsTrue() {
        assertTrue(provider.isEnabled());
    }

    @Test
    @DisplayName("Blank code throws WECHAT_JS_CODE_INVALID")
    void blankCode_throwsInvalid() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.login(""));

        assertEquals(ErrorCode.WECHAT_JS_CODE_INVALID, ex.getCode());
    }

    @Test
    @DisplayName("Null code throws WECHAT_JS_CODE_INVALID")
    void nullCode_throwsInvalid() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> provider.login(null));

        assertEquals(ErrorCode.WECHAT_JS_CODE_INVALID, ex.getCode());
    }

    @Test
    @DisplayName("Result carries null unionid and userId — account creation is not the provider's job")
    void result_hasNullUnionidAndUserId() {
        WechatLoginResult result = provider.login("code-x");

        assertNull(result.unionid());
        assertNull(result.userId());
    }
}
