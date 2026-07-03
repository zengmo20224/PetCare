# PetCare O2O 全量回归测试报告

> 本报告由 QA 全量回归任务生成。**仅诊断，未修改任何业务代码、配置或 schema。**
> 任务分支：`qa/full-regression-2026-07`（基于 `wot-h5-integration`）

---

## 1. 元信息

| 项目 | 值 |
|---|---|
| 测试日期 | 2026-07-04 |
| 分支 | `qa/full-regression-2026-07` |
| 测试深度 | 自动化测试 + 构建 + API 级运行时验证（HTTP+JWT） |
| 运行环境 | Windows 10 (10.0.26200)，Git Bash |
| 工具链 | git 2.53.0 · Docker 29.5.2 / Compose v5.1.4 · Maven 3.9.11 (JDK 21) · Node v24.12.0 / npm 11.6.2 · curl 8.18.0 · MySQL 8.0.42 客户端 |
| 服务起法 | 复用**已在运行**的 compose 栈（`petcare-api`/`petcare-mysql`/`petcare-admin-web`/`petcare-h5`，均 healthy，已运行 15h+）。API 入口：`http://127.0.0.1:8082`。注意：该 API 镜像为历史构建，不完全等于当前分支代码——发现缺陷时会对照当前代码确认。 |
| 演示账号 | 用户 `13800138001` / `user123456`；管理员 `admin` / `admin123456` |

---

## 2. 测试矩阵总览

图例：✅ 通过 · ⚠️ 警告（非阻塞）· ❌ 缺陷

| 功能域 | 自动化测试 | 构建 | API 运行时 | 结论 |
|---|:---:|:---:|:---:|---|
| 后端单元（H2） | ✅ 857/857 | — | — | 健康 |
| 后端集成（MySQL/Testcontainers） | ❌ 3 IT 类失败 | — | — | **缺陷 D1**（测试与状态机变更不同步；非生产缺陷） |
| 后端打包 | — | ✅ jar 生成 | — | 健康 |
| Admin Web（Vue3） | ✅ 617/617 | ✅ | （未做 UI 走查） | 健康 |
| H5 / miniapp（UniApp） | ✅ 83/83 | ✅ | （未做 UI 走查） | ⚠️ `lint` 脚本失效（L1） |
| Auth 认证 | — | — | ✅ | 健康（见预期禁用项） |
| Profile/Pet/Address | — | — | ✅ | 健康（GET 单地址为设计缺口，见 D3 附注） |
| Booking 预约 | — | — | ✅ | 运行时功能正常；底层锁在 MySQL 单测失败（D1） |
| Product/Cart/Order | — | — | ✅ | 健康（金额/库存校验生效） |
| Community 社区 | — | — | ✅ | 健康 |
| Marketing/Notification | — | — | ✅ | 健康 |
| Admin RBAC/Ops | — | — | ✅ | 健康 |
| 错误处理（全局） | — | — | ❌ | **缺陷 D2**（不支持的 HTTP 方法返回 500） |
| 输入校验（注册） | — | — | ❌ | **缺陷 D3**（嵌套校验不级联 → NPE 500） |

**总体结论**：核心业务功能链路在运行时**可用**（78 项 API 验证，甄别后真实失败仅 3 类缺陷，均非阻断主流程）。后端 H2 单元测试全绿（857）；前端构建与测试全绿。主要问题：① Booking 的 tc-mysql 集成测试与"创建即已确认"状态机变更不同步（测试缺陷，非生产缺陷，但高风险规则未被有效测试）；② 全局异常处理对部分异常返回 500 而非语义码；③ 注册嵌套校验未级联。

---

## 3. 自动化测试结果

### 3.1 后端单元测试（H2） — ✅ 通过
```
mvn -B -ntp clean test
Tests run: 857, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS  (02:20 min)
JaCoCo: 310 classes analyzed
```
日志：`/tmp/mvn-test.log`

