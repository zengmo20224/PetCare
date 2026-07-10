# MapperSmokeTest

- 原路径：`src/test/java/com/petcare/common/persistence/MapperSmokeTest.java`
- 删除日期：2026-07-10
- 删除提交：待提交
- 原行数 / 用例数：119 行 / 4 个 @Test
- 原验证内容：
  - `contextLoads`：Spring 上下文加载 11 个 mapper Bean。
  - `userMapperInsertAndSelect`、`storeMapperInsertAndSelect`、`adminUserMapperInsertAndSelect`：三条手写插入 / 查询冒烟。
- 删除理由：重叠。
  - `contextLoads` 的"所有 mapper Bean 加载"语义被 `MapperAndServiceCoverageTest`（遍历每个实体类型，断言 `BaseMapper` 与 `IService` Bean 均加载）更严谨地覆盖。
  - 三条手写插入与 `EntityMappingTest`（更深映射：完整 booking 图、唯一约束、逻辑删除）以及 MySQL 集成测试的代表性行插入存在重叠。
- 接管测试：
  - mapper 加载覆盖 → `MapperAndServiceCoverageTest`。
  - 深度映射 → `EntityMappingTest`。
  - 真实库插入 → `MySqlMapperIntegrationIT`（代表性模块插入 + 唯一约束）。
- 缺陷 / 提交 ID：无（纯冗余清理）。
- 备注：若后续需要 H2 层的 user / store / adminUser 插入冒烟，可向 `EntityMappingTest` 追加用例，而非恢复本 smoke 测试。
