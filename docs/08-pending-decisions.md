# 决策记录

本文件只记录会影响后续实现的重要决定。标记为“未决”的事项只有在直接影响当前任务时才会阻塞实现。

## 已决定

| ID | 决定 | 状态 | 日期 |
|---|---|---|---|
| D-001 | 用户端改为响应式 H5 优先 | 已决定 | 2026-06-13 |
| D-002 | 暂时保留 `frontend/miniapp` 目录，使用其 UniApp H5 构建能力 | 已决定 | 2026-06-13 |
| D-003 | 微信登录和小程序适配延后，不阻塞 H5 | 已决定 | 2026-06-13 |
| D-004 | AI 模块代码已实现；**V1 部分激活**（客服对话 + 经营分析报告接 DeepSeek）；发帖助手/用量页的原关闭态已随 M8.3/M8.5 解除（2026-08-14）；V2 Agent 增量见 D-013 | **已修订（2026-07-21；2026-08-14 关闭态解除）** | 2026-07-10 更新 |
| D-005 | 商品、购物车和订单必须真实可用 | 已决定 | 2026-06-13 |
| D-006 | 社区简化为发帖、浏览、点赞、评论和收藏 | 已决定 | 2026-06-13 |
| D-007 | 保留基础营销活动展示、管理和商品/服务关联 | 已决定 | 2026-06-13 |
| D-008 | 合并执行文档，按纵向切片推进，不再新增阶段 brief 和 review 文档 | 已决定 | 2026-06-13 |
| D-009 | V1 保持单门店模块化单体 | 已决定 | 原设计延续 |
| D-010 | 不接**真实在线支付通道**（微信/支付宝等）、优惠券、会员积分和多门店；**钱包余额**为管理端手工台账，仅用于无支付资质下的支付链路验证，不计息、不可提现、不可转账 | 已决定 | 2026-07-18 更新（CR-20260718-003） |
| D-011 | 预约并发、订单金额、库存和权限必须由后端强制控制 | 已决定 | 原设计延续 |
| D-012 | 钱包余额的边界：扣款与扣库存必须同事务；失败必留痕；管理员调整必填理由；本期不做营销赠送 | 已决定 | 2026-07-18（CR-20260718-003） |
| D-013 | V2 AI Agent 增量：引入 PgVector 向量库做 RAG + langchain4j 作 `AiProviderClient` 实现层 + Agent 受限工具调用；激活社区助手与内容审核；安全边界继承 D-004（不直连 DB / 不诊断 / 建议不改库） | **已实施（M8.0-M8.3/M8.5，2026-08-14；M8.4 图片审核延后）** | 2026-08-01 |
| D-014 | AI 编排路线：继续 Java 内 langchain4j + 自写 Agent，**不引入 Dify 进生产链路**；Dify 仅作开发态 Prompt 调试/对比工具；按 M8.2 → M8.3 → M8.4 顺序推进 | **已决定** | 2026-08-05 |

## D-010 / D-012 补充说明（2026-07-18）

**D-010 修订背景**：原 D-010 措辞"不接在线支付、优惠券、积分和多门店"在引入钱包余额时产生语义冲突——钱包余额同时触碰"在线支付"（资金注入路径）和"会员积分"（账户类资产）。经 CR-20260718-003 评估，钱包余额既不是真实在线支付通道（无第三方支付资质、无资金清算、无签名验签/回调），也不属于会员积分（不基于消费返点、不参与等级、不可兑换权益）。它是一个"管理端手工台账"，本质是收银台现金/扫码收款的数字化延伸。

**D-010 修订后的精确边界**：
- ✅ 允许：管理端手工给用户钱包加/减余额；用户用钱包余额支付商品订单与服务预约；订单/预约取消时事务内自动退款到钱包；管理员后台审计钱包所有操作；用户端只读查询自己的余额与流水。
- ❌ 仍禁止：真实在线支付通道接入（微信支付、支付宝等，仍受 P-003 约束）；用户自助充值；提现、转账、计息；优惠券、会员积分、等级权益；营销定价规则（如"充100送20"本期不实现，仅预留 `BONUS` source_type）；退款审批流（单一 SUPER_ADMIN 角色下价值有限）。

