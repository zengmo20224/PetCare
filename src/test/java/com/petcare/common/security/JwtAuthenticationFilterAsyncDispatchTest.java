package com.petcare.common.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 守卫：JWT 过滤器必须在 ASYNC dispatch 时重跑（2026-08-15 验收缺陷）。
 * <p>
 * 背景：Spring MVC 完成异步请求（SseEmitter 等）时会发起 ASYNC dispatch；
 * Spring Security 6 的 AuthorizationFilter 默认在 dispatch 上重新授权，
 * 而 OncePerRequestFilter 默认跳过 ASYNC dispatch——若 JWT 过滤器不重跑，
 * SecurityContext 为空，授权被拒（AccessDenied）且响应已提交无法收尾，
 * Tomcat 直接掐断连接（SSE 无 chunked 终止块），前端流式 reader 永远挂起。
 */
class JwtAuthenticationFilterAsyncDispatchTest {

    @Test
    @DisplayName("shouldNotFilterAsyncDispatch 必须为 false（ASYNC dispatch 时重建 SecurityContext）")
    void jwtFilterMustRunOnAsyncDispatch() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                null, null, null, null, null);
        Method m = OncePerRequestFilterForTest.shouldNotFilterAsyncDispatch();
        boolean skip = (boolean) m.invoke(filter);
        assertFalse(skip, "JwtAuthenticationFilter 必须覆写 shouldNotFilterAsyncDispatch()=false，"
                + "否则 SSE 等异步端点在完成 dispatch 时被 AuthorizationFilter 拒绝，连接被掐断");
    }

    /** 反射桥：protected 方法无法直接访问。 */
    private static final class OncePerRequestFilterForTest {
        static Method shouldNotFilterAsyncDispatch() throws NoSuchMethodException {
            Method m = org.springframework.web.filter.OncePerRequestFilter.class
                    .getDeclaredMethod("shouldNotFilterAsyncDispatch");
            m.setAccessible(true);
            return m;
        }
    }
}
