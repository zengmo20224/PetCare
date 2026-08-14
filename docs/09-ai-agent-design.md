# AI Agent 增量设计（V2）

> 阶段：**V2 增量设计**｜状态：**已实施（M8.0-M8.3/M8.5，2026-08-14）**——M8.4 图片 NSFW 审核延后｜设计日期：2026-08-01｜实施归档：2026-08-14
>
> **实施摘要**：M8.0（langchain4j + PgVector + BGE-zh embedding + 知识入库）/ M8.1（客服 RAG + 5 只读 Tool + SSE）/ M8.2（经营分析 Agent + 4 只读 Tool）/ M8.3（社区助手激活 + PostAssistantAgent + 文本审核 ModerationAgent 产 PostReport）/ M8.5（全量回归 + AI 用量页 + H5 SSE 前端对接 + 文档归档）。实施中同步完成的安全加固见 `docs/08` D-013 补充说明与相关 commit（RAG 召回护栏、客服医疗护栏补齐、审计字段分流、AI 限流、知识库重建幂等）。
>
> 关联文档：`docs/00-project-boundary.md` §3、`docs/01-architecture-design.md` §6、`docs/08-pending-decisions.md` D-004 / D-013、`AGENTS.md` §4
>
> 本文件只描述 V2 AI Agent 增量的设计与边界，不重复 V1 既有 AI 实现。V1 已激活部分见 D-004 修订说明。

---

## 0. 文档定位与读法

本设计是对 D-004（V1 部分激活：客服对话 + 经营分析报告）的**能力增量**，不是 V1 的返工。

- **V1 已交付（不动）**：`AiProviderClient` 端口、DeepSeek/Disabled 双实现、三层医疗护栏、客服对话（CUSTOMER_SERVICE / PET_CHAT）、经营分析报告（吃预聚合 JSON）、`AiProviderArchitectureTest` 边界守卫。
- **V2 增量（本文档范围）**：引入向量库（PgVector）做 RAG、把"对话接口"升级为"Agent"（具备受限工具调用能力）、激活社区助手与内容审核场景。

读到"V2"即指本设计；读到"V1 AI"即指 D-004 已激活部分。两者共存，不互相替代。

## 1. 设计目标

把 V1 的"无状态 prompt-stuffing 客服"和"吃预聚合 JSON 的分析报告"升级为：

1. **可检索**：客服不再把全部 FAQ/商品/服务塞进 system prompt，改为向量检索 top-K 注入，token 可控、可扩展。
2. **可行动**：Agent 在受控范围内调用业务工具（查我的订单、查预约、查营业时间、查服务价目），而不是只能"背 FAQ"。
3. **可生成**：激活社区发帖助手（现 401 关闭）与商品/活动文案生成，复用同一套 LLM 基建。
4. **可审核**：社区 UGC（文本+图片）接入 AI 辅助审核，与现有敏感词规则叠加，不替代。

非目标（明确不做，详见 §5）：多 Agent 编排框架、自主长任务 Agent、AI 直接落库写业务数据、疾病诊断。

## 2. 不可逾越的边界（继承且强化 V1）

下列约束**直接继承 V1**，V2 设计不得突破。任何与之冲突的条款以本节为准。

| # | 约束 | 来源 | V2 落地方式 |
|---|---|---|---|
| B1 | AI **不得直接访问数据库** | `AGENTS.md` §4 + `AiProviderArchitectureTest` | Agent 调用业务数据**只通过受限 Tool → 业务 Service**，向量库 PgVector 是**独立 PG 实例**，不与 MySQL 业务库混部，AI 读 PgVector 视为"读知识库"非"读业务库" |
| B2 | AI **不得做疾病诊断 / 药物处方 / 治疗承诺** | `AGENTS.md` §4 + `docs/00` §3 | 三层护栏全部保留：`HighRiskSymptomDetector`（前置）、`PetMedicalSafetyPolicy`（后处理）、`AiOutputSafetyPolicy`（通用输出） |
| B3 | 上游错误 / apiKey / 原始 body/headers **永不外泄** | D-004 修订 | 复用 `DeepSeekAiProviderClient` 的异常分级（503 / internalCode），langchain4j 层异常映射到同一套 |
| B4 | AI 建议**只能作管理参考，不能自动改业务数据** | `AGENTS.md` §4 | 经营分析 Agent 的所有 Tool 为**只读**；社区审核 Agent 输出"建议+置信度"，最终处置仍由 `moderation` 规则或人工确认 |
| B5 | 所有外部输入必须校验，错误响应不泄露内部 | `AGENTS.md` §4 | Agent 入口 DTO 校验、Tool 入参校验、向量召回结果回灌前过 `AiOutputSafetyPolicy` |

**新增 V2 专属约束**：

