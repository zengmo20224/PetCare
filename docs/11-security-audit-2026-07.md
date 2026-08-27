# 安全审计报告 2026-07

> 日期：2026-07-04 ｜ 类型：授权白盒安全审计 ｜ 状态：**2 高危 + 5 中危全部已修复，全量测试通过**
>
> 范围：31 个 Controller、447 个 Java 源文件、前端、构建配置、密钥泄露面
> 方式：静态白盒审计（4 个专项并行扫描 + 人工复核关键项），未做任何破坏性操作

---

## 1. 总体结论

PetCare O2O 的安全基线在课程项目中处于**较高水平**。SQL 注入面、IDOR 越权面、金额/库存/预约并发均已正确防护。本次审计发现的 2 个高危与 5 个中危问题，集中在**业务幂等、限流、文件校验、信息泄露、依赖版本**维度，不涉及已正确实现的核心安全机制。

| 类别 | 评价 |
|---|---|
| SQL 注入 | ✅ 全部参数化，无一处 `${}` 拼接 |
| IDOR / 越权 | ✅ 所有用户资源都做归属校验 |
| 金额/价格 | ✅ 服务端计算，客户端无法注入 |
| 库存 | ✅ `WHERE stock >= ?` 原子扣减，不会负库存 |
| 预约并发 | ✅ `SELECT … FOR UPDATE` 行级悲观锁 |
| 状态机 | ✅ 全部状态流转受锁保护 |
| JWT | ✅ tokenType 隔离 + 失败必 401 不降级 |
| 封禁用户 | ✅ 下一个请求即失效 |
| 错误回显 | ✅ GlobalExceptionHandler 不泄露堆栈/SQL |

---

## 2. 漏洞清单

### 2.1 高危（HIGH）

#### H1. 订单创建无幂等保护 → 双击/重放可重复下单、超卖
- **位置**：`ProductOrderCreateRequest.java`（无幂等字段）、`ProductOrderTransactionServiceImpl.java:82-205`
- **漏洞**：`createOrder` 在一个 `@Transactional` 内扣库存 + 插单 + 删购物车，但**没有幂等键**。唯一约束 `uk_order_no` 只防订单号碰撞，不去重业务操作。
- **PoC**：
  ```http
  POST /api/v1/product-orders
  Authorization: Bearer <user-jwt>
  Idempotency-Key: (缺失)
  ```
  连发两个相同请求 → 各扣一份库存、各生成一个订单号。并发同秒提交时两请求都看到 `checked=1` 行 → **超卖**。
- **违反**：AGENTS.md 第 4 条"订单金额、库存…必须使用事务并由服务端校验"的并发维度。
- **修复方案**：`product_order` 表加 `idempotency_key VARCHAR(64)` + `UNIQUE(user_id, idempotency_key)`；客户端通过 `Idempotency-Key` 请求头传入 UUID；服务端先查现存订单，命中则直接返回，`DuplicateKeyException` 作为竞态兜底。

#### H2. 全站无任何限流 / 登录失败锁定 → 在线暴力破解
- **位置**：`SecurityConfig.java:62-69`（permitAll 端点）、`UserAuthService.login`、`AdminAuthServiceImpl.login`、`UserAuthService.resetPassword`
- **漏洞**：登录只做一次 BCrypt 比对就发 token，**无失败计数、无锁定、无 IP/账号限流**。`pom.xml` 无 bucket4j/resilience4j/Redis。
- **PoC**：
  ```bash
  for pw in admin123 admin123456 password; do
    curl -s -X POST http://api:8082/api/v1/admin/auth/login \
      -H 'Content-Type: application/json' \
      -d "{\"username\":\"admin\",\"password\":\"$pw\"}"
  done
  ```
  无限次尝试，配合 M2 用户枚举可批量破解。
- **修复方案**：新增 `RateLimitFilter`（`OncePerRequestFilter` + `ConcurrentHashMap` 滑动窗口），仅匹配登录/注册/找回密码端点，超阈值返回 429。

