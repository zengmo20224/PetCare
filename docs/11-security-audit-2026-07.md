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