### 3.2 后端集成测试（Testcontainers MySQL 8.0.46） — ❌ 8 失败
```
mvn -B -ntp -P tc-mysql test
首次：Tests run: 18, Failures: 2, Errors: 6（8 失败，含 flaky "statement not found"）
重跑：Tests run: 18, Failures: 2, Errors: 2（4 失败，"statement not found" 消失，转为状态机断言失败）
```
失败用例（**确定性根因**：状态机断言过时，见缺陷 D1）：
| 测试类 | 结果 |
|---|---|
| `BookingConcurrencyMySqlIT` | `sameStaffAdjacentTime` 断言 `PENDING_CONFIRM` 但实际 `CONFIRMED` |
| `BookingStatusTransitionMySqlIT` | 1 failure + 1 error（状态转移前提崩塌，`CONFIRMED→CONFIRMED` 非法） |
| `BookingReassignMySqlIT` | 重跑后通过（首次报 "statement not found" 属 flaky） |

日志：`/tmp/mvn-tcmysql.log`

### 3.3 后端打包 — ✅ 通过
```
mvn -B -ntp clean package -DskipTests
BUILD SUCCESS → target/petcare-o2o-api-0.1.0-SNAPSHOT.jar
```

### 3.4 Admin Web — ✅ 通过
```
cd frontend/admin-web
npx vitest run        → 16 files / 617 tests passed
npm run build         → vue-tsc -b && vite build → dist/  (BUILD SUCCESS, 1.35s)
```
构建警告（非错误，不影响产物）：`@vueuse/core` 的 `/* #__PURE__ */` 注解位置告警；单 chunk >500kB。

### 3.5 H5 / miniapp — ✅ 通过（lint 除外）
```
cd frontend/miniapp
npm run typecheck     → 通过（无输出）
npm run test          → 13 files / 83 tests passed
npm run build:h5      → Build complete.  (仅 sass 弃用警告)
npm run lint          → ❌ 见 L1
```

---

## 4. API 级运行时验证明细

> 验证脚本：`tmp-qa-verify.sh`（临时文件，验证后清理，不提交）
> 原始日志：`/tmp/qa-api-verify.log`（78 项，PASS=63 / FAIL=15）
> 经逐条复核，15 项"FAIL"中 **12 项为脚本断言/字段名错误（产品功能正常）**，**3 项为真实缺陷（D1 已在集成测试体现，D2/D3 见下）**。

### 4.1 Auth 认证 ✅
- 用户登录（正常/错密码/缺字段）✅；未注册手机号返回 `401 invalid_credentials`（**安全设计**：不区分账号是否存在，防枚举）✅
- 注册新号 ✅；重复手机号返回 `422 phone_already_registered`（产品选用 422）✅
- 安全问题列表 / 忘记密码两步流 ✅
- **预期禁用项确认**：`POST /auth/wechat-login` → `422 wechat_login_not_enabled` ✅；`POST /auth/test-login`（非 test profile）→ `404` ✅

### 4.2 Profile / Pet / Address ✅
- 用户资料 GET/PUT ✅；无 token → `401` ✅
- 宠物列表/详情/越权：越权访问他人宠物 → `404 资源不存在`（**安全设计**：不泄露存在性）✅
- 地址：列表/新增/修改/删除端点齐全 ✅。注：`GET /user/addresses/{id}` **未实现**（设计上地址从列表获取），对该路径发请求触发 D2。

### 4.3 Booking 预约 ✅（运行时）
- 公开端点：可用时段查询、服务类目/项目、门店列表/详情 ✅
- 创建预约 → `201`，返回 bookingId ✅；缺字段 → `400` ✅
- 我的预约列表/详情 ✅
- 管理端状态流转：开始服务 → 完成 ✅（两步流转正常）
- ⚠️ 底层并发锁 `StaffBookingLockMapper` 在 MySQL 集成测试中不可用（D1），但**运行时 createBooking 仍成功**——推测运行时未触达该 mapper 的 databaseId 分支，或并发场景才会暴露。

### 4.4 Product / Cart / Order ✅
- 公开浏览：类目/轮播/商品列表/详情 ✅
- 购物车：列表/加入(`201`)/修改/删除 ✅
- 下单：正确入参（`contactName`/`contactPhone`/`deliveryMethod=PICKUP`/`storeId`）→ `201`，金额 `178.00` 服务端计算正确 ✅
- 校验生效：自提缺 `storeId` → `400 validDelivery: 自提需选择门店` ✅
- 管理端订单列表 ✅