**D-012 强制规则**（违反即视为缺陷）：
1. 钱包扣款与订单扣库存/预约容量扣减必须在同一数据库事务（`@Transactional(rollbackFor=Exception.class)`），禁止跨事务。
2. 钱包余额变更必须先 `SELECT ... FOR UPDATE` 行锁，再条件 UPDATE（`balance >= amount`），全局锁顺序统一为 `wallet → product(asc) → booking`。
3. 钱包所有写操作必须有对应的 `wallet_transaction` 流水记录（含 before/after 余额快照），流水表只追加不修改。
4. 管理员钱包调整（充值/扣减）必须照 booking 范式审计：成功审计与业务同事务（save 失败抛 `IllegalStateException` 触发回滚），失败审计走 `saveFailLog` REQUIRES_NEW 保活，未知异常脱敏为 `unexpected_error`。
5. 管理员钱包调整必须填写理由（`reason` 字段非空校验）。
6. 金额精度统一 `DECIMAL(10,2)` + `BigDecimal` + `RoundingMode.HALF_UP`，永不信任客户端金额。

**D-012 已实现清单**（CR-20260718-003，2026-07-19 实施完成）：

- ✅ 规则 1（同事务）：`ProductOrderTransactionServiceImpl.doCreateOrder` 与 `BookingTransactionServiceImpl.createBookingOnce` 的钱包扣款/退款与扣库存/容量扣减同处一个 `@Transactional`。直接测试：`WalletPaymentAtomicityIT`（3 路径）、`WalletRefundAtomicityIT`（3 场景）。
- ✅ 规则 2（行锁 + 锁序）：`WalletMapper.selectForUpdate` + `deductBalance`/`addBalance` 条件 UPDATE；全局锁序 wallet→product(asc)→booking。直接测试：`WalletConcurrencyMySqlIT`（超扣防护 + 幂等并发 + 高并发 5 线程）。
- ✅ 规则 3（流水只追加）：`wallet_transaction` 表无 update_time/deleted 字段，`WalletTransaction` 实体不继承 BaseEntity；`insertLedger` 写入 before/after 余额快照。直接测试：`WalletEntityMappingIT`（列映射 + 唯一约束）。
- ✅ 规则 4（审计范式）：`WalletApplicationServiceImpl.auditSuccess/auditFail/sanitizeErrorMessage` 照搬 `BookingApplicationServiceImpl`。直接测试：`AdminWalletAuditTest`（6 用例）+ `AdminWalletAuditRollbackTest`（3 场景验证事务回滚）。
- ✅ 规则 5（理由必填）：`WalletServiceImpl.requireReason` + `WalletDtos.WalletRechargeRequest/WalletAdjustRequest` 的 `@NotBlank` 校验。前端 accounts 页面对话框 reason 必填。
- ✅ 规则 6（金额精度）：`WalletServiceImpl.requirePositiveAmount` 用 `setScale(2, RoundingMode.HALF_UP)`。直接测试：`WalletServiceTest.AmountPrecision`（3 位小数 HALF_UP + scale 归一化）。

## D-004 修订说明（2026-07-21）

**修订背景**：原 D-004（2026-07-10）将所有 AI 能力关闭。本次经用户授权后，**部分**激活 AI 能力——用户端智能客服对话 + 管理端 AI 经营分析报告——并接入真实 DeepSeek LLM。其余 AI 能力（发帖助手、AI 用量查看页）仍保持关闭。

**修订后的精确边界**：
- ✅ 已激活：
  - 用户端智能客服对话（`AiConversationController`，CUSTOMER_SERVICE / PET_CHAT 两种会话类型，多轮上下文，医疗安全兜底保留）。
  - 管理端 AI 经营分析报告（`AdminAiAnalysisController`，BUSINESS/COMMUNITY/SALES/ACTIVITY 四种类型，仅传入预聚合 JSON，AI 不直连 DB）。
  - 接入真实 DeepSeek（`DeepSeekAiProviderClient`，条件装配 `petcare.ai.provider-enabled=true` 时生效）。
  - dev 与 prod profile 默认 `provider-enabled=true`；`AI_PROVIDER_ENABLED` / `DEEPSEEK_API_KEY` / `DEEPSEEK_MODEL` 走环境变量，不入库。
  - RBAC 种子补 `ai:analysis:generate`（7048）、`ai:usage:read`（7049），授予 SUPER_ADMIN / ADMIN。
