# TcMySqlSchemaGateIT

- 原路径：`src/test/java/com/petcare/common/persistence/TcMySqlSchemaGateIT.java`
- 删除日期：2026-07-10
- 删除提交：待提交
- 原行数 / 用例数：67 行 / 3 个 @Test
- 原验证内容：
  - `connectsToMySql8`：MySQL 版本检查（`SELECT VERSION()`）。
  - `everyMapperCanCountAgainstRealMysql`：遍历所有 `BaseMapper` Bean，验证每个 mapper 在真实 MySQL 上可 `selectCount`。
  - `userMapperInsertAndSelectOnMysql`：单条 user 插入 + 查询冒烟。
- 删除理由：严格子集。上述三个用例全部被 `MySqlMapperIntegrationIT` 覆盖，且后者覆盖更广——还包含各模块代表性插入 / 查询与唯一约束校验。本测试不提供任何独立覆盖。
- 接管测试：`src/test/java/com/petcare/common/persistence/MySqlMapperIntegrationIT.java`（同样 Testcontainers MySQL 8 + `@Tag("tc-mysql")`）。
- 缺陷 / 提交 ID：无（纯冗余清理）。