| # | 约束 | 理由 |
|---|---|---|
| B6 | 向量库 PgVector **不存业务真源数据**，只存"派生知识"（FAQ 文本、商品/服务描述、政策文档的 embedding 副本） | 避免双写一致性；业务真源仍在 MySQL，PgVector 是只读索引 |
| B7 | Agent 的 Tool 清单是**白名单**，每个 Tool 显式声明权限码与读写属性，运行时由 RBAC + 工具注册表双重校验 | 防止 Agent 被诱导调用未授权工具 |
| B8 | 流式输出（SSE）**仍必须经过输出护栏**，不能因为是流式就跳过 `PetMedicalSafetyPolicy` | 流式护栏在片段聚合后或按句边界检查，详见 §7.5 |

## 3. 架构总览

```text
┌─────────────────────────────────────────────────────────────────┐
│  H5 / 管理端（Vue）                                              │
│  - 客服 SSE 流式对话  - 经营分析  - 社区助手  - 审核建议面板     │
└──────────────────────────┬──────────────────────────────────────┘
                           │ REST + SSE
┌──────────────────────────▼──────────────────────────────────────┐
│  Controller 层（协议/鉴权/输入校验）                             │
│  AiConversationController  AdminAiAnalysisController            │
│  AiPostAssistantController  AdminModerationAgentController      │
└──────────────────────────┬──────────────────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────────────────┐
│  Agent 编排层（V2 新增）                                         │
│  CustomerServiceAgent / AnalyticsAgent / PostAssistantAgent /   │
│  ModerationAgent                                                │
│  职责：意图识别 → 检索增强 → Tool 调用编排 → 护栏 → 响应拼装    │
└───────┬────────────────────────────┬───────────────────┬───────┘
        │（受限 Tool 白名单）         │（RAG 检索）        │
┌───────▼──────────────┐  ┌──────────▼─────────┐  ┌──────▼────────┐
│ 业务 Service（只读） │  │ RAG 检索服务        │  │ AiProviderClient │
│ - ProductQueryService│  │ - PgVector 向量召回 │  │（端口，V1 保留）│
│ - BookingQueryService│  │ - 重排序 / top-K    │  │ - DeepSeek 实现 │
│ - StoreInfoService   │  │ - 知识入库 indexing │  │ - langchain4j  │
│ （不暴露 Mapper）    │  └──────────┬─────────┘  │   适配实现(V2) │
└──────────────────────┘             │            └────────────────┘
                                     │
                           ┌─────────▼──────────┐
                           │ PgVector（独立 PG） │
                           │ - ai_knowledge_doc  │
                           │ - ai_embedding      │
                           │ 派生知识副本，只读  │
                           └────────────────────┘
                           ┌────────────────────┐
                           │ MySQL（业务真源）   │
                           │ 不动，仍是唯一真源  │
                           └────────────────────┘
```

### 3.1 分层职责

| 层 | 职责 | V2 变化 |
|---|---|---|
| Controller | 协议、鉴权、输入校验、SSE 透传 | 新增审核 Agent 入口；其余复用 |
| **Agent 编排层**（新） | 意图识别、RAG 注入、Tool 调用编排、护栏编排、响应拼装 | V1 无此层，对话逻辑散在 `AiConversationApplicationService` |
| RAG 检索服务（新） | 向量化查询、top-K 召回、重排序、知识入库与更新 | V1 无（全量塞 prompt） |
| 业务 Service | 只读查询工具，供 Agent 调用 | 新增 `*QueryService` 只读门面，**不新增 Mapper 暴露** |
| AiProviderClient | LLM 调用端口 | V1 保留；新增 langchain4j 适配实现（端口实现层） |
| 护栏 domain | 输入/输出安全 | V1 三层全部保留，流式场景扩展（§7.5） |

### 3.2 与 V1 架构守卫的兼容性

`AiProviderArchitectureTest`（V1 既有）用反射强制 `provider` 包不依赖 Mapper/DataSource/MyBatis。V2 设计**不破坏**该守卫：

- **向量库访问不在 `provider` 包**：PgVector 访问封装在 `ai/rag/` 包，由 Agent 层调用，`provider` 包仍是纯 LLM 调用端口。
- **Tool 调用业务 Service 不在 `provider` 包**：Tool 定义在 `ai/agent/tool/`，依赖业务 Service 接口，不依赖 Mapper。
- **新增架构守卫**：`AiAgentArchitectureTest`（V2 新增）反射强制 `ai/agent/` 包对 `ai/rag/` 与业务 Service 的依赖是接口依赖，且 Tool 实现类不得直接依赖 `*Mapper`。

## 4. 向量库设计（PgVector）

### 4.1 选型结论与理由

**选定：PgVector（PostgreSQL 扩展）**。决策记录见 D-013。