### 2.2 中危（MEDIUM）

#### M1. 文件上传：扩展名由客户端 `Content-Type` 决定，无魔数校验
- **位置**：`FileUploadController.java:44-48`
- **漏洞**：`extension = ALLOWED_EXTENSIONS.get(file.getContentType())` —— `Content-Type` 是 HTTP 头，完全由攻击者控制。
- **PoC**：上传含 `<script>` 的 SVG/HTML 内容，伪造 `Content-Type: image/jpeg` → 被存为 `xxxxx.jpg` 但字节是恶意脚本，浏览器 MIME sniff 可执行（存储型 XSS）。
- **修复方案**：读前 12 字节做魔数比对（JPEG `FFD8FF`、PNG `89504E47`、GIF `47494638`、WebP `52494646`）；nginx 给 `/uploads/` 加 `X-Content-Type-Options: nosniff`。

#### M2. 用户枚举：找回密码端点区分"未注册"
- **位置**：`UserAuthService.java:160-162, 188-190`
- **漏洞**：注册号返回安全问题，未注册号返回"该手机号未注册"——`permitAll` 端点可被用来枚举有效手机号。
- **PoC**：`POST /api/v1/auth/forgot-password/questions` 批量探测手机号列表 → 构建注册账号清单 → 配合 H2 暴力破解。
- **修复方案**：未注册手机号返回**固定假问题列表**（与注册号同结构），仅 `resetPassword` 时失败；错误信息改为通用"手机号或安全问题不正确"。

#### M3. 公告内容 `v-html` 渲染 + 写入侧无 HTML 净化 → 存储型 XSS
- **位置**：`frontend/miniapp/src/pages/announcement/detail.vue:20`、`AdminAnnouncementController.java`、`AdminAnnouncementRequest.java:11`（仅 `@NotBlank`）
- **漏洞**：`<view v-html="item.content" />` 直接渲染服务端内容，写入时不做任何 HTML 净化。
- **PoC**：
  ```http
  POST /api/v1/admin/announcements
  Authorization: Bearer <admin-jwt>
  { "content":"<img src=x onerror=alert(document.cookie)>" }
  ```
  所有打开公告详情的用户都会在其会话中执行 JS。降为中危是因写入需管理员权限。
- **修复方案**：双层防御——写入侧用 HTML Sanitizer 净化；渲染侧 `v-html` → `{{ }}` 文本插值。

#### M4. RBAC 角色模型断裂：`hasRole('SUPER_ADMIN')` 永远为假
- **位置**：`AdminPrincipal.java:27-30`、`AdminAnnouncementController.java:39,48,57,70`
- **漏洞**：`AdminPrincipal` 只把**权限码**作为 authority，角色从未注入为 `ROLE_<role>` authority。结果 `hasRole('SUPER_ADMIN')` 永远 `false`。
- **当前影响**：`AdminAnnouncementController` 写的是 `or` 条件，靠 `hasAuthority('system:config')` 兜底，**暂时不可提权**——但角色权限模型本质是坏的。任何未来仅靠 `hasRole('SUPER_ADMIN')` 的端点会要么全拒要么全放。
- **修复方案**：`AdminPrincipal` 构造时同时注入 `ROLE_<role>` authority（与权限码并存）。

#### M5. Spring Boot 3.3.6 存在 CVE-2025-22235
- **位置**：`pom.xml:10`
- **漏洞**：3.3.6 处于 CVE-2025-22235（EndpointRequest.to 访问控制绕过，影响 ≤3.3.10）范围内。本项目未暴露 actuator（仅 health），可利用性低，但版本落后。
- **修复方案**：升级到 `3.3.11+`。

### 2.3 低危/信息（LOW / INFO）

