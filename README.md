# PetCare O2O 🐾

> **面向单体宠物门店的 O2O 服务/商品/社区平台** —— 本科毕业设计项目
>
> 版本：**v1.0.0-rc1** ｜ 状态：产品基线已建立 ｜ CI/CD 全链路打通 ｜ 后端 888 个测试通过

---

## 📌 项目简介

PetCare O2O 是一套面向单体宠物门店的模块化单体应用，覆盖宠物服务预约、商品零售、社区互动和营销活动四大业务域。系统采用响应式 H5 优先策略，先交付可在手机和桌面浏览器使用的 Web 应用，后续再适配微信小程序。

| 端 | 技术栈 | 入口 |
|---|---|---|
| **管理端 PC Web** | Vue 3 + Vite + Element Plus | http://localhost:8080 |
| **用户端 H5** | UniApp + Vue 3 | http://localhost:8081 |
| **后端 API** | Spring Boot 3.3 + MyBatis-Plus + MySQL 8 | http://localhost:8082 |
| **CI/CD** | Jenkins + GitHub Actions + Docker Compose | http://localhost:9090 |

---

## 🎯 已交付能力（M1–M6 全部完成）

- **服务预约**：服务浏览、宠物档案、地址管理、预约时段/距离/并发/状态流转
- **商品订单**：商品详情、购物车、下单、库存/金额/幂等、订单状态机
- **社区互动**：发帖、浏览、点赞、评论、收藏、标签、敏感词审核
- **营销活动**：活动列表/详情、后台管理、商品/服务关联
- **后台风控**：管理员 RBAC、操作日志、用户封禁、内容审核
- **配置管理**：76 个配置项受控、8 个基线 tag、变更控制流程实例化

---

## ⚙️ 配置管理与 CI/CD（项目核心实践）

### 配置管理计划（依据 IEEE Std 828-2012）

| 交付物 | 位置 |
|---|---|
| 配置管理计划（CMP）v1.0 | [`docs/03-configuration-management-plan.md`](docs/03-configuration-management-plan.md) |
| 配置项登记表（76 项 CI） | [`docs/配置项登记表.md`](docs/配置项登记表.md) |
| 基线清单（8 个 tag） | [`docs/基线清单.md`](docs/基线清单.md) |
| 变更申请单实例（2 个真实 CR） | [`docs/变更申请单-实例/`](docs/变更申请单-实例/) |
| 构建指导书 | [`docs/06-build-guide.md`](docs/06-build-guide.md) |
| 部署指南 | [`docs/07-deployment-guide.md`](docs/07-deployment-guide.md) |
| Jenkins 接入指南 | [`jenkins/README.md`](jenkins/README.md) |
| 第 16 周审计证据 | [`docs/audit-evidence/week16/`](docs/audit-evidence/week16/) |

### CI/CD 流水线

```
提交代码 → Jenkins 自动触发 → 编译 → 测试(888) → 打包 → Docker 构建 → 部署
                                                                      ↓
                                                        三端容器可运行（演示）
```

- **Jenkins 流水线**（[`Jenkinsfile`](Jenkinsfile)）：Checkout → Backend Build → Backend Test → Backend Package → Docker Build → Deployment Check → Deploy → Health Check
- **GitHub Actions**（[`.github/workflows/ci.yml`](.github/workflows/ci.yml)）：push/PR 触发，后端 + 管理端 + H5 三 job 并行
- **Docker 化部署**（[`docker-compose.yml`](docker-compose.yml)）：MySQL + API + 管理端 nginx + H5 nginx 四服务一键启动
- **自动测试报告**：JUnit + JaCoCo 覆盖率（class 93% / line 72%）+ 邮件通知

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
# 1. 准备 .env（复制样例并填入真实值，JWT_SECRET 需 ≥32 字节）
copy .env.example .env

# 2. 一键启动四服务（含数据库初始化）
docker compose up -d --build --wait
```

启动后访问：
- 管理端：http://localhost:8080（账号 `admin` / `admin123456`）
- 用户端 H5：http://localhost:8081
- API 健康检查：http://localhost:8082/api/v1/system/health

### 方式二：本地开发模式

```powershell
# 数据库初始化
mysql -u root -p < schema.sql
mysql -u root -p petcare_o2o < src/main/resources/data-dev.sql

