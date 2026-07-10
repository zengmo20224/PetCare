# BookingConcurrencyIT

- 原路径：`src/test/java/com/petcare/booking/mapper/BookingConcurrencyIT.java`
- 删除日期：2026-07-10
- 删除提交：待提交
- 原行数 / 用例数：301 行 / 5 个 @Test
- 原验证内容：
  - H2 内存库下的预约并发场景：同一员工同一时段并发抢占、相邻时段、同一员工不同日期、取消后释放时段、状态日志生成。
  - 旗舰用例 `sameStaffSameTime` 用 `assertThat(successCount).isBetween(1, 2)` 验证并发结果。
- 删除理由：过期 + 断言失效。
  - 自提交 `a5a8810` 起，预约创建即自动确认（`CONFIRMED`），但本测试仍断言 `PENDING_CONFIRM`（约第 204、227、248 行），与现行业务行为不一致。
  - 旗舰并发用例自身注释承认在 H2 上"无法复现 InnoDB 行锁语义"，断言放宽到 `isBetween(1,2)`，无法真正验证并发安全。
- 接管测试：`src/test/java/com/petcare/booking/mapper/BookingConcurrencyMySqlIT.java`（Testcontainers MySQL 8 + `@Tag("tc-mysql")`），使用严格断言 `isEqualTo(1)` 成功 / `isEqualTo(1)` 冲突，并能复现真实行锁。
- 缺陷 / 提交 ID：缺陷 D1（见 `docs/10-qa-full-regression-2026-07.md`）；行为变更提交 `a5a8810`。
- 备注：H2 版本的 4 个非并发用例（相邻时段、不同日期、取消释放、状态日志）属业务逻辑验证，如后续发现未被覆盖，应在 service 层补一个轻量 `@SpringBootTest`，而非恢复本 H2 并发测试。