- ❌ 仍关闭：用户端社区发帖助手（`AiPostAssistantController` 仍硬编码 401）、管理端 AI 用量查看页（前端未建）。
- 🔒 安全边界不变（AGENTS.md §4）：
  - AI 不直连 DB：`AiProviderArchitectureTest` 强制 provider 包不依赖 Mapper / DataSource / MyBatis。
  - AI 不做疾病诊断 / 药物处方 / 治疗承诺：`HighRiskSymptomDetector`（PET_CHAT 前置）、`PetMedicalSafetyPolicy`（输出后处理）、`AiOutputSafetyPolicy`（注入/密钥防护）三层保留。
  - 上游错误不外泄：`DeepSeekAiProviderClient` 失败映射为 `AiProviderUnavailableException`（503）或 `AiProviderException(internalCode)`，原始 body/headers/apiKey 永不出现在异常或日志。

**降级路径**：设 `AI_PROVIDER_ENABLED=false` 或不设 `DEEPSEEK_API_KEY`，Bean 构造时记 WARN、运行时返回 503，不影响应用启动与其他功能。

## D-013 V2 AI Agent 增量（2026-08-01）

**决策背景**：D-004 将 V1 AI 能力部分激活（客服对话 + 经营分析）。本次进入 V2 阶段，把"无状态 prompt-stuffing 客服"和"吃预聚合 JSON 的分析报告"升级为可检索（RAG）+ 可行动（Agent 工具调用）的 AI Agent，并激活 V1 关闭的社区助手与内容审核。完整设计见 `docs/09-ai-agent-design.md`。

**核心技术选型**：

| 维度 | 决定 | 理由 |
|---|---|---|
| 向量库 | **PgVector**（独立 PG 实例） | langchain4j/spring-ai 一等支持；业务库 MySQL 零污染（派生知识副本独立存储）；单门店数据量在 PgVector 舒适区 |
| LLM 框架 | **langchain4j**，作为 `AiProviderClient` 端口的实现层 | 不破坏 V1 端口契约与 `AiProviderArchitectureTest`；RAG/ChatMemory/Tool 能力直接复用；DeepSeek 手写实现保留作降级 |
| embedding | **all-MiniLM-L6-v2 本地 ONNX**（384 维，Java 内推理） | 零外部 API 费用；中文质量不足时可切厂商 API（schema 已留 `model_name`） |
| 图片审核 | **ONNX Java 原生**（nsfw_model），不引入 Python sidecar | 与单门店运维定位不符；DJL + onnxruntime 成熟 |
| 对话记忆 | langchain4j `MessageWindowChatMemory` + 自定义 JDBC Store 落 `ai_message` 表 | 不引入 Redis；对齐 V1 `MAX_HISTORY_TURNS` |

**Agent 范围（M8 切片）**：
1. 智能客服 Agent（升级 V1 客服，RAG + 只读 Tool + SSE 流式）
2. 经营分析 Agent（升级 V1 报告，只读下钻 Tool + 结构化报告）
3. 社区助手 Agent（激活 V1 的 401 + 个性化草稿，不自动发布）
4. 内容审核 Agent（文本 LLM 分类 + 图片 ONNX，产 `PostReport` 不直接删）

**安全边界（继承 D-004，强化为 8 条，详见 `docs/09` §2）**：
- B1 AI 不直连 DB：V1 由 `AiProviderArchitectureTest` 强制 provider 包；V2 新增 `AiAgentArchitectureTest` 强制 `ai/agent/` 与 `ai/rag/` 不依赖 `*Mapper`，Agent 访问业务数据只通过受限 Tool → 业务 Service。
- B2/B3/B5 三层医疗护栏 + 上游错误不外泄 + 输入校验：全部保留。
- B4 AI 建议只作参考：经营分析 Tool 全只读；社区审核/图片审核产 `PostReport`，最终处置由 moderation 规则或人工确认，不自动删帖封号。
- B6（新增）PgVector 只存派生知识副本，业务真源仍在 MySQL，跨库最终一致（`source_hash` 幂等 + 定时补偿），客服价格/库存不信向量库走实时 Tool。
- B7（新增）Agent Tool 是静态白名单 + RBAC 双重校验。
- B8（新增）流式输出仍过护栏（分句检查）。

**降级路径**：
- `AI_AGENT_ENABLED=false` / `AI_RAG_ENABLED=false`：Agent/RAG 不可达，退回 V1 客服（全量塞 prompt）与 V1 分析报告，应用启动与其他功能不受影响。
- PgVector 故障：Agent 降级纯 Tool 回答；知识库可从 MySQL 全量重建。
- langchain4j 故障：切 V1 DeepSeek 手写 Provider；RAG 退回全量塞 prompt（数据量小可承受）。

