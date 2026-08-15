# 测试与验证

## 1. 策略

项目采用风险驱动验证，不再要求每个任务执行相同的全量流程。

- Bug 修复和高风险业务规则：优先先写失败测试，再实现。
- 普通功能：至少补充能防止核心回归的测试。
- UI、文档和配置：按实际风险运行构建、页面检查或引用检查。
- 关键变更逻辑目标覆盖率不低于 80%；全仓覆盖率作为趋势指标，不作为每个任务的统一阻塞门禁。

## 2. 必须直接测试的规则

- 认证、授权、越权访问和公开隐私。
- 预约距离、容量、并发、幂等和状态流转。
- 商品价格、库存、订单金额、事务和状态流转。
- 社区发布、互动权限和审核。
- 管理端高风险操作。

## 3. 常用验证命令

### 后端

优先运行受影响测试，发布前运行全量测试：

```powershell
mvn test
```

### 管理端

```powershell
cd frontend/admin-web
npm run test
npm run build
```

### 用户 H5

```powershell
cd frontend/miniapp
npm run typecheck
npm run test
npm run build:h5
```

### Git 差异

```powershell
git diff --check
git status --short --branch
```

## 4. 纵向切片验收

每个纵向切片至少验证：

1. 正常用户流程。
2. 一个关键失败路径。
3. 权限或数据隔离。
4. 对应前端构建。
5. 对应后端测试。

商品订单、预约、社区互动和营销发布必须提供端到端可重复验证。发布前再运行跨模块回归和关键 E2E。

## 5. 文档任务

只修改文档时，至少检查：

- 核心文档引用不存在失效路径。
- 新定位和路线图没有互相冲突。
- `git diff --check` 通过。
- 最终 Git 状态未覆盖无关变更。

文档任务无需运行无关应用全量测试，但如果文档声明某个构建能力，应尽量执行对应构建验证。

## 6. 完成声明

只有在相关验证实际通过后，才能说明功能已完成或可用。未运行的测试、环境限制和已知风险必须明确列出。

## 7. 测试分层与质量说明

本节说明测试体系的分层结构，用于澄清"测试代码量接近业务代码量"是否等于"凑覆盖率"。

### 7.1 三层测试结构

| 层 | 形式 | 数量级 | 作用 |
|---|---|---|---|
| 纯 Mockito 单元 | 不起 Spring 容器，mock 依赖，验证业务规则 | 10+ 测试类 | 快速验证 Service 的分支逻辑、异常路径、状态流转 |
| `@SpringBootTest` 集成（H2） | 起完整容器，用 H2 内存库 | 占主体（2026-08 全量回归约 1067 个测试） | 验证 Mapper、事务、缓存、Web 层、配置绑定的真实行为 |
| `@Tag("tc-mysql")` 真实 MySQL | 用 Testcontainers 起 MySQL 8 容器 | 2026-08 回归 33 个测试 | 在真实数据库上验证并发、幂等、锁、状态机的安全性 |

`mvn test` 默认跑前两层（H2）；`mvn -P tc-mysql test` 才跑第三层。CI 当前只跑前两层，第三层需本地或专门阶段触发。

### 7.2 高价值测试（答辩重点）

这些测试直接覆盖 §2 列出的高风险规则，是"测试不是凑数"的硬证据：

- **`ProductIdempotencyConcurrencyIT`**：并发重复提交订单，验证幂等键保证只生成一单。
- **`BookingConcurrencyMySqlIT`**：同一员工相邻时段并发预约，验证不会超卖时段。
- **`ProductInventoryConcurrencyIT`**：并发下单，验证库存原子扣减不会超卖。
- **`BookingStatusTransitionTransactionTest`**：预约状态非法流转被拒绝，事务回滚。
- **`AddressTransactionRollbackTest` / `BookingStatusLogRollbackTest`**：用 `@SpyBean` 注入故障，验证事务真实回滚。
- **`AdminManagementAuditTest`**：用 ArgumentCaptor 验证管理操作的审计日志字段正确。

### 7.3 结构性契约测试（少量，有防回归价值）

以下 4 个测试不验证业务规则，而是验证"代码结构契约"，占比 <5%，不构成水分：

| 测试 | 作用 |
|---|---|
| `PetCareApplicationTest` | 验证 Spring 上下文能启动（标准样板） |
| `MapperAndServiceCoverageTest` | 反射扫描，断言每个 `@TableName` 实体都有 Mapper bean 和 IService bean，防止漏注册 |
| `EntitySchemaMappingContractTest` | 反射比对实体字段与 DDL 列，防止漏标 `@TableField`/`@TableLogic` |
| `MySqlTestProfileContractTest` | 验证 test profile 配置正确 |

### 7.4 测试代码量说明

测试代码与业务代码行数接近（约 1:1），原因是 §2 列出的每个高风险规则都有独立测试，而非通过覆盖率堆砌：

- 并发场景需要多线程编排代码（latch、executor、await），代码量天然较大。
- 事务回滚测试需要 `@SpyBean` 注入故障 + 断言中间状态。
- 状态机测试需要覆盖每个合法/非法转移路径。
- Web 层测试需要 MockMvc + jsonPath 断言完整响应结构。

如需区分"业务测试"与"结构性测试"，参考 §7.2（高价值业务测试）和 §7.3（结构性契约测试）的分类。