| # | 位置 | 问题 |
|---|---|---|
| L1 | `docker-compose.yml:18,22,40,63` | DB 密码默认 `changeme` fallback |
| L2 | `data-dev.sql:172-177` | 种子 `admin/admin123456` 超管，密码明文写在注释，compose 自动挂载 |
| L3 | `application.yml:18` + `SecurityProperties.java:37-47` | `JWT_SECRET` 默认空，`validate()` 仅 `log.warn` 不抛异常 |
| L4 | `application-dev.yml:10` | 开发态 JWT secret 硬编码在仓库 |
| L5 | `UpdateUserProfileRequest.java:10-26` | `idCardNo`/`avatarUrl` 等无 `@Pattern`/URL 白名单 |
| L6 | `AdminManagementServiceImpl.java:512-514`, `ProductCatalogApplicationServiceImpl.java:78-80` | LIKE 关键字未转义 `%`/`_`（非注入，可全表匹配枚举） |
| L7 | `FileUploadController.java:27` | 控制器 10MB 限制 vs Spring 默认 1MB 不一致 |
| L8 | `ProductOrderTransactionServiceImpl.java:363` | 订单号 `时间戳+4位随机`，并发下碰撞触发 500 |
| L9 | `frontend/miniapp/.env.*` | 硬编码内网 LAN IP |
| L10 | `nginx/*.conf` | 无全局安全头（CSP/X-Frame-Options 等） |

> 低危项不在本次修复切片范围，记录备查。

---

## 3. 已确认安全的项（无需处理）

- **SQL 注入**：所有 XML mapper 用 `#{}`，所有 `@Select/@Update` 用 `#{}`，无 `.last()`/`.apply()` 拼接，无用户可控 `sort/order/column` 参数。
- **路径穿越**：`FileUploadController` 文件名用 UUID 重命名，用户原文件名从不参与路径构造。
- **JWT tokenType 混淆 / alg:none**：`parseToken` 用 jjwt 0.12+ `verifyWith` 对称密钥，拒绝无签名/异算法；filter 按类型分流 + `instanceof` 校验。
- **JWT 降级匿名**：原 `docs/02` 记录的旧 bug 已修——Bearer token 出现但失败必走 `rejectInvalidToken` → 401。
- **IDOR**：Pet/Address/Order/Booking/Cart/Post/Comment/Notification 全部用 `SecurityContextHelper.getCurrentUserId()` 作用域化查询。
- **库存负数**：`deductStock` 用 `WHERE stock >= #{quantity}` 原子保护。
- **金额**：全部从 `product.getPrice()`（DB）服务端计算，请求 DTO 不含价格字段。
- **预约并发**：`staff_booking_lock` 唯一约束 + `SELECT...FOR UPDATE` 悲观锁。
- **状态机**：订单/预约所有状态流转都在 `selectForUpdate` 锁下行做。
- **错误回显**：GlobalExceptionHandler 不暴露 `e.getMessage()`、堆栈、SQL。
- **测试后门**：`TestLoginController/Service` 均 `@Profile("test")`，dev/prod 404。
- **前端依赖**：Vue3.5 / Vite8 / Element Plus 2.14 / UniApp 均较新，无已知严重 CVE。

---

## 4. 修复计划

| 优先级 | 项 | 方案 | 状态 |
|---|---|---|---|
| P0 | H1 订单幂等 | DB 唯一约束 + `Idempotency-Key` 请求头 + 并发兜底（DuplicateKey 回查+重试） | ✅ 已修复 |
| P0 | H2 限流 | `RateLimitFilter` 自实现内存限流（滑动窗口，按 IP+端点） | ✅ 已修复 |
| P1 | M1 文件上传 | 魔数校验（JPEG/PNG/GIF/WebP）+ nginx nosniff + multipart 配置 | ✅ 已修复 |
| P1 | M2 用户枚举 | 找回密码响应同质化（占位问题列表） | ✅ 已修复 |
| P1 | M3 公告 XSS | 写入侧 HTML 转义（ContentSanitizer）+ 渲染侧文本插值 | ✅ 已修复 |
| P2 | M4 RBAC role | `AdminPrincipal` 注入 `ROLE_<role>` authority | ✅ 已修复 |
| P2 | M5 Spring Boot | 升级 3.3.6 → 3.3.11 | ✅ 已修复 |