**未决子项（不阻塞设计，实施时定）**：embedding 本地 vs 厂商 API（Q-1）、知识入库定时 vs 事件（Q-2）、是否引入 Reranker（Q-3）、图片审核自托管 vs 商用 API（Q-4）。详见 `docs/09` §14。

**实施归档（2026-08-14，M8.0-M8.3/M8.5）**：

- Q-1 已落地：本地 ONNX（BGE-small-zh-v1.5，512 维，中文召回质量优于 all-MiniLM，M8.1 实测后切换）。
- Q-2 已落地：定时重建（每日 03:00）+ 管理端手动重建；重建为"先全清再建"强幂等 + 进程内互斥。
- Q-3 维持不做（召回质量当前可接受）。
- Q-4 随 M8.4 延后（文本审核已上线，图片审核独立切片再启）。
- 对话记忆（§7.3 JDBC ChatMemory Store）未按设计落地 langchain4j ChatMemory——实际实现复用 V1 `ai_message` 表构建多轮上下文（`PromptFactory` + Agent 内 history 拼装），语义等价、少一张表；如后续引入 langchain4j AI Services 再评估。
- 实施中同步完成的安全加固（源自 2026-08-14 全仓安全审查）：RAG 召回内容注入前过 `AiOutputSafetyPolicy`（B5 补齐）、客服/Agent 路径补医疗三层护栏（B2 补齐）、DeepSeek 截断保留 system 消息、`ai_tool_call_log` 审计字段按 admin/user 身份分流、AI 消息端点按登录主体限流（20/min 默认）、知识库重建幂等 + 互斥 + 操作审计。

## D-014 AI 编排路线（2026-08-05）

**决策背景**：用户在开发机加入了 Dify agent 系统，评估是否转向用 Dify 承担 Agent 编排（替代 V2 自写 langchain4j Agent），避免重复造轮子。

**核心决定**：**继续 Java 内 langchain4j + 自写 Agent 推进 M8，不引入 Dify 进生产链路。**

**理由**：

1. **保留架构守卫的代码级强制**：B1-B8 安全边界（`AGENTS.md` §4 + `docs/09` §2）目前由 `AiProviderArchitectureTest`（V1）与 `AiAgentArchitectureTest`（V2）反射强制。Dify 是外部进程，业务数据通过"暴露 REST 给 Dify 当 Tool"实现，守卫测试对 Dify 内编排完全失效，B1-B8 退化为"文档约定"而非"代码强制"。
2. **三层医疗护栏不可 1:1 复刻**：B2 的 `HighRiskSymptomDetector`（前置）+ `PetMedicalSafetyPolicy`（后处理）+ `AiOutputSafetyPolicy`（通用）+ B8 流式分句护栏，依赖 Java 策略对象语义；Dify workflow 节点（LLM/HTTP/Code）无法等价表达。
3. **保留已落地投入**：M8.0（langchain4j + PgVector + 5 个只读 Tool + Agent 工具协议 + 审计 + SSE 后端）+ M8.1（客服 RAG 升级 + BGE-zh 中文 embedding）已约 3000-4000 行 Java + 测试 + PgVector schema，全面切 Dify 将浪费。
4. **与项目定位一致**：P-002 明确项目定位为"展示 AI coding 实力的 demo"——手写 langchain4j Agent 编排 + 工具协议白名单 + 架构守卫 + 三层医疗护栏，本身就是 AI coding 实力叙事的核心组成；Dify 拖拽 workflow 偏向 low-code 运营工具，削弱叙事。
5. **运维成本**：Dify 完整栈（API + Worker + Web + Redis + 自带 PG + 向量库 + Nginx）10+ 容器约 4GB RAM，与现状（Java 单体 + MySQL + PgVector）4 容器 ~1.5GB RAM 相比翻倍，与 D-009"V1 保持单门店模块化单体"精神冲突；课程 demo 场景部署复杂度直接影响演示可靠性。

**Dify 角色（明确边界）**：
- ✅ 开发态 Prompt 调试 / 多模型对比 / 灵感工具（不接入业务链路）
- ❌ 生产链路 Agent 编排、Tool 调用、知识库存储、SSE 输出、审计、护栏

