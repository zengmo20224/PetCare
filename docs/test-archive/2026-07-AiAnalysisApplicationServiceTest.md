# AiAnalysisApplicationServiceTest

- 原路径：`src/test/java/com/petcare/ai/service/AiAnalysisApplicationServiceTest.java`
- 删除日期：2026-07-10
- 删除提交：待提交
- 原行数 / 用例数：140 行 / 4 个 @Test
- 原验证内容：
  - `AiAnalysisApplicationServiceImpl`（管理端业务分析报告生成）的成功路径、provider 失败路径、日期范围校验（`startAfterEnd`、`futureEndDate`）。
- 删除理由：低价值。该服务不触达医疗安全（`AGENTS.md` 第 4 节 AI 安全边界不适用），唯一的真实业务逻辑是日期范围校验；且本测试从未覆盖安全策略分支 `AiOutputSafetyPolicy.isUnsafe()`（SUT 第 118 行），安全编排路径实际未被测。在 AI 保持禁用的现状下，剩余价值仅剩日期校验与编排管道。
- 接管测试：日期范围校验由其它服务测试中的通用校验用例覆盖；provider 失败编排路径由 `AiConversationApplicationServiceTest` / `AiPostAssistantServiceTest` 中的 `providerUnavailable` 用例覆盖。
- 缺陷 / 提交 ID：无。
- 备注：删除前已确认 `MockAiProviderClient`（共享 mock fixture）仍被 `AiConversationApplicationServiceTest` 与 `AiPostAssistantServiceTest` 引用，故 mock 本身保留。