| 候选 | 结论 | 理由 |
|---|---|---|
| **PgVector** ✅ | 采用 | langchain4j / spring-ai 一等支持；SQL+元数据过滤成熟；单门店数据量（FAQ/商品/服务描述 < 1 万条）远在 PgVector 舒适区；PG 实例可与未来审计/日志库复用 |
| Redis Stack | 不采用 | 当前 Caffeine 够用，V2 不引入 Redis 多目标变更；Redis 向量生态弱于 PgVector |
| Chroma / Qdrant | 不采用 | 独立向量服务运维成本高，单门店不值得 |
| MySQL JSON + Java 算余弦 | 不采用 | 天花板低，无法用 ANN 索引，仅 V1 过渡方案；既然 V2 要专门投入，一步到位 PgVector |

### 4.2 部署形态

- **独立 PG 实例**：与 MySQL 业务库物理隔离。理由：业务库零向量字段污染（B6）、PG 故障不影响业务核心流程、可独立备份与扩缩。
- docker-compose 新增 `postgres` 服务（镜像 `pgvector/pgvector:pg16`，内置扩展）。
- V2 不引入 PG 高可用，单实例 + 持久卷即可；高可用是 V3 议题。

### 4.3 Schema（PgVector 侧，仅存派生知识）

```sql
-- PgVector 库内，独立于 MySQL 业务库
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE ai_knowledge_doc (
    id           BIGSERIAL PRIMARY KEY,
    source_type  VARCHAR(32) NOT NULL,   -- FAQ / PRODUCT / SERVICE / STORE_INFO / POLICY
    source_id    BIGINT,                  -- 对应 MySQL 业务表主键（可空，如 POLICY 无对应行）
    chunk_index  INT NOT NULL,            -- 切片序号（一篇文档切多段）
    title        VARCHAR(255),
    content      TEXT NOT NULL,           -- 切片文本（ground truth，用于注入 prompt）
    metadata     JSONB,                   -- {category, price, tags...} 供元数据过滤
    source_hash  VARCHAR(64) NOT NULL,    -- 内容哈希，幂等更新判断
    created_at   TIMESTAMPTZ DEFAULT now(),
    updated_at   TIMESTAMPTZ DEFAULT now(),
    UNIQUE(source_type, source_id, chunk_index)
);

CREATE TABLE ai_embedding (
    doc_id       BIGINT NOT NULL REFERENCES ai_knowledge_doc(id) ON DELETE CASCADE,
    embedding    vector(384) NOT NULL,    -- 维度由 AI_EMBEDDING_DIM 决定（默认 384 = all-MiniLM-L6-v2）
    model_name   VARCHAR(64) NOT NULL,    -- 记录用哪个模型生成，换模型时全量重建
    created_at   TIMESTAMPTZ DEFAULT now(),
    PRIMARY KEY(doc_id, model_name)
);
-- 注：换 embedding 模型时维度可能变（如厂商 API 可能是 768/1024），
-- 需 DROP + 重建 ai_embedding 表（ai_knowledge_doc 文本不动），并同步 AI_EMBEDDING_DIM。
-- 多模型共存的 model_name 已预留，但同表 vector 列类型维度需统一（不同维度需分表）。

-- HNSW 索引（PgVector 推荐，单门店规模查询 < 50ms）
CREATE INDEX ai_embedding_vec_idx ON ai_embedding
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);
```

设计要点：
- **`ai_knowledge_doc` 与 `ai_embedding` 分离**：换 embedding 模型时只需重建 embedding 表，知识文本不动。
- **`source_hash`**：知识入库任务幂等，内容未变不重 embed，省 token。
- **`metadata` JSONB**：检索时按元数据过滤（如"只在 SERVICE 类目召回"），避免召回无关商品。
- **MySQL 业务库零改动**：业务真源（商品价/库存/服务时段）仍在 MySQL，PgVector 只存"给 AI 看的描述副本"。

### 4.4 知识入库流程（离线/定时）

```text
MySQL 业务库（FAQ/商品/服务/门店配置/政策文档）
    │
    ▼ [定时任务 / 事件触发]
KnowledgeIndexingService（V2 新增，在 ai/rag/ 包）
    1. 按源类型拉取业务数据（走业务 Service 只读接口，不走 Mapper）
    2. 切片（按段落/句号 + overlap，复用 langchain4j DocumentSplitter）
    3. 计算 source_hash，跳过未变更项
    4. 调 EmbeddingModel 批量 embed
    5. 写 PgVector（upsert by source_type+source_id+chunk_index）
```

触发时机（三选一或组合）：
- 定时（每日凌晨全量校验 hash，增量更新）；
- 管理端变更后事件（如管理员改了商品描述，发 MQ/内存事件触发单条重索引）；
- 手动重建（管理端"重建知识库"按钮，带权限码 `ai:knowledge:rebuild`）。