### 4.5 Community 社区 ✅
- 帖子流/标签/热门标签/话题列表 ✅
- 发帖(`201`)/详情/点赞/取消点赞/收藏/取消收藏/评论(`201`)/评论列表/评论点赞 ✅
- 举报：正确字段 `reasonType` → 校验生效（脚本用错 `reason` 字段，非缺陷）✅
- 我的帖子/我点赞的/我收藏的 ✅
- 管理端：帖子审核列表/举报列表 ✅

### 4.6 Marketing / Notification / Moderation ✅
- 活动：列表（有数据）/详情（用真实 id）✅
- 公告列表、通知列表、通知未读数 ✅
- 管理端敏感词列表 ✅

### 4.7 Admin RBAC / Ops ✅
- `admin/auth/login` → `200` + token；`/me` ✅；无 token → `401` ✅
- 管理端全部 15 个列表/详情端点 → `200` ✅（门店/服务项/员工/商品/预约/自提订单/用户/活动/公告/操作日志/社区帖子/社区举报/敏感词）
- **管理端 AI 分析报告** `/admin/ai/analysis-reports` → `200`（功能可用，符合设计）✅
- **用户端 AI 禁用确认**：`POST /ai/conversations`（合法 body）→ `401 用户端 AI 功能暂未开放` ✅（与 `AiConversationController.java:94` 一致）

---

## 5. 缺陷清单（未修改代码，仅诊断）

### D1【高】Booking MySQL 集成测试与"创建即已确认"状态机变更不同步

> **根因修正说明**：首轮报告曾推测为"`databaseId` 未注入导致 mapper 语句未注册"。经深入诊断（临时探针实测 `Configuration.databaseId="mysql"`、`hasStatement(upsertStaffBookingLock)=true`），**该推测被推翻**。真实根因如下，确凿无疑。

- **现象**：`mvn -P tc-mysql test` 中 3 个 IT 类失败（首次全量跑 8 个、重跑 4 个；含 flaky 成分，见末尾）：
  ```
  BookingConcurrencyMySqlIT.sameStaffAdjacentTime_bothBookingsSucceed       → AssertionError
  BookingStatusTransitionMySqlIT.concurrentConfirmAndReject_onlyOneTransitionWins  → AssertionError
  BookingStatusTransitionMySqlIT.concurrentStartAndCancel_neverProducesInvalidFinalState → ERROR BusinessException
  ```
- **确定性根因**（断言失败信息直接印证）：
  ```
  expected: "PENDING_CONFIRM"
   but was: "CONFIRMED"
  BusinessException: 预约状态不允许从 CONFIRMED 变更为 CONFIRMED
  ```
  业务代码 commit `a5a8810`（"预约创建即已确认"）将 `createBooking` 的初始状态由 `PENDING_CONFIRM` 改为 `CONFIRMED`（`BookingTransactionServiceImpl`、`BookingStateMachine` 允许 `null→CONFIRMED`）。该 commit 的提交说明明确写道"后端 855 全量测试通过 / 更新状态机/创建/审计/状态流转测试适配新规则"——**但只更新了 H2 单测**（`BookingStateMachineTest`、`BookingAdminAuditTest` 等），**漏改了 tc-mysql profile 的 3 个 IT**。
- **为何漏改未被察觉**：`pom.xml` 的 surefire 配置 `<excludedGroups>tc-mysql</excludedGroups>` 使 `mvn test` **默认排除所有 `@Tag("tc-mysql")` 的 IT**。提交时的"全量测试"仅指 `mvn test`（855 个 H2 单测），从不跑这 3 个 IT，故状态机变更未触发它们的失败、漏网合入。
- **影响**：
  - IT 断言失效本身是测试缺陷（非生产代码缺陷）；`mvn test`（855）与运行时 API 验证均正常，业务功能可用。
  - 但这意味着**预约并发安全/状态转移一致性这些高风险规则（`docs/05-testing-and-verification.md` §2 强制要求"必须有直接测试"）当前实际未被有效测试覆盖**——这是测试有效性问题，属高风险。