### 验证结果（2026-07-04）

| 测试套件 | 结果 |
|---|---|
| `mvn test`（H2 内存库全量） | ✅ **898 测试通过，0 失败** |
| `mvn test -Ptc-mysql`（Testcontainers MySQL 8 IT） | ✅ **20 测试通过，0 失败** |

新增测试覆盖（相对基线 843）：
- H1：4 个单元测试（幂等命中/未命中/并发兜底/向后兼容）+ 2 个 MySQL IT（并发不超卖、串行幂等）
- H2：9 个限流单元测试（阈值/不同 IP/不同端点/管理员/XFF/forgot-password 归一化）
- M1：11 个魔数校验单元测试（JPEG/PNG/GIF/WebP/SVG 伪造/HTML 伪造/类型错配/空输入）
- M3：7 个净化单元测试（img onerror/script/svg onload/正常文本/引号/null）
- M4：6 个 AdminPrincipal 单元测试（ROLE_ 注入/权限码共存/hasRole 求值/null 过滤）

每个修复切片遵循 AGENTS.md §5 风险驱动测试：先写失败测试，再实现，再 `mvn test` 全量验证。

---

## 5. 审计方法说明

本次审计为**授权白盒安全评估**（ defensive security audit），针对项目自身的代码与配置，目标是发现漏洞以便修复加固。所有"注入测试"均以本地受控验证和静态代码分析为准，未对外发起真实攻击流量，未植入持久化后门，未破坏数据。

---

# 上线前部署加固 2026-08

> 日期：2026-08-15 ｜ 类型：上线前部署配置审计（4 路并行：密钥与 git 历史 / 后端 / 前端 / 部署配置） ｜ 状态：**8 项阻塞清单已修复 7 项，剩余 2 项为部署时动作**
>
> 核心结论：git 全历史（所有分支）无任何真实密钥泄露；后端代码无严重/高危（越权、注入、SSRF、XSS、资金事务四路复核通过）。阻塞项集中在**部署配置**维度。

## 6. 总体结论

| 类别 | 评价 |
|---|---|
| 密钥泄露 | ✅ git 全历史无真实密钥；DeepSeek key / DB 密码 / JWT secret 仅在本机 `.env`（已 gitignore） |
| 后端代码 | ✅ 无严重/高危；低危待排期：改密后旧 JWT 最长 120 分钟仍有效、订单/AI 会话分页 size 未钳制、AI 对话原文进 INFO 日志 |
| token 存储 | ✅ 已迁移 HttpOnly Cookie 双轨（2026-08-15，`21ea438`）：Cookie 优先 + Bearer 回退（小程序运行时无 cookie，header 通道永久保留） |
| 上传接口 | ✅ `UploadRateLimitFilter` 分钟级限流（20/分/登录主体，`21ea438`） |

## 7. 部署阻塞清单（8 项）

