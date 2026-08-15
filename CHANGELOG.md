# 更新日志（Changelog）

> 本文件记录 PetCare O2O 项目所有重要版本与变更，按 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 风格维护，遵循 [SemVer](https://semver.org/lang/zh-CN/)。
>
> 基线 tag 与本文件一一对应，详见 `docs/基线清单.md`。

---

## [Unreleased] / v1.2.0 — AI V2 Agent + UI 改版 + 上线加固（2026-07-21 ~ 2026-08-15）

### 新增（AI V2 Agent，D-013 / `docs/09`）

- **微信登录 demo 形态（P-002）**：后端三态 Provider（`disabled`/`mock`/`real`）；小程序兼容回归修复（v-html→rich-text、15 个图片 helper 统一 `assetFullUrl`）；主包 ≈332KB。`fb7b692`
- **M8.0 基建**：langchain4j + PgVector 独立实例（RAG 派生知识副本）+ embedding + 知识入库；`RagConfig` DataSource 局部化。`84628e2` `5812365`
- **M8.1 客服 RAG 升级**：RAG 召回与 V1 实时 Tool 并存；中文 embedding 切 BGE-small-zh-v1.5（512 维）。`bb39532` `9cc8dd4`
- **M8.2 经营分析 Agent**：只读下钻 Tool + 结构化报告；`AgentToolRegistry` 白名单 + RBAC 双校验 + `ai_tool_call_log` 审计。D-014 决策不引入 Dify 进生产链路。`443df22`
- **M8.3 社区助手 + 文本审核**：解除 401 激活发帖助手（仅草稿不自动发布）；文本审核 Agent LLM 分类产 `PostReport` 进人工队列。`639c4d9`
- **M8.5 收口**：AI 用量查看页（管理端）+ H5 SSE 流式对接 + 全量回归（1067 默认 + 33 tc-mysql）。`490f35c`
- **安全边界守卫**：新增 `AiAgentArchitectureTest`（ai/agent 不依赖 Mapper，数据只经受限 Tool）。

### 新增（管理端与演示）

- **商品/服务上架端点对称**（与禁用下架互逆）。`8e215c6`
- **商品/服务删除（phase19）**：服务端强制先下架/停用（OFF_SALE）后删除，新权限码 + 审计 + 管理端按钮。`329fdc4`
- **真实媒体与内容种子**：`data-media.sql`（10 服务封面 + 12 商品电商级文案 + 10 社区帖 44 评论），68 张 Wikimedia/Unsplash 授权图片入库 `uploads/images/seed/`（署名见 CREDITS.md）。`64e239a`

### 新增（UI 改版 2026-08）

- **miniapp 全站 UI 改版**：新组件体系（PcServiceCard 等）+ Tabler Icons 字体方案（docs/14→16→17 调研链）+ 设计 demo；契约守卫重锚定到新设计，160/160 全绿。`64291f1` `bad0747`
- 服务列表卡片恢复真实封面照片渲染（UI 改版回归修复）。`c6022e4`

### 修复（安全，2026-08）

- **3 个高危**：账号接管 / 生产弱口令 / 资金滞留。`9118be8`
- **中危批次 1**：AI 安全 7 项 + 限流 3 项（A1-A7/C1/C3）。`7ae9668`
- **中危批次 2**：业务并发 4 项（B1-B4）。`0937a4d`
- **SSE 卡死修复**：JWT 过滤器 ASYNC dispatch 重跑 + 流结束主动完成。`062c91a`
- **JWT 认证迁移 HttpOnly Cookie 双轨**：Cookie 优先 + Bearer 回退（小程序无 cookie），新增 4 个 logout 端点；上传接口分钟级限流（20/分）。后端全量 1093 测试。`21ea438`
- **上线前部署加固 4 项**（备份隔离 / compose 强化 / 默认 prod profile / nginx 安全头）。`ff38cd0`
- **AI 全计费入口限流 + 每日额度**：三层限额（分钟/用户日额/全站日额）。`649a330`

### 修复（CI）

- admin-web 锁文件用 npm@latest 重建（补登记 `@emnapi/runtime`）。`1fd152e`
- 补提 `AiToolCallLog` 无 IService 白名单，修复 Jenkins 全量回归挂测。`5a97b81`

### 文档

- 2026-08-15 文档整合：README 全面刷新（AI V2/测试数/截图展示区）；docs/08 未决区清空（P-001/002/003 移入已决定）；docs/11 追加 2026-08 上线加固清单（8 项，余 2 项部署时动作）；docs/14+17 合并为 18-ui-icon-decisions；CHANGELOG 补断档期；docs/01/02/05/07/requirements-source 局部修正。

---

## [Unreleased] / v1.1.0 — 钱包余额（CR-20260718-003）

### 变更（决策与边界）

- **D-004 修订（2026-07-21）**：部分激活 AI——用户端智能客服对话（CUSTOMER_SERVICE / PET_CHAT，多轮上下文）+ 管理端 AI 经营分析报告（BUSINESS/COMMUNITY/SALES/ACTIVITY），接入真实 DeepSeek LLM。发帖助手、AI 用量查看页仍关闭（后随 M8.3/M8.5 解除）。安全边界不变（AI 不直连 DB、不做诊断/处方/治疗承诺）。
- **D-010 修订**：精确化为"不接真实在线支付通道（微信/支付宝等）、优惠券、会员积分和多门店；钱包余额为管理端手工台账，不计息、不可提现、不可转账"。
- **D-012 新增**：钱包余额强制规则——扣款与扣库存同事务、行锁 + 条件 UPDATE、流水只追加、审计照 booking 范式、调整必填理由、金额精度 `DECIMAL(10,2)` + `HALF_UP`。
- **boundary §3 精确化**：明确"在线支付"指真实第三方支付通道，钱包余额不属于此范畴。

### 新增（后端）

- **com.petcare.ai DeepSeek 接入（D-004 修订 2026-07-21）**：
  - `DeepSeekAiProviderClient`：真实 DeepSeek HTTP 适配器，调用 `POST {base-url}/chat/completions`，失败映射为 `AiProviderUnavailableException`（503）或 `AiProviderException(internalCode)`，不外泄原始 body/headers/apiKey。
  - `AiConfig`：Bean 条件装配——`provider-enabled=true` 时用 DeepSeek，否则回落 `DisabledAiProviderClient`。
  - `AiConversationController.resolveCurrentUserId`：移除硬编码 401，改用 `SecurityContextHelper.getCurrentUserId()`（用户 JWT 已实现）。
  - `AiConversationApplicationServiceImpl`：修复单轮失忆——`sendMessage` 加载历史 `ai_message`（最近 10 轮）拼入 provider 请求；`PromptFactory` 新增带历史重载。
  - RBAC 种子：`data-dev.sql` / `migration-phase7-admin-rbac.sql` 新增 `ai:analysis:generate`（7048）、`ai:usage:read`（7049），授予 SUPER_ADMIN / ADMIN。
  - 配置：`application-dev.yml` / `application-prod.yml` 默认 `provider-enabled=true`；`DEEPSEEK_API_KEY` / `DEEPSEEK_MODEL` 走环境变量。
- **com.petcare.wallet 模块**：`user_wallet` 账户表（含乐观锁 version）、`wallet_transaction` 只追加流水表（含 before/after 余额、direction、source_type、related_order、operator、idempotency_key）。
- **WalletService**：`getOrCreateWallet` / `deductForPayment` / `refundForCancellation` / `rechargeByAdmin` / `adjustByAdmin`，全部行锁 + 条件 UPDATE + 流水写入。
- **AdminWalletController**：充值、双向调整（必填理由 + 强制审计）、账户列表、流水查询。
- **UserWalletController**：用户只读查询自己的余额与流水。
- **RBAC**：新增 `wallet:account:{read,recharge,adjust}`、`wallet:transaction:read` 权限码，绑定 SUPER_ADMIN。
- **PaymentMethod 枚举**：新增 `WALLET`。**PaymentStatus 枚举**：新增 `WALLET_PAID`。

### 变更（后端）

- **ProductOrderCreateRequest**：新增 `paymentMethod` 字段，向后兼容默认 `OFFLINE_STORE`。
- **ProductOrderTransactionServiceImpl**：下单事务内接入钱包扣款（锁顺序 wallet→product asc），取消事务内接入钱包退款；移除 `paymentMethod` 硬编码。
- **BookingApplicationServiceImpl**：预约下单/取消接入钱包扣款与退款。

### 新增（前端）

- **miniapp AI 客服（D-004 修订 2026-07-21）**：新增 `pages/ai/chat.vue`（智能客服 / 宠物闲聊切换、多轮气泡、乐观发送）、`api/ai.ts`、`types/ai.ts`；`pages.json` 注册 `pages/ai/chat` 路由；首页 `PcBlockedFeature` 占位卡替换为可点击的"智能客服"入口卡。
- **admin-web AI 分析报告**：新增 `views/ai/reports.vue`（报告列表 + 生成对话框 + 详情对话框）、`api/ai-report.ts`；路由与侧边栏菜单注册，受 `ai:analysis:generate` 权限保护。
- **miniapp `pages/wallet` 分包**：余额卡片、流水查询；订单结算页与预约创建页新增"钱包支付"切换；用户端不实现自助充值（仅提示联系门店）。
- **admin-web `views/wallet`**：账户列表（充值 + 双向调整）、流水查询；新增"钱包管理"菜单；操作日志页 `wallet` 模块字典。

### 新增（测试）

- `WalletServiceTest`、`WalletConcurrencyMySqlIT`（并发扣款不超卖）。
- `WalletPaymentAtomicityIT`（**扣款与扣库存原子性**）、`WalletRefundAtomicityIT`（退款与库存恢复原子性）。
- `AdminWalletControllerTest`（失败必留痕 6 用例）、`AdminWalletAuditRollbackTest`。
- 扩展 `ProductOrderTransactionServiceTest`、`BookingApplicationServiceTest` 覆盖钱包支付分支。

---

## [Unreleased] / v1.0.0-rc1 — 2026-06-22

### 新增（配置管理 / CI/CD — 课程作业核心）

- **配置管理计划**：新增 `docs/03-configuration-management-plan.md`，按 IEEE 828 结构覆盖组织、配置项识别、版本控制、基线管理、变更控制、配置审计、状态报告。
- **配置项登记表**：新增 `docs/配置项登记表.md`，登记 7 类共 76 个配置项。
- **基线清单**：新增 `docs/基线清单.md`，记录功能基线、M1–M6 里程碑基线、产品基线。
- **变更申请单**：新增 `docs/变更申请单-模板.md` 及 2 个实例（CR-20260613-001 登录方式变更、CR-20260622-002 配置管理基线建立）。
- **构建指导书**：新增 `docs/06-build-guide.md`。
- **部署指南**：新增 `docs/07-deployment-guide.md`（Docker Compose 单机部署）。
- **Docker 化**：新增后端 `Dockerfile`（多阶段 maven→JRE）、`frontend/admin-web/Dockerfile`、`frontend/miniapp/Dockerfile`。
- **容器编排**：新增 `docker-compose.yml`（mysql + api + admin-web nginx + h5 nginx）与 `nginx/admin-web.conf`、`nginx/miniapp.conf`。
- **生产配置**：新增 `src/main/resources/application-prod.yml`、补全 `.env.example`。
- **Jenkins 流水线**：新增 `Jenkinsfile`（编译/测试/打包/Docker/部署/邮件通知）、`jenkins/README.md`、`jenkins/email-template.groovy`。
- **GitHub Actions**：新增 `.github/workflows/ci.yml`（push/PR 编译测试）、`.github/workflows/deploy.yml`（tag 触发自动部署）。
- **本 CHANGELOG**：从 git log 回填全部历史。

### 变更

- `.gitignore` 补充 Docker / CI 相关忽略项。

---

## [v1.0.0-m6] — 2026-06-14 · 里程碑基线：发布收口

### 新增

- 用户认证：手机号 + 密码登录，安全问题找回密码（CR-20260613-001）`cce58fd`
- 安全问题下拉选择，密码强度策略（8–32 字符 + 字母数字）`6fe0d29` `038da30`
- 服务卡片与详情封面图 `a3f7047`

### 修复

- 修正种子数据 BCrypt 哈希，补开发用 JWT secret `9cccea6`
- H5 安全问题下拉改用原生 select `c562119`
- M6 收尾编译错误、响应式与测试 `9e8d2b8`

---

## [v1.0.0-m5] — 2026-06-13 · 里程碑基线：社区互动

### 新增

- 社区发帖、点赞、评论、收藏 `b35fb07`
- 公开社区隐私契约测试 `c9c309b`
- 匿名公开目录只读 `3de8153` `dd7a1ff`

---

## [v1.0.0-m4] — 2026-06-13 · 里程碑基线：商品与订单

### 新增

- 购物车、结算、下单完整流程 `d31da27`
- 当前用户地址增删改查 API、默认地址并发事务 `e825208` `310379f` `f01c5bd`
- 宠物档案 CRUD API `143f5c7`

### 修复

- 默认地址切换 fail-closed `8463655`
- 禁用用户写地址返回 401 `310f358`
- 宠物档案写入失败拒绝与空指针 `eaabd39` `fd10668`
- 用户手机号脱敏 `aab5300`，活跃用户边界 `d0f9a95`

---

## [v1.0.0-m3] — 2026-06-13 · 里程碑基线：用户资料与真实预约

### 新增

- 用户 JWT 认证基础 `72a1975`，测试 profile 登录 `49dd45a`
- 当前用户资料读写 API `3399b83`
- 端到端预约流程 `9f8d647`

### 修复

- 用户与管理员 JWT 认证加固 `f8ac5b1`
- 测试登录服务隔离 `c9cef9f`

---

## [v1.0.0-m2] — 2026-06-13 · 里程碑基线：公开浏览与营销

### 新增

- H5 公开浏览集成 `cadc705`
- 营销活动后端、H5 页面与种子数据 `e0c9adf`

---

## [v1.0.0-m1] — 2026-06-13 · 里程碑基线：H5 基础与演示数据

### 新增

- H5-first 加速交付模型文档基线 `e9bb199`
- 公开浏览种子数据 `9b7cbcc`

---

## [v1.0.0-fb] — 2026-06-09 ~ 2026-06-12 · 功能基线

### 新增（管理端 H01–H12）

- PetCare 设计令牌与 App 外壳（H01）`2731729`
- 页面状态组件与反馈工具（H02）`8244bae`
- 表格/筛选/抽屉/确认对话框组件（H03）`2f334b0`
- 登录页与错误页重设计（H04）`283fde7`
- 预约管理共享组件迁移（H05）`a251fb9`
- 员工管理共享组件（H06）`92a52e9`
- 服务/商品迁移至共享组件（H07）`8469cd0`
- 商品订单迁移（H08）`226ce78`
- 社区贴子与举报迁移（H09）`62b9aa5`
- 门店与操作日志（H10）`70e2208`
- 契约测试与敏感词页面重构（H11）`cd79e84`
- 最终质量门禁测试（H12）`27b2bed`

### 新增（管理端骨架）

- Vue 3 + Vite + Element Plus 骨架、认证与路由 `9acf1e0`
- 门店信息与配置页 `267c7d1`
- 全部管理页面与权限控制 `076374a`

### 新增（用户端）

- uni-app 用户端脚手架 `f551e23`

### 修复（质量加固）

- 操作审计日志、并发加固、Snowflake DTO 修复 `7ba8941`
- 强制必需的操作审计日志 `42ccd83`
- 预约管理操作审计可靠性 `f432d3d` `8b4cd7c`
- 管理端开放重定向与错误信息脱敏 `8485c5c`
- API DTO 字段与可空性对齐后端 `d963ad4`
- 订单动作守卫、报表过滤、移除假 API `fb1ca59`
- 状态字典与动作守卫对齐 `6c5d6ef`

### 新增（预约并发与契约加固 — 风险修复 RM-B02/B03）

- 预约状态转换原子性与行锁 `bb5896f`，回归测试 `188eb9f`
- 预约人员重分配加锁 `08519db`，回归测试 `030232f`
- MySQL 安全的员工日期锁 upsert `01565ab`，锁回归测试 `d41c3a0`
- 外部 Snowflake ID 序列化为字符串 `f8e4b08` `b9c7e84`

### 测试

- Testcontainers schema 与并发门禁 `dd58edf`
- 管理端状态契约回归 `f6463f9`
- 管理端 API 契约回归 `08ab84b`
- Snowflake ID 序列化契约 `6533342` `f41b1e9`

### 文档

- AGENTS.md、决策记录、路线图更新 `119ef1e`
- 已批准项目决策记录 `e20a6a9`

---

## 版本号规则提示

- `vMAJOR.MINOR.PATCH-mN`：里程碑基线（M1–M6）
- `vMAJOR.MINOR.PATCH-rcN`：发布候选
- `vMAJOR.MINOR.PATCH`：正式发布
- `vMAJOR.MINOR.PATCH-hfN`：紧急修复

详见 `docs/03-configuration-management-plan.md` §4.3、§5.2。