**后续路线**：按 `docs/09` §12 M8 切片顺序推进——M8.2（经营分析 Agent）→ M8.3（社区助手 + 文本审核 Agent）→ M8.4（图片 NSFW 审核）→ M8.5（收口）。每个切片满足 `docs/02` §5 完成定义，复用 M8.0/M8.1 已落地的 Agent 基建（`AgentToolRegistry` + `AgentTool` 契约 + `AiToolCallLog` 审计 + 三层护栏）。

**回退条件**：仅当后续出现以下情况之一，才重新评估本决策：
- langchain4j 与 Spring Boot 兼容性出现无法修复的阻断；
- BGE-zh/PgVector 在中文召回质量上持续不达预期且无调优空间；
- 项目定位从"技术 demo"转向"对外营业产品"且 AI 场景需要持续高频扩展。

## 未决

### P-002：微信小程序是否进入 V2 范围

状态：**已决定推进 demo 形态（非上架），2026-08-01**。原 2026-07-04 降级为"长期规划"的结论针对的是"真实对大陆用户营业"场景；本次用户将项目定位为"展示 AI coding 实力的 demo"，不追求上架，故资质/备案/微信支付不再是阻断——小程序端全面铺开到"可在微信开发者工具里完整演示"的程度。

本次推进的精确边界（已落地，2026-08-01）：
- **微信登录**：后端三态 Provider（`disabled`/`mock`/`real`）由 `petcare.wechat.mode` 切换。dev 默认 `mock`（按 code 确定性派生 openid，开发者工具点登录即拿 token）；`real` 调真实 `jscode2session`（需 appid/secret 才能真机）。登录链路对齐密码登录（返回 `tokenType`/`accessToken`/`expiresInSeconds`/`user`），前端复用 `AuthResult`。
- **支付**：复用已实现的"钱包余额（WALLET）"模拟支付（D-010/D-012），不接微信支付。商品/购物车/订单全流程可演示。
- **小程序兼容回归修复**：AI 客服页（v-html→rich-text、document/window→条件编译）、全站 15 个图片 helper（统一 `assetFullUrl`，修复小程序图片黑屏）。
- **配置**：`manifest.json` 填 appid 占位 + 开分包优化（`optimization.subPackages` + `lazyCodeLoading:requiredComponents`）；主包 ≈332KB，远低于 2MB。

仍不做（非 demo 范围）：真实资质办理、微信支付商户号、手机号解密、用户协议/隐私政策/`wx.requirePrivacyAuthorize`、内容安全 `msgSecCheck`。

降级原因（历史，2026-07-04）：用户明确当前场景为"课程作业 / 演示"，且选择"国外服务器自用测试"。该场景下小程序的微信登录、微信支付、备案、类目资质等门槛不必要，H5 是更合适的载体（与项目 README"H5 优先"主线一致）。

历史背景：`docs/12-wechat-miniprogram-launch-plan.md` 完成了 gap 分析，作为长期参考资料保留。当未来场景升级为"真实对大陆用户营业"时（需真实资质 + 微信支付 + 合规），仍需按 `docs/12` §2-§4 走完整上架流程。

当前阻塞：**无**。demo 形态已可演示；上架形态依赖资质办理（非技术问题）。

### P-003：是否申请微信支付商户号

状态：**长期规划，随 P-002 一并降级（2026-07-04）**。

背景：微信支付仅在"小程序商品零售类目"场景下才需要。当前课程演示场景不涉及。详见 `docs/12-wechat-miniprogram-launch-plan.md` §3.2、§6。

### P-001：H5 正式公开登录方式

状态：已决定。

决定：采用手机号 + 密码登录，找回密码通过安全问题（用户注册时设置问题和答案，找回密码时回答验证）。

已实现：
- POST `/api/v1/auth/register` — 注册（手机号、密码、安全问题）
- POST `/api/v1/auth/login` — 手机号 + 密码登录
- POST `/api/v1/auth/forgot-password/questions` — 获取安全问题
- POST `/api/v1/auth/forgot-password/reset` — 回答安全问题并重置密码
- H5 注册、登录、找回密码页面
- 测试登录（`@Profile("test")`）仍保留用于自动化测试，生产环境自动关闭。

## 使用规则

- 新问题只有确实需要用户选择且会影响架构或业务规则时才加入本文件。
- 技术实现细节应由 Agent依据现有代码做出保守选择，不要升级为未决事项。
- 决定完成后直接更新本文件和相关核心文档，不新增单独决策文档。