- **复现**：`mvn -B -ntp -P tc-mysql test`（或单独 `-Dtest=BookingConcurrencyMySqlIT`）
- **诊断证据**（临时探针，已删除）：
  - `Configuration.databaseId = "mysql"`、`Provider.getDatabaseId(dataSource) = "mysql"`、`hasStatement(upsertStaffBookingLock) = true` → 证明 mapper 语句与 databaseId 均正常，**首轮"statement not found"非确定性根因**。
- **flaky 成分**：首次全量 `mvn -P tc-mysql test` 报 8 个 "Invalid bound statement (not found)"；单独跑 `BookingConcurrencyMySqlIT` 或重跑全量时，"not found" 消失，转为状态机断言失败。推测是 Testcontainers 容器在全量 IT 套件并发启动时的类加载/上下文竞争（非确定性，不作为根因，建议后续观察）。
- **修复方向（不改代码，仅建议）**：把这 3 个 IT 里 `PENDING_CONFIRM` 断言改为 `CONFIRMED`，并修正依赖 `PENDING_CONFIRM→CONFIRMED` 转移的并发测试前提（如 `concurrentConfirmAndReject` 需改为对已 `CONFIRMED` 的预约做并发 `start`/`complete`）。建议同时把 `mvn -P tc-mysql test` 纳入 CI（当前 `.github/workflows/ci.yml` 与 `Jenkinsfile` 均只跑 `mvn test`，从不跑 tc-mysql，这是根因能潜伏的流程缺口）。
- **位置**：
  - `src/test/java/com/petcare/booking/mapper/BookingConcurrencyMySqlIT.java:264`（`PENDING_CONFIRM` 断言）
  - `src/test/java/com/petcare/booking/mapper/BookingStatusTransitionMySqlIT.java`（状态转移前提）
  - `pom.xml:124-129`（`excludedGroups=tc-mysql`）
  - 业务变更源头：commit `a5a8810`

### D2【中】GlobalExceptionHandler 把 "Method Not Supported" 当成 500
- **现象**：对只注册了特定 HTTP 方法的路径发其他方法请求，返回 `500 internal_error`，而非标准 `405 Method Not Allowed`。
  - 例：`GET /api/v1/user/addresses/12001`（该路径仅 PUT/DELETE）→ `500`
  - 例：`GET /api/v1/ai/conversations`（该路径仅 POST）→ `500`
- **日志**：`org.springframework.web.HttpRequestMethodNotSupportedException: Request method 'GET' is not supported` → 被 `GlobalExceptionHandler` 归入 "Unexpected error" → 500
- **影响**：客户端无法区分"方法错"与"服务器崩"，且暴露给用户"服务内部错误"的误导文案；不符合 REST 规范。
- **优先级**：中
- **位置**：`src/main/java/com/petcare/common/exception/GlobalExceptionHandler.java`（需新增 `@ExceptionHandler(HttpRequestMethodNotSupportedException.class)` 返回 405）

### D3【中】注册接口嵌套校验未级联 → NPE 500
- **现象**：`POST /api/v1/auth/register`，当 `securityQuestions[].questionIndex` 为 `null` 时，返回 `500 internal_error`，而非 `400 validation_error`。
- **日志**：
  ```
  java.lang.NullPointerException: Cannot invoke "java.lang.Integer.intValue()" because
  the return value of "com.petcare.user.dto.RegisterRequest$SecurityQuestionItem.questionIndex()" is null
      at com.petcare.user.auth.UserAuthService.register(UserAuthService.java:73)
  ```
- **根因**：`RegisterRequest.securityQuestions` 字段（`List<SecurityQuestionItem>`）**未标注 `@Valid`**，导致 Bean Validation 不级联到集合元素；元素内的 `@NotNull` 失效，null 值进入 service 层触发 NPE。
- **修复方向（不改代码，仅建议）**：在 `RegisterRequest.securityQuestions` 上加 `@Valid`（并视情况加 `@NotEmpty`/`@Size`），使元素级校验生效返回 400。
- **优先级**：中（输入校验缺失 + 错误处理泄漏内部错误）
- **位置**：`src/main/java/com/petcare/user/dto/RegisterRequest.java:29`、`src/main/java/com/petcare/user/auth/UserAuthService.java:73`