### 4.5 RAG 检索流程（在线）

```text
用户 query
    │
    ▼
EmbeddingModel.embed(query)              # 同一模型向量化
    │
    ▼
PgVectorContentRetriever                 # top-K（默认 5）+ metadata 过滤
    │
    ▼
Reranker（可选，V2.1）                   # 小模型重排，先不做
    │
    ▼
拼接为 grounding context 注入 system prompt
    │
    ▼
Agent 编排（可能再调 Tool 补实时数据，如"我的订单"）
```

召回内容回灌前过 `AiOutputSafetyPolicy` 检查（防向量库被注入污染，B5）。

## 5. Agent 场景设计

本设计覆盖用户选定的三个场景。每个 Agent 都是"编排单元"，不共享可变状态。

### 5.1 智能客服 Agent（升级 V1 客服）

**挂载**：`ai/agent/CustomerServiceAgent`，复用 `AiConversationController` 入口。

**V1 → V2 变化**：

| 维度 | V1 现状 | V2 升级 |
|---|---|---|
| 上下文构建 | `CustomerServiceContextBuilder` 全量塞 FAQ/服务/商品/门店到 system prompt | 改为 query embed → PgVector top-K 注入；`ContextBuilder` 退化为"召回结果 + 实时 Tool 结果" 拼装 |
| 数据时效 | 静态塞入，价格/库存可能过期 | Tool 实时查询（` getProductPrice`/`getStockStatus`/`getStoreHours`） |
| 能力 | 只能"背 FAQ" | 能查"我的订单状态""我的预约"（需用户身份 Tool） |
| 输出 | 同步 | SSE 流式（§7.5） |
| 护栏 | 三层 | 三层保留，流式扩展 |

**Tool 白名单**（均为只读，权限码见 §6.3）：

| Tool | 入参 | 出参 | 说明 |
|---|---|---|---|
| `searchFaq` | keyword | top-3 FAQ | 兜底，向量召回不足时用 |
| `getProductInfo` | productId | 商品快照（名称/价格/库存状态） | 价格库存实时，不信向量库缓存 |
| `getServiceInfo` | serviceId | 服务快照（时长/价格/可约时段摘要） | |
| `getStoreInfo` | — | 营业时间/地址/联系方式 | |
| `getMyOrderStatus` | orderId | 订单状态（需当前用户身份校验） | 用户身份 Tool，越权校验在 Tool 内 |
| `getMyBookingStatus` | bookingId | 预约状态 | 同上 |

**不可调用**：钱包余额、他人订单、管理端写接口。白名单未列即禁。

### 5.2 经营分析 Agent（升级 V1 分析报告）

**挂载**：`ai/agent/AnalyticsAgent`，复用 `AdminAiAnalysisController` 入口。

**V1 → V2 变化**：

| 维度 | V1 现状 | V2 升级 |
|---|---|---|
| 数据来源 | `*AnalyticsAggregator` 预聚合 JSON 全量塞 prompt | 预聚合 JSON 仍提供"概览"；Agent 可按需调 Tool 拉细分（如"某品类近 7 天销量"） |
| 深度 | 受限于预聚合粒度 | 可下钻，但仍**只读**，Tool 不写库 |
| 输出 | 整段报告 | 结构化报告（JSON schema：摘要/趋势/异常/建议），管理端渲染 |

**Tool 白名单**（只读，权限码 `ai:analysis:generate`）：

| Tool | 说明 |
|---|---|
| `getSalesTrend` | 指定时间窗 + 品类/服务维度的销售趋势 |
| `getBookingFunnel` | 预约漏斗（创建/确认/完成/取消） |
| `getCommunityMetrics` | 发帖/互动/举报指标 |
| `getActivityEffect` | 营销活动关联商品/服务的连带率 |

Tool 内部走 `*AnalyticsAggregator`，不新增数据访问路径。**所有 Tool 只读**（B4）。

### 5.3 社区助手 + 审核 Agent（激活 V1 关闭功能）

**挂载**：
- 助手：`ai/agent/PostAssistantAgent`，激活现 401 关闭的 `AiPostAssistantController`。
- 审核：`ai/agent/ModerationAgent`，新增 `AdminModerationAgentController`（管理端辅助）。

#### 5.3.1 发帖助手 Agent

**V1 → V2 变化**：
- 解除 `AiPostAssistantController.resolveCurrentUserId()` 的硬编码 401（该注释"User JWT 未实现"已过期，D-004 后 User JWT 已实现）。
- 复用 `PostAssistantFactPolicy`：只用用户提供的事实，不编造。
- 升级：用户输入"帮我写个帖子"，Agent 结合**用户宠物档案**（品种/年龄，走 Tool 只读）生成个性化草稿，用户确认后走原发帖事务。

