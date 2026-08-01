# 决策记录

本文件只记录会影响后续实现的重要决定。标记为“未决”的事项只有在直接影响当前任务时才会阻塞实现。

## 已决定

| ID | 决定 | 状态 | 日期 |
|---|---|---|---|
| D-001 | 用户端改为响应式 H5 优先 | 已决定 | 2026-06-13 |
| D-002 | 暂时保留 `frontend/miniapp` 目录，使用其 UniApp H5 构建能力 | 已决定 | 2026-06-13 |
| D-003 | 微信登录和小程序适配延后，不阻塞 H5 | 已决定 | 2026-06-13 |
| D-004 | AI 模块代码已实现但功能关闭（provider 未接真实 LLM，用户端 401、管理端 403），不进入 V1 业务流程，后续激活时接入 | **已修订（2026-07-21）** | 2026-07-10 更新 |
| D-005 | 商品、购物车和订单必须真实可用 | 已决定 | 2026-06-13 |
| D-006 | 社区简化为发帖、浏览、点赞、评论和收藏 | 已决定 | 2026-06-13 |
| D-007 | 保留基础营销活动展示、管理和商品/服务关联 | 已决定 | 2026-06-13 |
| D-008 | 合并执行文档，按纵向切片推进，不再新增阶段 brief 和 review 文档 | 已决定 | 2026-06-13 |
| D-009 | V1 保持单门店模块化单体 | 已决定 | 原设计延续 |
| D-010 | 不接**真实在线支付通道**（微信/支付宝等）、优惠券、会员积分和多门店；**钱包余额**为管理端手工台账，仅用于无支付资质下的支付链路验证，不计息、不可提现、不可转账 | 已决定 | 2026-07-18 更新（CR-20260718-003） |
| D-011 | 预约并发、订单金额、库存和权限必须由后端强制控制 | 已决定 | 原设计延续 |
| D-012 | 钱包余额的边界：扣款与扣库存必须同事务；失败必留痕；管理员调整必填理由；本期不做营销赠送 | 已决定 | 2026-07-18（CR-20260718-003） |

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

## 未决

### P-002：微信小程序是否进入 V2 范围

状态：**长期规划，非当前优先级（已降级，2026-07-04）**。

降级原因：用户明确当前场景为"课程作业 / 演示"，且选择"国外服务器自用测试"。该场景下小程序的微信登录、微信支付、备案、类目资质等门槛不必要，H5 是更合适的载体（与项目 README"H5 优先"主线一致）。

历史背景：`docs/12-wechat-miniprogram-launch-plan.md` 完成了 gap 分析，作为长期参考资料保留。当未来场景升级为"真实对大陆用户营业"时再重新评估。

当前阻塞：**无**。不阻塞 H5 演示部署，不阻塞任何当前开发切片。

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