| # | 项 | 状态 | 修复提交/说明 |
|---|---|---|---|
| 1 | 备份与敏感产物隔离：全库备份移出仓库（`../petcare-o2o-backups`），`.dockerignore` 补 `logs/`、`*.sql.gz` | ✅ 已修复 | `ff38cd0` |
| 2 | 根 `.env` 全是弱 demo 凭据（DB/JWT/MYSQL_ROOT）——生产须**全新生成**，不能拷贝 demo 值 | ⬜ **部署时动作** | 购买服务器后生成强凭据写入生产 `.env`；注意 Jenkinsfile 部署校验会核对 DB 口令与现有卷一致 |
| 3 | compose 强化：admin-web/h5 端口绑 `127.0.0.1`（公网走 TLS 反代）；6 处 `changeme` 回退 + `JWT_SECRET` 改 `${VAR:?}` 强制语法；MySQL healthcheck 去 root 凭据 | ✅ 已修复 | `ff38cd0`；连带 Jenkinsfile 占位值注入 + 部署校验扩到 3 变量 |
| 4 | `frontend/miniapp/.env.production` 指向 `http://192.168.137.1:8080`（热点内网 IP）——生产构建会把 JWT/密码发往明文内网 | ⬜ **部署时动作** | 生产构建前改为真实域名 + HTTPS（域名/服务器待购买，见 docs/13 VPS 方案） |
| 5 | `application.yml` 默认 profile 改 `prod`：裸 `java -jar` 不再静默落入 dev（公开 JWT 回退密钥） | ✅ 已修复 | `ff38cd0`；本地开发需显式 `SPRING_PROFILES_ACTIVE=dev` |
| 6 | AI 全计费入口限流：`AiRateLimitFilter` 前缀集合（conversations/ + post-assistant/ + admin/ai/ + 发帖审核）+ 三层限额（分钟 20 / 每用户每日 50 / 全站每日默认关） | ✅ 已修复 | `649a330`；`AiRateLimitFilterTest` 9 用例，全量 1078 通过 |
| 7 | nginx 安全头与上传上限：`client_max_body_size 12m`（修 >1MB 上传 413）+ `X-Frame-Options` / `nosniff` / `Referrer-Policy` | ✅ 已修复 | `ff38cd0`；`nginx -t` 通过 |
| 8 | docs/07 部署文档三处过时（弱口令表述 / 健康检查端点 / dev 种子挂载） | ✅ 已修复 | 2026-08-15 文档整合（docs/07 §4.2-4.3） |

## 8. 部署时待办（第 2、4 项操作指引）

1. **凭据**：生产 `.env` 全新生成（`openssl rand` 级别）；`JWT_SECRET` ≥32 字节；DB 口令若沿用已有卷须与卷内一致，否则需重置卷。
2. **H5 生产 API 地址**：`frontend/miniapp/.env.production` 的 `VITE_API_BASE_URL` 改为 `https://<真实域名>`，经 TLS 反代（Caddy 方案见 `docs/13-vps-deploy-cheatsheet.md`）。
3. AI 开关：用户已拍板上线启用（`AI_PROVIDER_ENABLED=true` + DeepSeek key + PgVector）；Jenkins 仅做构建验证。

---

# 安全复审 2026-08-23（资源耗尽面 P0 修复）

> 类型：授权白盒复审（4 路并行：认证会话 / 业务校验 / AI 模块 / 前端与部署 CI） ｜ 状态：**P0 三项已修复，全量回归通过**

## 9. 复审结论

前两轮修复复核有效：SQL 参数化、IDOR 归属校验、金额/库存/预约/钱包事务与行锁、上传魔数校验、JWT HttpOnly Cookie 双轨、AI 架构边界守卫均合格。本轮新发现 **8 中危 + 约 20 低危/信息项，0 高危**，集中在资源耗尽、限流拓扑与部署流水线三个维度。中危清单：

- **M1** RateLimitFilter 桶 Map 无界增长 → 海量伪造 IP 慢性 OOM
- **M2** 分页插件无全局 maxLimit → 15 个端点可 `size=100000` 拖库（含匿名 `/api/v1/posts`、`/api/v1/products`）
- **M3** AI SSE 无并发连接限制 + `SimpleAsyncTaskExecutor` 无界建线程 → 线程耗尽 DoS
- **M4** 规划中 Caddy 反代拓扑下 XFF 末段恒为网关 IP → 登录限流退化为全站共享桶（部署时处理）
- **M5** 无账号级登录失败锁定，admin 与用户登录同阈值
- **M6** deploy.yml 自动回退 `.env.example`（changeme 可静默上生产）+ tag 名未消毒插值进 sed
- **M7** CSRF 全局禁用仅靠 SameSite=Strict 单点防御
- **M8** 预约/订单 DTO 超长字段稳定 500；钱包手工加钱无单笔上限、无自操作拦截