**Tool 白名单**（只读）：

| Tool | 说明 |
|---|---|
| `getMyPetProfile` | 当前用户的宠物档案（用于个性化生成） |
| `getRecentHotTopics` | 近期社区热门话题（用于蹭热度，可选） |

生成结果**仅作草稿**，用户点"发布"才走 `CommunityPostApplicationService` 发帖事务（B4）。

#### 5.3.2 内容审核 Agent

**定位**：**辅助**现有敏感词规则，不替代。输出"建议 + 置信度 + 命中类型"，最终处置由 `moderation` 模块规则或人工确认。

**两条子链路**：

| 子链路 | 触发 | 模型形态 | 输出 |
|---|---|---|---|
| 文本审核 | 发帖/评论提交时异步 | LLM 分类（违规/疑似/正常 + 类型：广告/辱骂/医疗误导/其他） | 建议分值，超阈值进 `PostReport` 人工队列 |
| 图片审核 | 发帖上传图片时异步 | ONNX Java 原生推理（nsfw_model，见 §8） | NSFW 分值，超阈值拦截 + 进人工队列 |

**关键约束**：
- AI 审核**不直接删帖/封号**（B4），只产 `PostReport` 记录，由 `moderation` 既定规则或管理员处置。
- 文本审核结果**不展示给用户**（避免对抗），仅管理端可见。
- 误判申诉走现有 `PostReport` 流程，不新建。

## 6. 工具调用（Tool）规范

### 6.1 Tool 定义契约

```java
// ai/agent/tool/ 目录，所有 Tool 实现此契约
public interface AgentTool {
    String name();                    // 唯一名，白名单注册用
    String description();             // 给 LLM 的能力说明
    String requiredPermission();      // RBAC 权限码（null=登录即可）
    boolean readOnly();               // true=只读，经营分析/客服/助手全 true
    Class<?> inputSchema();           // 入参 DTO 类型（JSON schema 校验）
    Class<?> outputSchema();          // 出参 DTO 类型
    Object invoke(Object input, AgentContext ctx);  // ctx 含当前用户/权限上下文
}
```

### 6.2 Tool 注册表与运行时校验

- **静态白名单**：每个 Agent 显式声明可用的 Tool 列表（构造期注入），运行时不可扩。
- **双重校验**：Tool 调用前校验①该 Agent 是否声明了此 Tool ②当前用户是否持有 `requiredPermission`。任一失败抛 `ToolNotAuthorizedException`，不调 LLM。
- **审计**：每次 Tool 调用记 `ai_tool_call_log`（agent/tool/userId/args 摘要/耗时/成功），与 `AiUsageLog` 关联。**该表在 MySQL 业务库**（与 `ai_usage_log` 同库，外键 `usage_log_id` 关联），**不在 PgVector**——PgVector 只存派生知识副本（B6），审计日志属业务数据。迁移脚本：`migration-phase17-ai-tool-call-log.sql`（CI-DB-019）。

### 6.3 RBAC 权限码（V2 新增）

权限码分配避开 phase7（7001-7049）与 phase15 钱包（7051-7054），V2 AI Agent 用 **7055-7056** 段。迁移脚本：`migration-phase16-ai-agent-rbac.sql`（CI-DB-018）。

| ID | 权限码 | 授权角色 | 说明 |
|---|---|---|---|
| 7048 | `ai:analysis:generate`（V1 已有） | SUPER_ADMIN / ADMIN | 经营分析（不变） |
| 7049 | `ai:usage:read`（V1 已有） | SUPER_ADMIN / ADMIN | AI 用量查看（V2 补建前端页） |
| 7055 | `ai:moderation:review` | SUPER_ADMIN / ADMIN / **MODERATOR** | 查看审核建议（管理端 RBAC） |
| 7056 | `ai:knowledge:rebuild` | SUPER_ADMIN | 手动重建知识库（管理端 RBAC） |
| — | `ai:customer-service:chat` | 所有登录用户 | 客服对话（**用户端权限**，运行时由 SecurityContext 校验登录态，不进 `admin_permission` 表） |
| — | `ai:post-assistant:use` | 所有登录用户 | 发帖助手（**用户端权限**，同上） |

**MODERATOR 角色**（role_id=4，V2 新增）：内容审核员，只获 `ai:moderation:review`（7055），不能重建知识库（7056 仅 SUPER_ADMIN）。由 `migration-phase16` 创建并绑定。

## 7. 关键技术与实现策略

### 7.1 LLM 框架引入

**引入 langchain4j**（决策见 D-013），作为 `AiProviderClient` 端口的**实现层**之一，不替换端口。