### L1【低】miniapp `npm run lint` 调用全局 eslint 失败
- **现象**：`npm run lint`（`eslint src/`）报 `'eslint' is not recognized as an internal or external command`。
- **根因**：`package.json` 的 `lint` 脚本直接调用 `eslint`，依赖 PATH 中的全局安装；但 `eslint` 在 `devDependencies` 中（本地已装）。应改为 `eslint src/` → `npx eslint src/` 或 `vue-cli-service lint`。
- **影响**：CI 的 h5 job 未跑 lint（`.github/workflows/ci.yml` h5 步骤无 lint），故未被发现；本地开发者需全局装 eslint 才能跑。
- **优先级**：低
- **位置**：`frontend/miniapp/package.json`（`scripts.lint`）

---

## 6. 预期禁用/占位项确认（均符合 V1 边界，非缺陷）

| 项 | 预期 | 实测 | 结论 |
|---|---|---|---|
| 用户端 AI（对话/发帖助手） | 禁用，返回 401 | `POST /ai/conversations` 合法 body → `401 用户端 AI 功能暂未开放` | ✅ |
| 管理端 AI 分析/用量 | 功能可用 | `/admin/ai/analysis-reports` → `200` | ✅ |
| 微信登录 | 占位禁用 | `POST /auth/wechat-login` → `422 wechat_login_not_enabled` | ✅ |
| test-login（非 test profile） | 不可用 | `POST /auth/test-login` → `404` | ✅ |
| 微信小程序构建 | V1 延后 | 未测（仅 H5 构建 ✅） | — |

---

## 7. 交接与下一步建议

**已完成**：在 `qa/full-regression-2026-07` 分支完成全量回归（自动化测试 + 构建 + 78 项 API 运行时验证），产出本报告，未改任何业务代码。

**发现的真实缺陷（按优先级）**：
1. **D1（高，测试缺陷）**：Booking 的 tc-mysql 集成测试（3 个 IT 类）断言仍停留在旧的 `PENDING_CONFIRM`，与 commit `a5a8810`"预约创建即 CONFIRMED"不同步；因 `mvn test` 默认排除 tc-mysql 组而漏改。生产代码无缺陷，但高风险规则（预约并发/状态转移）实际未被有效测试。
2. **D2（中）**：全局异常处理对 `HttpRequestMethodNotSupportedException` 返回 500（应 405）。
3. **D3（中）**：注册 `securityQuestions` 未级联 `@Valid`，`questionIndex=null` 触发 NPE 500（应 400）。
4. **L1（低）**：miniapp `lint` 脚本调用全局 eslint。

**下一步建议**（需用户授权后另起任务修复，本任务不动代码）：
- D1：①把 3 个 IT 的 `PENDING_CONFIRM` 断言改为 `CONFIRMED`，并修正依赖该前提的并发测试逻辑；②把 `mvn -P tc-mysql test` 纳入 CI（当前 CI/Jenkins 只跑 `mvn test`，是根因潜伏的流程缺口）。
- D2：`GlobalExceptionHandler` 增补 `HttpRequestMethodNotSupportedException` → 405 处理（同时建议覆盖 `HttpMediaTypeNotSupportedException` 等）。
- D3：`RegisterRequest.securityQuestions` 加 `@Valid` + 集合级 `@NotEmpty/@Size`，先写失败测试再改。
- L1：`frontend/miniapp/package.json` 的 `lint` 改为 `npx eslint src/`，并把 lint 纳入 CI h5 job。

**验证证据**：本报告所有结论均有对应日志/HTTP 响应佐证（`/tmp/mvn-test.log`、`/tmp/mvn-tcmysql.log`、`/tmp/qa-api-verify.log`、`docker logs petcare-api`）。

**未完成/风险**：① 未做前端 UI 人工点击走查（本次方案不含）；② 运行中的 API 为历史镜像，但 D1 经诊断确定为**测试缺陷而非生产缺陷**（当前代码 `mvn test` 与运行时 API 验证均正常）；③ 首次全量 tc-mysql 跑出的 "statement not found" 属 flaky（重跑消失），未深挖，建议后续观察 Testcontainers 并发启动行为。