## 10. 本轮已修复（P0）

| # | 修复 | 位置 | 守卫测试 |
|---|---|---|---|
| M2 | `PaginationInnerInterceptor.setMaxLimit(100)` 一行兜底全部端点（已核实无内部大批量分页依赖 >100） | `common/config/MyBatisPlusConfig.java` | `MyBatisPlusConfigTest` |
| M1 | 桶容量上限（`max-buckets` 默认 50000）+ 闲置桶清扫（按窗口节流）+ 打满 fail-open 放行不计数 | `common/security/RateLimitFilter.java` | `RateLimitFilterTest` 新增 3 用例（有界/清扫回收/fail-open 不误伤） |
| M3 | 有界 `ThreadPoolTaskExecutor`(core 4/max 16/queue 0) + 单用户并发流上限 2（拒绝路径保证归还许可）+ 同步归属校验移到开流前 | `ai/service/StreamingConversationService.java` | `StreamingConversationServiceTest` 新增 3 用例（超限拒绝/越权前置拒绝/池满拒绝且归还） |

验证：`mvn test` 全量 **1101 通过 / 0 失败**（新增守卫 7 个）。异步生命周期回归守卫（AsyncLifecycle）继续通过。

## 11. 待办（按优先级）

- **P1**：~~账号级登录失败锁定 + admin 更严阈值；CSRF 双提交纵深；预约/订单 DTO `@Size`/`@Pattern` 补齐；钱包加钱单笔上限与自操作拦截。~~
  **✅ 已完成（2026-08-23 第二批）**：
  - `LoginAttemptService`：账号级固定窗口失败锁定（USER 5 次/10 分，ADMIN 更严 3 次/15 分），成功清零、过期自动解锁、容量上限防枚举洪泛；锁定复用 `rate_limit_exceeded`(429) 文案不透露账号存在性。接入 `UserAuthService.login` / `AdminAuthServiceImpl.login`。
  - `RateLimitFilter.admin-max-requests`（默认 5）管理端 IP 阈值独立且更严。
  - 钱包 `requirePositiveAmount` 单笔上限 100 万；自操作拦截经评估不可实现（admin 与 user 分表、无身份关联），依赖既有强制审计日志追溯，决策已记录。
  - DTO 补齐：BookingCreate/ProductOrderCreate（contactName/contactPhone/remark/serviceMode/paymentMethod）、BookingRejectRequest.reason(255)、ReportPostRequest.reasonType(32)、SensitiveWordCreateRequest.category(32)。
  - test profile 放宽限流/锁定阈值（沿用既有先例），生产配置不变。
  - 守卫测试：LoginAttemptServiceTest 6 + RateLimitFilterTest 重构 admin 阈值对比 + DtoValidationGuardTest 8 + WalletServiceTest 上限 1；全量回归 **1117 通过 / 0 失败**。
  - **CSRF 双提交纵深移至独立切片**：需后端 filter + admin-web/H5 双前端请求头协同改造 + 契约测试重锚定，风险高于其余项，单独交付。
  **✅ 已完成（2026-08-23 第四批，M7 收口）**：
  - 新增 `CsrfDoubleSubmitFilter`：仅约束 Cookie 通道的非安全方法写请求（`X-XSRF-TOKEN` 头 vs `XSRF-TOKEN` cookie 常量时间比对，失败 403）；Bearer 通道与匿名请求放行；permitAll 认证入口豁免；GET 惰性补发令牌实现存量会话无感迁移。
  - `AuthCookieService` 登录/登出自动配对签发/清除 XSRF cookie（非 HttpOnly，Secure/SameSite=Strict/Path=/api 与认证 cookie 一致）。
  - 前端：admin-web axios 开启 `withXSRFToken` 双提交回显；miniapp H5 request 层与 AI SSE fetch 补头（MP 端 Bearer 通道不受影响）。
  - 守卫测试：CsrfDoubleSubmitFilterTest 9 用例 + AuthCookieServiceTest 配对断言升级；前端双端构建通过；全量回归 **1137 通过 / 0 失败**。
  - 迁移说明：升级瞬间已存在的会话在首个 GET 时自动补发令牌，无需重新登录。