```text
AiProviderClient（端口，V1 保留）
├── DisabledAiProviderClient（V1，兜底）
├── DeepSeekAiProviderClient（V1，手写 HTTP）
└── LangChain4jProviderClient（V2 新增，包 langchain4j 适配）
```

理由：
- 不破坏 V1 端口契约与 `AiProviderArchitectureTest`；
- langchain4j 的 RAG / ChatMemory / AI Services / Tool 调用能力直接复用；
- DeepSeek 手写实现保留作降级路径（langchain4j 故障时配置切换）。

依赖（pom，V2 阶段加）：
- `dev.langchain4j:langchain4j-spring-boot-starter`
- `dev.langchain4j:langchain4j-pgvector`（embedding store）
- `dev.langchain4j:langchain4j-open-ai`（DeepSeek 走 OpenAI 兼容协议）

### 7.2 Embedding 模型

- **默认**：`all-MiniLM-L6-v2`（384 维），通过 ONNX 在 Java 内推理（spring-ai 的 ONNX embedding 支持成熟），无需调外部 embedding API，零额外费用。
- **可选**：切换为 DeepSeek/其他厂商的 embedding API（配置切换，schema 中 `model_name` 字段支持多模型共存与切换）。
- 换模型 = 全量重建 embedding 表（§4.3 已预留 `model_name`）。

#### 7.2.1 模型文件分发（C-6 修订）

- **存放**：项目根 `models/all-MiniLM-L6-v2/` 目录（`model.onnx` ~90MB + `tokenizer.json` ~71KB）。
- **不入 git**：`.gitignore` 已排除 `models/**/*.onnx/*.bin/*.safetensors`；构建/部署时按 `models/README.md` 下载并校验 sha256。
- **CI 校验**：模型文件 sha256 记录在 `models/README.md`，构建脚本拉取后比对哈希，不一致则失败。
- **NSFW 模型**：渠道待定（HF 已鉴权墙，见 §8），M8.4 决策（D-013 Q-4）后再补分发方式。

### 7.3 ChatMemory（对话记忆）

- 用 langchain4j `MessageWindowChatMemory.withMaxMessages(20)`，对齐 V1 `PromptFactory.MAX_HISTORY_TURNS=10`（一轮=2 message）。
- **持久化**：自定义 `JdbcChatMemoryStore`，落 MySQL **新建 `ai_chat_memory` 表**（CI-DB-020，`migration-phase18`），存 langchain4j 序列化的 ChatMessage JSON。**不复用 V1 `ai_message` 业务表**——V1 ai_message 是结构化对话历史（role/content/conversation_id，供客服记录展示），职责分离，避免污染。
- `storeRetrievedContentInChatMemory=false`：检索片段不进记忆，避免记忆膨胀与污染。
- 不引入 Redis（B 约束：缓存仍 Caffeine/MySQL）。

### 7.4 SSE 流式输出

- 后端：langchain4j `TokenStream` → Spring `SseEmitter` / `Flux<String>`。
- 前端：H5 用 `EventSource`；管理端用 `fetch` + ReadableStream（SSE 鉴权需放 header，EventSource 不支持自定义 header，管理端用 fetch stream）。
- **H5 SSE 鉴权方案**（EventSource 不能带 Authorization header）：采用"短时 query token"——客户端先调普通 REST 接口换取 30s 有效的一次性 SSE token，再以 `GET /api/v1/ai/conversations/{id}/stream?token=xxx` 建立 EventSource。后端 SSE 端点校验 query token 后建立流。约束：①token 一次性、短时效（30s），不放入长时 JWT；②访问日志对 token 值脱敏（仅记前 6 位）；③token 用后即焚，存 Caffeine（不引入 Redis）。
- 流式不等于无护栏，见 B8 与 §7.5。

### 7.5 流式护栏（B8 落地）

流式输出不能逐 token 过护栏（误判高、延迟大），策略：

1. **前置护栏照常**：`HighRiskSymptomDetector`（PET_CHAT 前置拦截）在调 LLM 前执行，不进入流式。
2. **后置护栏分句**：流式按句边界（。！？.!? 换行）聚合，每完整句过 `PetMedicalSafetyPolicy` + `AiOutputSafetyPolicy`，命中则该句替换为兜底文案，并标记会话进入"安全降级"（后续输出降级为兜底）。
3. **结束护栏**：流结束后整段再过一次 `AiOutputSafetyPolicy`，写 `AiUsageLog`。

### 7.6 限流与降级

- **限流**：在 `AiProviderClient` 外包 Resilience4j `@RateLimiter`（按用户/IP/token-per-minute 三维），配置走 `application.yml`。
- **降级链**：langchain4j Provider 故障 → 切 DeepSeek 手写 Provider → 仍故障 → Disabled 桩返回 503 → 前端展示"AI 暂不可用，已为你转人工"兜底文案。
- Agent Tool 调用失败不阻塞对话，返回"该能力暂不可用"，LLM 据此回退到纯 RAG 回答。

