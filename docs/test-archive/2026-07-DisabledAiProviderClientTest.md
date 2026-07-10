# DisabledAiProviderClientTest

- 原路径：`src/test/java/com/petcare/ai/provider/DisabledAiProviderClientTest.java`
- 删除日期：2026-07-10
- 删除提交：待提交
- 原行数 / 用例数：49 行 / 2 个 @Test
- 原验证内容：
  - 验证 `DisabledAiProviderClient`（生产占位桩）调用时抛 `AiProviderUnavailableException`。
  - 验证异常消息不包含 "API"、"key"、"http" 等敏感词。
- 删除理由：同义反复。被测对象 `DisabledAiProviderClient` 全部内容是 4 行 `throw new AiProviderUnavailableException("AI 服务暂未启用")`——测试等于在确认"禁用的桩确实是禁用的"，异常消息检查也只是对一段硬编码中文字符串的字面匹配，不提供回归价值。AI 禁用状态本身已由 `AiProviderArchitectureTest`（架构边界守卫）和各 service 测试中的 provider-unavailable 路径覆盖。
- 接管测试：无需——仅确认禁用桩。架构边界由 `AiProviderArchitectureTest` 守卫。
- 缺陷 / 提交 ID：无。
- 备注：生产类 `src/main/java/com/petcare/ai/provider/DisabledAiProviderClient.java` **未删除**，仅删除其测试。
