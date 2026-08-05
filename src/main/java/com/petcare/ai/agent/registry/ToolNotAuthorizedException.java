package com.petcare.ai.agent.registry;

/**
 * Tool 调用未授权（未在白名单 / 用户未登录）。
 * <p>
 * 对标 docs/09 §6.2：任一校验失败抛此异常，不调 LLM。
 * 继承 {@link RuntimeException} 以不污染 Tool 接口签名（Tool.invoke 不抛 checked）。
 */
public class ToolNotAuthorizedException extends RuntimeException {
    public ToolNotAuthorizedException(String message) {
        super(message);
    }
}