## 8. 图片审核：ONNX Java 原生推理

社区图片审核（§5.3.2）采用 ONNX，**不引入 Python sidecar**（运维成本与单门店定位不符）。

- **模型**：`GantMan/nsfw_model`（Mobilenet v2，5 类，已核实 2.1k stars），导出 ONNX。
- **Java 栈**：`com.microsoft.onnxruntime:onnxruntime` + `ai.djl:djl-api`（图像预处理：resize/normalize）。
- **调用点**：`ai/moderation/image/NsfwImageClassifier`，被 `ModerationAgent` 异步调用。
- **边界**：模型输出分值 → 超阈值产 `PostReport(type=NSFW)` → 走 moderation 既定处置（B4）。模型文件不入 git（>50MB），走 release 附件或对象存储拉取，CI 校验哈希。

## 9. 数据流与一致性

### 9.1 业务数据 → 知识库的同步

```text
管理员改商品描述（MySQL）
    │
    ▼ 事件 / 定时
KnowledgeIndexingService 重算 source_hash
    │ 未变：跳过
    │ 变更：重新切片 + embed → upsert PgVector
```

- **最终一致**：知识库允许短暂滞后（秒级～分钟级），客服回答价格/库存**不信向量库**，必须走实时 Tool（§5.1）。这消除了"知识库过期导致报价错"的风险。
- **故障隔离**：PgVector 故障时，RAG 召回失败 → Agent 降级为纯 Tool 回答（"让我帮你查一下"），不阻塞业务。

### 9.2 不引入分布式事务

知识库更新不做跨库事务（MySQL ↔ PgVector），理由：
- 知识库是派生索引，丢失可从 MySQL 重建（§4.4 全量重建能力）。
- 跨库事务复杂度与单门店定位不符。
- 容忍策略：`source_hash` 幂等 + 定时校验补偿。

## 10. 测试策略

继承 `docs/05-testing-and-verification.md` 风险驱动原则。AI Agent 属高风险（涉及权限、数据隔离、医疗安全、流式护栏），必须有直接测试。

### 10.1 必须直接测试的规则

| # | 规则 | 测试形式 |
|---|---|---|
| T1 | Agent Tool 白名单：未声明 Tool 不可调 | 单测 `AgentToolRegistryTest` |
| T2 | Tool RBAC：无权限码用户调用被拒 | 单测 + MockMvc |
| T3 | 客服 Tool 越权：用户 A 查用户 B 订单被拒 | 集成测试 |
| T4 | 经营分析 Tool 全只读：任意调用不产生写 | 单测反射断言 `readOnly=true` + 行为验证 |
| T5 | 流式护栏：PET_CHAT 含高危症状，流中仍拦截 | 集成测试（mock 流式 LLM 输出医疗承诺） |
| T6 | 向量库故障降级：PgVector 挂，Agent 降级纯 Tool 回答 | 集成测试 |
| T7 | 知识入库幂等：内容未变不重 embed | 单测 |
| T8 | 图片审核超阈值产 PostReport，不直接删 | 集成测试 |
| T9 | 社区助手生成结果不自动发布 | 集成测试 |

### 10.2 架构守卫测试（结构性契约，对标 V1 `AiProviderArchitectureTest`）

- **`AiAgentArchitectureTest`**（V2 新增）：反射强制
  - `ai/agent/` 包不依赖 `*Mapper`（只依赖业务 Service 接口）；
  - 所有 `AgentTool` 实现的 `readOnly()` 与声明的写行为一致（经营分析/客服/助手 Tool 必须 `true`）；
  - 每个 Agent 的 Tool 集合是 final 不可变。
- **`AiRagArchitectureTest`**：反射强制 `ai/rag/` 包不依赖 `ai/provider/` 反向（provider 不知道 RAG 存在）。

### 10.3 LLM 调用替身

- 测试态默认 `provider-enabled=false`（沿用 V1 test profile），走 Disabled 桩。
- 需要验证 Agent 编排逻辑时，用 `MockAiProviderClient`（V1 既有）注入确定性 LLM 输出。
- **不打真实 DeepSeek**（成本 + 不确定性），与 V1 策略一致。

### 10.4 PgVector 集成测试

- 用 Testcontainers 起 `pgvector/pgvector:pg16`（与 MySQL IT 同模式），打 `@Tag("tc-pgvector")`。
- CI 默认不跑（对标 `tc-mysql` 策略），本地或专门阶段触发。

## 11. 配置项（V2 新增）

走环境变量，不入库（沿用 D-004 策略）。纳入配置项登记表（§13）。

