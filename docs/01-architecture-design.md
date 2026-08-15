# 架构设计

## 1. 总体结构

项目保持单仓库、模块化单体：

```text
响应式用户 H5（frontend/miniapp）
管理端 Web（frontend/admin-web）
                |
         Spring Boot REST API
                |            \
              MySQL        PgVector（AI 派生知识索引，
            （业务真源）      只存 RAG 副本，见 D-013/B6）
```

`frontend/miniapp` 继续使用 UniApp，但当前主目标是 H5。暂不重命名目录，避免对现有构建、测试和引用造成无业务价值的迁移。

## 2. 用户端原则

- 以 `dev:h5` 和 `build:h5` 为主要运行和构建命令。
- 页面应适配常见手机宽度，并在桌面浏览器可用。
- 用户端直接接入真实 API；只允许在开发测试中使用明确标识的测试身份和测试数据。
- 微信特有 API 必须隔离，不能成为 H5 核心流程的依赖。

## 3. 后端模块

后端继续按业务域组织：

- 认证与用户资料
- 宠物和地址
- 服务、排班和预约
- 商品、购物车和订单
- 钱包余额（管理端手工台账，用于商品订单与服务预约的钱包抵扣；扣款与扣库存/容量必须同事务，详见 D-010/D-012）
- 社区和审核
- 营销活动
- 管理端权限

Controller 负责协议和输入校验，Service 负责业务规则和事务，Mapper 负责数据访问。禁止 Controller 直接编排 Mapper 完成业务流程。

### 应用服务编排（Facade）

部分业务域使用"应用服务（Application Service）"作为 Facade，在单事务内编排多个子域操作并拼装聚合响应：

- `CommunityPostApplicationService`：发帖和评论是跨子域用例——单事务内要同时完成内容落库、敏感词审核、标签关联、图片保存、评论计数自增、作者通知。这些操作分属 post、comment、tag、image、moderation、notification 六个子域，必须在一个事务边界内完成。该类同时承载公开列表查询（批量加载标签、封面图、作者信息并拼装为公开 DTO），因为多个列表端点共享同一套批量加载逻辑，集中实现避免重复。子域的细粒度 `IService<T>`（PostService、PostCommentService 等）提供单表 CRUD 能力，跨子域编排统一由应用服务承担。
- `AdminManagementServiceImpl`：管理后台统一操作入口，每个写操作在同一事务内完成业务变更并记录审计日志。

应用服务不是"上帝类"的借口：它只承担用例编排和聚合响应拼装，单表读写规则和状态校验仍下沉到对应领域服务或 Mapper。


## 4. 关键数据一致性

- 预约：数据库约束、锁或原子更新必须防止超卖时段。
- 订单：服务端计算金额、保存快照、校验库存并在事务中扣减。
- 状态机：只允许文档和代码定义的合法状态流转。
- 权限：用户数据按当前用户隔离，后台操作按角色和权限校验。
- 公开读取：使用专门 DTO，避免实体字段意外泄露。

## 5. 认证策略

- H5 正式公开登录已决定并实现（P-001）：手机号 + 密码注册登录，安全问题找回密码；`@Profile("test")` 测试登录仅自动化测试可用，生产环境自动关闭。
- 管理端 JWT 已迁移 HttpOnly Cookie 双轨（2026-08-15）：Cookie 优先、`Authorization: Bearer` 回退，见 `docs/11` §4。
- 微信登录为 demo 形态（P-002）：后端三态 Provider（disabled/mock/real）；其接入不改变内部用户身份和授权模型。
- 测试身份入口不得用于生产环境。

## 6. 营销与 AI

营销活动是当前业务模块，只实现活动展示、后台维护和商品/服务关联，不进入复杂价格计算链路。

AI 分两个阶段，边界由 `docs/08-pending-decisions.md` D-004（V1）与 D-013（V2）共同约束：

- **V1（D-004 修订，2026-07-21，已激活）**：用户端智能客服对话 + 管理端经营分析报告接入真实 DeepSeek；`AiProviderClient` 端口 + DeepSeek/Disabled 双实现 + 三层医疗护栏（`HighRiskSymptomDetector`/`PetMedicalSafetyPolicy`/`AiOutputSafetyPolicy`）+ `AiProviderArchitectureTest` 边界守卫（强制 provider 包不依赖 Mapper/DataSource）。
- **V2（D-013，2026-08-01 设计，M8.0-M8.3/M8.5 已实施 2026-08-14）**：PgVector 向量库 RAG（独立 PG 实例，只存派生知识副本）+ Agent 受限工具调用（`AgentToolRegistry` 白名单 + RBAC 双校验 + `ai_tool_call_log` 审计；客服/经营分析/社区助手三类 Agent）+ 社区助手激活（仅草稿不自动发布）+ 文本内容审核（LLM 分类产 `PostReport` 进人工队列，不直接删）+ SSE 流式（H5 fetch stream）+ AI 用量查看页（管理端）。**M8.4 图片 NSFW 审核延后**。架构详见 `docs/09-ai-agent-design.md`。

两个阶段共用的不可逾越约束：① AI 不得直接访问数据库（V1 由 `AiProviderArchitectureTest` 强制，V2 由 `AiAgentArchitectureTest` 强制 `ai/agent/` 只依赖业务 Service，Agent 访问数据只通过受限 Tool）；② AI 不得做疾病诊断/药物处方/治疗承诺（三层护栏保留，M8.3 后客服/Agent 路径全覆盖）；③ AI 建议只能作参考，不能自动改业务数据（经营分析 Tool 全只读、审核只产建议记录）。

## 7. 交付架构原则

按纵向切片交付：一个切片应尽量同时包含必要的 API、H5 页面、管理能力和测试。架构调整只有在解决当前切片的真实问题时才进行。
