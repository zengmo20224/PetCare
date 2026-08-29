# PetCare O2O 🐾

**[简体中文](README.md) ｜ [English](README_EN.md)**

[![CI](https://github.com/zengmo20224/PetCare/actions/workflows/ci.yml/badge.svg)](https://github.com/zengmo20224/PetCare/actions/workflows/ci.yml)

> **面向单体宠物门店的 O2O 全栈平台**：服务预约 · 商品零售 · 社区互动 · 钱包台账 · AI Agent —— 本科毕业设计项目
>
> **M1–M8 全部交付** ｜ AI Agent（RAG + 受限 Tool 调用 + SSE 流式）已上线 ｜ 白盒安全审计 → 修复 → 回归闭环 ｜ Docker 一键部署 + VPS 实机演示

---

## ✨ 这个项目值得看的五个点

### 1. AI Agent 全链路自研，不是套壳调 API

三类 Agent（智能客服 / 经营分析 / 社区助手）运行在统一编排层：意图识别 → PgVector RAG 检索增强（HNSW 向量索引）→ 受限只读 Tool 白名单调用（客服 5 个 / 经营分析 4 个）→ 三层医疗护栏 → SSE 流式响应。Embedding 用本地 ONNX 推理（BGE 中文小模型，384 维），不依赖外部 embedding API，零额外费用。全部计费入口三层限流（分钟 / 用户日额 / 全站日额），调用按模型 / token / 成败入审计（不含对话原文）。

### 2. 架构边界不靠自觉，靠测试强制

三个反射架构守卫测试进 CI：`AiProviderArchitectureTest` / `AiAgentArchitectureTest` / `AiRagArchitectureTest` 强制 AI 代码禁止依赖 Mapper / DataSource / MyBatis，Tool 必须 `readOnly()`，业务数据只能经白名单 Tool → 业务 Service 访问。PgVector 只存派生知识索引（可随时从 MySQL 重建），业务真源唯一。AI 建议永不自动写业务数据，流式输出片段同样要过输出护栏。

### 3. 并发与一致性在真实 MySQL 上验证，而不是 mock

用 Testcontainers 拉起真实 MySQL / PgVector 跑集成测试（订单幂等并发兜底、库存并发不超卖、钱包扣款与扣库存同事务原子、预约并发不超订、状态机非法流转拒绝）——详见[测试与质量保障](#-测试与质量保障)。

### 4. 白盒安全审计走完闭环

自查发现 2 高危（订单无幂等可重放超卖、全站无限流可暴力破解）+ 5 中危（上传无魔数校验、用户枚举、存储型 XSS、RBAC 角色断裂、Spring CVE），全部修复并回归；上线前部署加固清单收口（生产凭据 / 公网域名 2 项留待部署时执行）。详见[安全审计报告](docs/11-security-audit-2026-07.md)。

### 5. 工程治理按真实规范走

配置管理依据 IEEE Std 828-2012（76 配置项登记、8 个基线 tag、4 个真实变更申请单实例）；Conventional Commits；Jenkins 8 阶段流水线 + GitHub Actions 三 job 并行；Docker Compose 一键部署 + 健康检查，已部署 VPS 实机演示（Caddy + HTTPS）。

---

## 📌 项目简介

PetCare O2O 是一套面向单体宠物门店的模块化单体应用，覆盖宠物服务预约、商品零售、钱包台账、社区互动、营销活动和 AI Agent 六大业务域。系统采用响应式 H5 优先策略，先交付可在手机和桌面浏览器使用的 Web 应用，微信小程序以 demo 形态同步可演示。

| 端 | 技术栈 | 入口 |
|---|---|---|
| **管理端 PC Web** | Vue 3 + Vite + Element Plus | http://localhost:8080 |
| **用户端 H5 / 小程序** | UniApp + Vue 3 + wot-design-uni | http://localhost:8081 |
| **后端 API** | Spring Boot 3.3 + MyBatis-Plus + MySQL 8 + PgVector | http://localhost:8082 |
| **CI/CD** | Jenkins + GitHub Actions + Docker Compose | http://localhost:9090 |

---

## 📱 UI 展示（2026-08 改版，运行中的应用实拍）

### 用户端 H5

> 五页均为**当前运行中的应用**统一视口（390×844）逐页截取，数据来自真实后端 + 演示媒体种子（68 张 Wikimedia/Unsplash 授权图片，逐图署名见 `uploads/images/seed/CREDITS.md`）。

<table>
  <tr>
    <td align="center"><img src="docs/assets/screenshots/home.png" width="160" alt="首页"/></td>
    <td align="center"><img src="docs/assets/screenshots/services.png" width="160" alt="服务预约"/></td>
    <td align="center"><img src="docs/assets/screenshots/products.png" width="160" alt="商品零售"/></td>
    <td align="center"><img src="docs/assets/screenshots/community.png" width="160" alt="社区互动"/></td>
    <td align="center"><img src="docs/assets/screenshots/profile.png" width="160" alt="个人中心"/></td>
  </tr>
  <tr>
    <td align="center"><sub><b>首页</b></sub></td>
    <td align="center"><sub><b>服务预约</b></sub></td>
    <td align="center"><sub><b>商品零售</b></sub></td>
    <td align="center"><sub><b>社区互动</b></sub></td>
    <td align="center"><sub><b>个人中心</b></sub></td>
  </tr>
</table>

<p align="center">
  <img src="docs/assets/screenshots/services-desktop.png" width="800" alt="桌面端响应式"/><br/>
  <sub><b>桌面端响应式布局</b>（1280 宽视口，服务页——同一套代码自适应）</sub>
</p>

### 管理端 PC Web

> 1440 宽桌面视口实拍：运营总览、商品管理、社区内容治理、AI 调用计量（token 消耗与结果审计，不含对话原文）。

<table>
  <tr>
    <td align="center"><img src="docs/assets/screenshots/admin-dashboard.png" width="440" alt="运营总览"/></td>
    <td align="center"><img src="docs/assets/screenshots/admin-products.png" width="440" alt="商品管理"/></td>
  </tr>
  <tr>
    <td align="center"><sub><b>运营总览</b>（在售商品 / 订单 / 预约 / 社区帖子 + 待办）</sub></td>
    <td align="center"><sub><b>商品管理</b>（真实库存价格 + 上下架 + 先下架后删除）</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/assets/screenshots/admin-community.png" width="440" alt="社区内容治理"/></td>
    <td align="center"><img src="docs/assets/screenshots/admin-ai-usage.png" width="440" alt="AI 调用用量"/></td>
  </tr>
  <tr>
    <td align="center"><sub><b>社区内容治理</b>（帖子审核 / 举报处理 / 敏感词）</sub></td>
    <td align="center"><sub><b>AI 调用用量</b>（模型 / token 消耗 / 成败审计）</sub></td>
  </tr>
</table>

---

## 🎯 已交付能力（M1–M8 全部完成）

- **服务预约**（M3）：服务浏览、宠物档案、地址管理、预约时段/距离/并发/状态流转，管理端处理预约状态
- **商品订单**（M4）：商品详情、购物车、下单、库存/金额/幂等、订单状态机
- **社区互动**（M5）：发帖、浏览、点赞、评论、收藏、标签、敏感词审核 + AI 文本审核
- **营销活动**（M2）：活动列表/详情、后台管理、商品/服务关联
- **钱包余额**（M7，CR-20260718-003）：管理端手工台账，扣款与扣库存同事务、行锁、只追加流水、强制审计
- **AI Agent**（M8，D-013）：见下节
- **后台风控**：管理员 RBAC、操作日志、用户封禁、内容审核、商品/服务先下架后删除（服务端状态守卫）

---

## 🤖 AI 能力（V1 + V2 均已上线）

| 能力 | 形态 | 安全边界 |
|---|---|---|
| 智能客服 | RAG 检索 + 只读实时 Tool + SSE 流式对话 | 三层医疗护栏，不诊断不处方 |
| 经营分析 Agent | 只读下钻 Tool + 结构化报告（管理端） | Tool 静态白名单 + RBAC 双校验 |
| 社区助手 | 个性化发帖草稿（仅草稿，不自动发布） | AI 建议只作参考 |
| 文本内容审核 | LLM 分类产 `PostReport` 进人工队列 | 不直接删帖封号 |

技术底座：**langchain4j + PgVector 独立实例**（只存派生知识副本，业务真源仍在 MySQL）+ 本地 ONNX 中文 embedding（BGE-small-zh-v1.5）+ DeepSeek LLM。

架构硬边界（由三个反射架构守卫测试强制，见下文）：AI 不直连数据库、不做疾病诊断/处方/治疗承诺、建议不能自动改业务数据。完整设计见 [`docs/09-ai-agent-design.md`](docs/09-ai-agent-design.md)。

---

## 🏗️ 技术架构

```
┌─────────────────────────────────────────────────────────────┐
│                    用户（浏览器 / 手机 / 微信开发者工具）        │
└──────────────┬────────────────────────────┬─────────────────┘
               │                            │
        ┌──────▼──────┐             ┌───────▼─────┐
        │  管理端 PC   │             │  用户端 H5   │
        │  Vue3+Vite  │             │  UniApp+Vue3 │
        │  :8080      │             │  :8081       │
        └──────┬──────┘             └───────┬─────┘
               │      nginx 反代 /api        │
               └────────────┬────────────────┘
                            │
                   ┌────────▼────────┐
                   │   后端 API      │
                   │ Spring Boot 3.3 │
                   │ langchain4j     │
                   │  :8082          │
                   └───┬─────────┬───┘
                       │         │
              ┌────────▼──┐  ┌───▼──────────────┐
              │ MySQL 8.0 │  │ PgVector         │
              │ 业务真源    │  │ AI 派生知识索引    │
              │ :3306     │  │ (可从 MySQL 重建) │
              └───────────┘  └──────────────────┘
```

- **模块化单体**：按业务域划分（user / booking / product / service / wallet / community / marketing / ai / moderation），单一可部署单元
- **安全**：JWT HttpOnly Cookie 双轨（小程序 Bearer 回退）、RBAC、参数校验、SQL 注入防护、密码 BCrypt + 强度策略、登录/上传/AI 计费入口三层限流
- **事务**：多表状态变更、库存扣减、订单金额、预约占用、钱包扣款均服务端事务校验
- **测试**：风险驱动策略——真实数据库上的并发/幂等/一致性集成测试 + 反射架构守卫，详见下文

---

## 🧪 测试与质量保障

**风险驱动，测在要害**：并发、幂等、状态流转、权限、订单金额、库存、社区隐私、AI 安全边界必须直接测试，关键变更逻辑覆盖率 ≥ 80%；不为凑数写低价值测试。

### 真实数据库集成测试（Testcontainers，33 个）

| 集成测试（节选） | 验证的规则 |
|---|---|
| `ProductIdempotencyConcurrencyIT` | 订单幂等键在真实并发下由唯一约束兜底，重放不重复下单（修复自审计高危 H1） |
| `ProductInventoryConcurrencyIT` | 高并发下单库存扣减不超卖 |
| `WalletPaymentAtomicityIT` / `WalletRefundAtomicityIT` | 钱包扣款/退款与扣库存同事务原子（CR-20260718-003） |
| `WalletConcurrencyMySqlIT` | 余额并发扣减走行锁，不出现负余额 |
| `BookingConcurrencyMySqlIT` / `BookingReassignMySqlIT` | 预约时段并发占用不超订、改派不冲突 |
| `BookingStatusTransitionMySqlIT` | 预约状态机拒绝非法流转 |
| `AddressDefaultConcurrencyMySqlIT` | 默认地址并发切换保持唯一 |
| `KnowledgeIndexingIT` | RAG 知识入库与索引重建幂等 |

### 架构守卫（反射测试，进 CI）

- `AiProviderArchitectureTest`：AI Provider 层不依赖 Mapper / DataSource / MyBatis
- `AiAgentArchitectureTest`：Agent Tool 不直连库、客服 Tool 全部 `readOnly()`、业务数据只经白名单 Tool → 业务 Service
- `AiRagArchitectureTest`：RAG 包访问业务数据只通过业务 Service 接口，向量库仅作派生索引

### 安全审计与加固

- **2026-07 白盒审计**：2 高危 + 5 中危全部修复并回归（订单幂等、全站限流、上传魔数校验、存储型 XSS、RBAC 修复、CVE 升级）
- **2026-08 上线加固**：端口绑回环、`${VAR:?}` 强制变量、默认 prod profile、nginx 安全头、JWT HttpOnly Cookie 双轨——清单见[安全审计报告](docs/11-security-audit-2026-07.md) §7
- **2026-08-23 资源耗尽面复审**：P0 项修复

---

## ⚙️ 配置管理与 CI/CD（项目核心实践）

### 配置管理计划（依据 IEEE Std 828-2012）

| 交付物 | 位置 |
|---|---|
| 配置管理计划（CMP）v1.0 | [`docs/03-configuration-management-plan.md`](docs/03-configuration-management-plan.md) |
| 配置项登记表（76 项 CI） | [`docs/配置项登记表.md`](docs/配置项登记表.md) |
| 基线清单（8 个 tag） | [`docs/基线清单.md`](docs/基线清单.md) |
| 变更申请单实例（4 个真实 CR） | [`docs/变更申请单-实例/`](docs/变更申请单-实例/) |
| 构建指导书 | [`docs/06-build-guide.md`](docs/06-build-guide.md) |
| 部署指南 | [`docs/07-deployment-guide.md`](docs/07-deployment-guide.md) |
| Jenkins 接入指南 | [`jenkins/README.md`](jenkins/README.md) |
| 第 16 周审计证据 | [`docs/audit-evidence/week16/`](docs/audit-evidence/week16/) |

### CI/CD 流水线

```
提交代码 → Jenkins 自动触发 → 编译 → 测试 → 打包 → Docker 构建 → 部署 → 健康检查
             ↳ GitHub Actions（push/PR）→ 后端 + 管理端 + H5 三 job 并行
```

- **Jenkins 流水线**（[`Jenkinsfile`](Jenkinsfile)）：Checkout → Backend Build → Backend Test → Backend Package → Docker Build → Deployment Check → Deploy → Health Check
- **GitHub Actions**（[`.github/workflows/ci.yml`](.github/workflows/ci.yml)）：三 job 并行 + JaCoCo 覆盖率
- **Docker 化部署**（[`docker-compose.yml`](docker-compose.yml)）：MySQL + PgVector + API + 管理端 nginx + H5 nginx
- **VPS 实机演示**：Caddy + HTTPS 部署速查见 [`docs/13-vps-deploy-cheatsheet.md`](docs/13-vps-deploy-cheatsheet.md)

### 基线版本（git tag）

```
v1.0.0-fb    功能基线
v1.0.0-m1    M1：H5 基础与可重复演示数据
v1.0.0-m2    M2：公开浏览 + 营销
v1.0.0-m3    M3：用户资料 + 预约
v1.0.0-m4    M4：商品 + 订单
v1.0.0-m5    M5：社区互动
v1.0.0-m6    M6：发布收口
v1.0.0-rc1   产品基线（CI/CD 全通 + Docker 化部署可演示）
```

---

## 🚀 快速启动

### 方式一：Docker Compose 一键部署（推荐）

```powershell
# 1. 准备 .env（复制样例并填入真实值，JWT_SECRET 需 ≥32 字节；弱凭据会被 prod 启动检查拒绝）
copy .env.example .env

# 2. 一键启动（含数据库初始化；生产 compose 不挂演示种子）
docker compose up -d --build --wait
```

启动后访问：
- 管理端：http://localhost:8080（演示种子账号 `admin / admin123456`，仅限 dev 数据）
- 用户端 H5：http://localhost:8081
- API 健康检查：http://localhost:8082/api/v1/system/health

### 方式二：本地开发模式

```powershell
# 数据库：dev compose 含演示种子与媒体（推荐）
docker compose -f docker-compose.dev.yml up -d mysql

# 后端（默认 profile 已改为 prod，本地开发需显式指定 dev）
$env:SPRING_PROFILES_ACTIVE='dev'; mvn spring-boot:run

# 管理端
cd frontend/admin-web && npm install && npm run dev

# 用户端 H5
cd frontend/miniapp && npm install && npm run dev:h5
```

详细环境变量和配置项见 [`.env.example`](.env.example) 与 [`docs/07-deployment-guide.md`](docs/07-deployment-guide.md)。

---

## 📚 核心文档

| 文档 | 用途 |
|---|---|
| [`AGENTS.md`](AGENTS.md) | Agent 执行规则（项目最高优先级） |
| [`AGENTS-TEMPLATE.md`](AGENTS-TEMPLATE.md) | **本项目方法论沉淀，可复用于后续项目的 AGENTS.md 模板** |
| [`docs/00-project-boundary.md`](docs/00-project-boundary.md) | V1 范围和不可突破的业务边界 |
| [`docs/01-architecture-design.md`](docs/01-architecture-design.md) | 系统架构和关键技术约束 |
| [`docs/02-task-breakdown.md`](docs/02-task-breakdown.md) | 交付路线图和里程碑（M0–M8） |
| [`docs/03-configuration-management-plan.md`](docs/03-configuration-management-plan.md) | **配置管理计划（CMP v1.0）** |
| [`docs/04-code-standards.md`](docs/04-code-standards.md) | 代码与安全规则 |
| [`docs/05-testing-and-verification.md`](docs/05-testing-and-verification.md) | 风险驱动验证方式 |
| [`docs/09-ai-agent-design.md`](docs/09-ai-agent-design.md) | **V2 AI Agent 设计**（PgVector RAG + Agent 工具调用 + 审核护栏） |
| [`docs/11-security-audit-2026-07.md`](docs/11-security-audit-2026-07.md) | 安全审计 + 上线加固清单 |
| [`docs/16-mp-native-ui-design-demo.md`](docs/16-mp-native-ui-design-demo.md) | 现行 UI 设计规范（2026-08 改版基线） |
| [`docs/requirements-source.md`](docs/requirements-source.md) | 产品需求基线 |

---

## 📝 提交规范

采用 [Conventional Commits](https://www.conventionalcommits.org/)：

```
<type>(<scope>): <描述>

type: feat | fix | refactor | test | docs | build | ci | chore
scope: booking | order | community | auth | product | service | ai | wallet | cm | ci | docs ...
```

示例：`feat(booking): 支持按服务人员过滤可预约时段`

---

## 🔒 不在 V1 范围

真实在线支付通道（微信/支付宝资质接入）、多门店、优惠券、会员积分、独立员工端、AI 疾病诊断/药物处方/治疗承诺（永久禁止）、微信小程序真实上架（资质/合规流程，demo 形态已可演示）—— 均待真实运营需求驱动后再排期。

---

## 📄 许可与声明

本项目为本科毕业设计作品，仅用于学术演示。演示种子账号 `admin/admin123456` 仅限开发环境数据，生产部署须通过环境变量注入真实凭据（弱凭据会被启动检查拒绝），禁止硬编码密钥。种子图片来自 Wikimedia Commons / Unsplash（授权与署名见 `uploads/images/seed/CREDITS.md`）。