| 配置项 | 默认 | 说明 |
|---|---|---|
| `AI_AGENT_ENABLED` | false | Agent 能力总开关（独立于 V1 `AI_PROVIDER_ENABLED`） |
| `AI_RAG_ENABLED` | false | RAG 检索开关 |
| `PGVECTOR_HOST/PORT/DB/USER/PASSWORD` | — | PgVector 连接（独立于 MySQL） |
| `AI_EMBEDDING_MODEL` | all-MiniLM-L6-v2 | embedding 模型名 |
| `AI_EMBEDDING_DIM` | 384 | 维度 |
| `AI_RAG_TOP_K` | 5 | 召回数 |
| `AI_AGENT_RATE_LIMIT_PER_MIN` | 20 | 单用户限流 |
| `AI_MODERATION_NSFW_THRESHOLD` | 0.8 | NSFW 拦截阈值 |
| `AI_KNOWLEDGE_REBUILD_CRON` | 0 0 3 * * * | 知识库定时校验 |

降级原则：任一开关 false，对应能力不可达但不影响应用启动与其他功能（对标 V1 Disabled 桩哲学）。

## 12. 交付路线（M8 切片）

纳入 `docs/02-task-breakdown.md`。建议纵向切片：

| 切片 | 目标 | 退出条件 |
|---|---|---|
| **M8.0 基建** | langchain4j + PgVector + embedding + 知识入库 | 离线能把 FAQ/商品/服务索引入 PgVector，召回归一化 cosine 验证通过 |
| **M8.1 客服 RAG 升级** | 客服接 RAG + Tool 白名单 + SSE | 客服能查实时库存/价格/订单状态；流式护栏测试通过 |
| **M8.2 经营分析下钻** | AnalyticsAgent + 只读 Tool + 结构化报告 | 管理端报告可下钻；任意 Tool 调用不产生写 |
| **M8.3 社区助手激活** | 解除 401 + PostAssistantAgent + 审核文本 Agent | 发帖助手可用；文本审核产 PostReport 不直接删 |
| **M8.4 图片审核** | ONNX nsfw + 图片审核 Agent | 图片上传异步审核，超阈值拦截 + 人工队列 |
| **M8.5 收口** | 全量回归 + 用量页 + 文档归档 | 所有架构守卫通过；E2E 关键路径通过 |

每个切片满足 `docs/02` §5 完成定义（真实可运行 + 业务规则生效 + 测试通过 + 失败路径有反馈 + diff 干净）。

## 13. 文档与配置项联动

本设计落地时需同步：
- `docs/00-project-boundary.md` §3：V2 范围条目（本文档引用）。
- `docs/01-architecture-design.md` §6：清掉过期"AI 全部关闭"表述，补 V2 Agent 架构。
- `docs/02-task-breakdown.md`：新增 M8 切片。
- `docs/08-pending-decisions.md`：新增 D-013（向量库/Agent/LLM 框架选型）。
- `docs/配置项登记表.md`：新增 AI Agent 相关 CI（§11 配置项、PgVector schema、09 文档本身）。

## 14. 未决事项

| ID | 事项 | 影响 | 默认处理 |
|---|---|---|---|
| Q-1 | embedding 用本地 ONNX 还是厂商 API | 成本/质量/延迟 | 默认本地 ONNX；若中文召回质量不足，M8.1 后评估切厂商 API |
| Q-2 | 知识入库触发：纯定时 vs 事件驱动 | 实时性 vs 复杂度 | M8.0 先定时，M8.1 视体验加事件 |
| Q-3 | 是否引入 Reranker | 召回质量 | M8 默认不做，作为 M8.1+ 优化项 |
| Q-4 | 图片审核模型自托管 vs 商用 API | 合规/成本 | 默认自托管 ONNX；商用 API 作为降级备选 |

以上均为技术实现细节，不阻塞设计落地，由实施 Agent 依据代码与体验保守选择。

## 15. 风险与回退

| 风险 | 回退路径 |
|---|---|
| langchain4j 与 Spring Boot 3.3 兼容性问题 | 退回 V1 DeepSeek 手写 Provider，RAG 用自写余弦检索（性能降但可用） |
| PgVector 单实例故障 | Agent 降级纯 Tool 回答；知识库可从 MySQL 全量重建 |
| RAG 召回质量差 | top-K 调参 / 加 Reranker / 退回 V1 全量塞 prompt（数据量小仍可承受） |
| 流式护栏误判 | 分句策略可配置化，必要时退回非流式 + 整段护栏 |
| 图片审核误判 | 阈值可配置 + 人工队列兜底，不自动删 |

---

**本设计基线确立后，实施 Agent 按 M8 切片顺序落地。任何超出本设计边界的新能力（如多 Agent 编排、自主长任务、疾病诊断辅助）必须先回 `docs/08-pending-decisions.md` 评估，不得顺手实现。**