# 后端
mvn spring-boot:run

# 管理端
cd frontend/admin-web && npm install && npm run dev

# 用户端 H5
cd frontend/miniapp && npm install && npm run dev:h5
```

详细环境变量和配置项见 [`.env.example`](.env.example) 与 [`docs/07-deployment-guide.md`](docs/07-deployment-guide.md)。

---

## 🏗️ 技术架构

```
┌──────────────────────────────────────────────────────────┐
│                     用户（浏览器 / 手机）                  │
└──────────────┬───────────────────────────┬───────────────┘
               │                           │
        ┌──────▼──────┐            ┌──────▼──────┐
        │  管理端 PC   │            │  用户端 H5  │
        │  Vue3+Vite   │            │  UniApp+Vue3│
        │  :8080       │            │  :8081      │
        └──────┬──────┘            └──────┬──────┘
               │   nginx 反代 /api        │
               └───────────┬──────────────┘
                           │
                  ┌────────▼────────┐
                  │   后端 API      │
                  │ Spring Boot 3.3 │
                  │  :8082          │
                  └────────┬────────┘
                           │
                  ┌────────▼────────┐
                  │   MySQL 8.0     │
                  │   :3306         │
                  └─────────────────┘
```

- **模块化单体**：按业务域划分（user / booking / product / service / community / marketing / notification / moderation），单一可部署单元
- **安全**：JWT 认证、RBAC、参数校验、SQL 注入防护、密码 BCrypt + 强度策略
- **事务**：多表状态变更、库存扣减、订单金额、预约占用均服务端事务校验
- **测试**：888 个单元/集成/契约测试，风险驱动覆盖（关键模块覆盖率 ≥ 80%）

---

## 📊 质量指标

| 指标 | 数值 |
|---|---|
| 后端测试数 | **888**（全部通过） |
| 类覆盖率（JaCoCo） | 93.07% |
| 行覆盖率 | 72.67% |
| 分支覆盖率 | 57.10% |

> 覆盖率数据采自 v1.0.0-rc1 基线（JaCoCo 报告），测试数为当前主干最新值。
| 配置项（CI）总数 | 76 |
| 基线 tag 数 | 8 |
| 变更申请单实例 | 2 |

---

## 📚 核心文档

| 文档 | 用途 |
|---|---|
| [`AGENTS.md`](AGENTS.md) | Agent 执行规则（项目最高优先级） |
| [`docs/00-project-boundary.md`](docs/00-project-boundary.md) | V1 范围和不可突破的业务边界 |
| [`docs/01-architecture-design.md`](docs/01-architecture-design.md) | 系统架构和关键技术约束 |
| [`docs/02-task-breakdown.md`](docs/02-task-breakdown.md) | 交付路线图和里程碑 |
| [`docs/03-configuration-management-plan.md`](docs/03-configuration-management-plan.md) | **配置管理计划（CMP v1.0）** |
| [`docs/04-code-standards.md`](docs/04-code-standards.md) | 代码与安全规则 |
| [`docs/05-testing-and-verification.md`](docs/05-testing-and-verification.md) | 风险驱动验证方式 |
| [`docs/09-ai-agent-design.md`](docs/09-ai-agent-design.md) | **V2 AI Agent 增量设计**（PgVector RAG + Agent 工具调用 + 社区/审核，D-013/C-R20260801-004） |
| [`docs/requirements-source.md`](docs/requirements-source.md) | 产品需求基线 |

---

## 📝 提交规范

采用 [Conventional Commits](https://www.conventionalcommits.org/)：

```
<type>(<scope>): <描述>

type: feat | fix | refactor | test | docs | build | ci | chore
scope: booking | order | community | auth | product | service | cm | ci | docs ...
```

示例：`feat(booking): 支持按服务人员过滤可预约时段`

---

## 🔒 不在 V1 范围

在线支付、多门店、优惠券、积分、独立员工端、AI 助手、AI 营销、医疗诊断、微信登录与小程序适配 —— 均在 H5 稳定交付后再规划。

---

## 📄 许可与声明

本项目为本科毕业设计作品，仅用于学术演示。默认管理员账号 `admin/admin123456` 仅限开发环境，生产部署须通过环境变量注入真实凭据，禁止硬编码密钥。
