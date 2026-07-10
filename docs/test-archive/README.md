# 测试归档（Test Archive）

本目录记录已从测试套件中删除的诊断型 / 冗余测试，便于后续追溯"曾经测试过什么、为什么删除、场景由谁接管"。

## 归档判定标准

依据 `AGENTS.md` 第 5 节"风险驱动测试"中的诊断型测试生命周期规则，一个测试可被归档删除，当且仅当：

1. 它是为复现 Bug 或验证修复而写的临时 / 诊断测试，**或**它与另一个保留测试存在冗余；
2. 它所覆盖的场景，已有**等价的永久回归守卫**接管（在本记录的"接管测试"一栏列出）；
3. 它**不属于** `AGENTS.md` 第 4 节强制要求的回归守卫——并发、距离限制、状态流转、权限、订单金额、库存、社区隐私、AI 安全边界——这些守卫无论是否通过一律保留。

## 操作流程

删除前必须：

1. 在本目录新建一条 `<YYYY-MM>-<原测试名>.md` 记录，填写下方模板；
2. 确认源文件无其它测试 / 生产代码引用（`grep -rl <类名> src/`）；
3. 运行 `mvn test-compile` 确认编译无断裂；
4. 才可 `git rm` 删除源文件。

## 归档记录模板

```text
# <原测试类名>

- 原路径：<src/test/.../*.java>
- 删除日期：YYYY-MM-DD
- 删除提交：<commit sha 或"待提交">
- 原行数 / 用例数：NNN 行 / N 个 @Test
- 原验证内容：<该测试覆盖了什么场景>
- 删除理由：<诊断 / 冗余 / 过期 / 其它>
- 接管测试：<保留的等价测试路径与类名>（或"无需——仅确认禁用桩"）
- 缺陷 / 提交 ID：<对应缺陷号或提交 sha，若无则"无">
```

## 已归档清单

| 日期 | 原测试 | 理由 | 接管测试 |
|------|--------|------|----------|
| 2026-07 | [BookingConcurrencyIT](./2026-07-BookingConcurrencyIT.md) | 过期 + 断言失效 | BookingConcurrencyMySqlIT |
| 2026-07 | [TcMySqlSchemaGateIT](./2026-07-TcMySqlSchemaGateIT.md) | 严格子集 | MySqlMapperIntegrationIT |
| 2026-07 | [DisabledAiProviderClientTest](./2026-07-DisabledAiProviderClientTest.md) | 同义反复 | 无需（禁用桩） |
| 2026-07 | [AiAnalysisApplicationServiceTest](./2026-07-AiAnalysisApplicationServiceTest.md) | 低价值 | 日期校验由其它服务测试覆盖 |
| 2026-07 | [MapperSmokeTest](./2026-07-MapperSmokeTest.md) | 重叠 | EntityMappingTest / MapperAndServiceCoverageTest / MySqlMapperIntegrationIT |