- **P2**：~~注册手机号枚举泛化（`UserAuthService.java:58`）；LIKE `%`/`_` 转义统一工具（5 处）；AI 对话原文出 INFO 日志（`AiConversationApplicationServiceImpl.java:214`）；改密后旧 JWT 撤销；AiRateLimitFilter 覆盖 `POST /ai/conversations` 创建端点；Upload/Ai 限流桶同款容量防护。~~
  **✅ 已完成（2026-08-23 第三批）**：
  - 注册重复手机号归并为通用 `validation_error`(400)，文案引导直接登录，消除专属枚举信号（残余信号：与其它校验失败同码同形，已不可脚本区分）。
  - 新增 `common/util/SqlLikeUtils.escape()`，收口全部 LIKE 通配符面（社区帖子/标签搜索、钱包手机号/ID 片段、管理端用户搜索、商品搜索共 8 个 like 调用点），MySQL/H2 默认转义符一致无需 ESCAPE 子句。
  - AI RAG 日志改为仅记录 `questionLength`，对话原文不再进 INFO。
  - **改密后旧 JWT 撤销**：新增 `JwtRevocationRegistry`（进程内"主体→撤销时刻"，容量上限+TTL 惰性清扫）；`TokenParseResult` 增加 iat；filter 在解析后校验；触发点=改密+密保找回重置。同秒边界语义：撤销秒内旧 token 必拒（iat 秒级精度宁严勿漏），撤销秒内新登录可能被误拒一次属可接受代价。进程内存态重启清零后旧 token 最长恢复至剩余 TTL（≤120 分钟，与引入前常态相同，只改善不回退）；多实例需换 Redis。
  - `AiRateLimitFilter.isProtected` 补 `POST /api/v1/ai/conversations` 精确匹配（原前缀尾斜杠漏掉创建端点）。
  - Upload/Ai 两过滤器分钟桶增加容量上限（默认 50000 可配）+ 闲置清扫 + 打满 fail-open 放行不计数；Ai 日额度桶按 2 天闲置清扫。
  - 守卫测试：JwtRevocationRegistryTest 4 + SqlLikeUtilsTest 3 + Ai/Upload 容量与覆盖 3 + UserSecurityAndPasswordControllerTest 改密撤销 E2E 1；全量回归 **1128 通过 / 0 失败**。
- **部署时（随 VPS）**：~~M4/M6——deploy.yml 去自动回退与 sed 消毒；反代拓扑下限流键修正；nginx location 头继承补齐 + CSP；生产 `.env` 全新生成（含轮换本机 DeepSeek key）；H5 生产域名 HTTPS。~~
  **代码侧已修（2026-08-23 第五批，上线准备切片）**：B1 nginx 双 conf 改 XFF 覆盖式透传 + filter 多段告警；B2 deploy.yml 去自动回退 + tag 白名单；B3 .env.example AI 生产标注（GLOBAL 日额度必配）；B4 check-env.ps1 脱敏重写；B5 /uploads 与静态资源 location 补全安全头 + 全站 CSP。首次引导缺口以 `AdminBootstrapRunner`（方案 A：`--bootstrap-admin` 一次性命令）解决，见 docs/13 §13。
  **仍属服务器操作**：生产 .env 强凭据生成、H5 域名 HTTPS 化、Caddy/nginx 实际部署、备份与日志轮转——清单见 docs/13 §2-§9/§12/§13。

