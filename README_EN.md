# PetCare O2O 🐾

**[简体中文](README.md) ｜ [English](README_EN.md)**

> **An O2O service / retail / community platform for single-location pet stores** — undergraduate capstone project
>
> Milestones **M1–M8 all delivered** ｜ AI Agent v2 in production ｜ 1093+ backend tests passing ｜ End-to-end CI/CD ｜ Pre-launch security hardening closed out

---

## 📌 Overview

PetCare O2O is a modular monolith serving a single pet store: service booking, product retail, a wallet ledger, community engagement, marketing campaigns, and AI agents across six business domains. It ships responsive-H5-first — a web app that works on phones and desktop browsers — with a WeChat Mini Program demo running in parallel.

| Client | Stack | Entry |
|---|---|---|
| **Admin console (PC Web)** | Vue 3 + Vite + Element Plus | http://localhost:8080 |
| **Customer H5 / Mini Program** | UniApp + Vue 3 + wot-design-uni | http://localhost:8081 |
| **Backend API** | Spring Boot 3.3 + MyBatis-Plus + MySQL 8 + PgVector | http://localhost:8082 |
| **CI/CD** | Jenkins + GitHub Actions + Docker Compose | http://localhost:9090 |

---

## 📱 UI Showcase (2026-08 redesign, captured from the running app)

### Customer H5

> All five pages captured from the **live app** at a uniform 390×844 viewport, backed by the real API and a licensed demo media seed set (68 Wikimedia/Unsplash images, per-image credits in `uploads/images/seed/CREDITS.md`).

<table>
  <tr>
    <td align="center"><img src="docs/assets/screenshots/home.png" width="160" alt="Home"/></td>
    <td align="center"><img src="docs/assets/screenshots/services.png" width="160" alt="Services"/></td>
    <td align="center"><img src="docs/assets/screenshots/products.png" width="160" alt="Products"/></td>
    <td align="center"><img src="docs/assets/screenshots/community.png" width="160" alt="Community"/></td>
    <td align="center"><img src="docs/assets/screenshots/profile.png" width="160" alt="Profile"/></td>
  </tr>
  <tr>
    <td align="center"><sub><b>Home</b></sub></td>
    <td align="center"><sub><b>Service Booking</b></sub></td>
    <td align="center"><sub><b>Retail</b></sub></td>
    <td align="center"><sub><b>Community</b></sub></td>
    <td align="center"><sub><b>Profile</b></sub></td>
  </tr>
</table>

<p align="center">
  <img src="docs/assets/screenshots/services-desktop.png" width="800" alt="Desktop responsive"/><br/>
  <sub><b>Desktop responsive layout</b> (1280-wide viewport, services page — one codebase adapts)</sub>
</p>

### Admin Console (PC Web)

> Captured at a 1440-wide desktop viewport: operations overview, product management, community moderation, and AI usage metering (token consumption and outcome auditing, no conversation content).

<table>
  <tr>
    <td align="center"><img src="docs/assets/screenshots/admin-dashboard.png" width="440" alt="Operations overview"/></td>
    <td align="center"><img src="docs/assets/screenshots/admin-products.png" width="440" alt="Product management"/></td>
  </tr>
  <tr>
    <td align="center"><sub><b>Operations Overview</b> (products / orders / bookings / posts + todos)</sub></td>
    <td align="center"><sub><b>Product Management</b> (real stock & prices, off-shelf-before-delete)</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="docs/assets/screenshots/admin-community.png" width="440" alt="Community moderation"/></td>
    <td align="center"><img src="docs/assets/screenshots/admin-ai-usage.png" width="440" alt="AI usage"/></td>
  </tr>
  <tr>
    <td align="center"><sub><b>Community Moderation</b> (post review / reports / sensitive words)</sub></td>
    <td align="center"><sub><b>AI Usage</b> (models / token spend / outcome audit)</sub></td>
  </tr>
</table>

---

## 🎯 Delivered Capabilities (M1–M8 complete)

- **Service booking** (M3): service browsing, pet profiles, addresses, slot/distance/concurrency/state-machine validation; admin-side booking lifecycle
- **Product orders** (M4): product detail, cart, checkout, inventory/amount/idempotency guarantees, order state machine
- **Community** (M5): posts, feed, likes, comments, favorites, tags, sensitive-word moderation + AI text review
- **Marketing** (M2): campaign list/detail, admin management, product/service linkage
- **Wallet ledger** (M7): admin-managed balance ledger — deduction and inventory in one transaction, row locks, append-only statements, mandatory audit
- **AI agents** (M8): see below
- **Admin controls**: role-based access control, operation logs, user bans, content moderation, off-shelf-before-delete enforced server-side
- **Configuration management**: 76 controlled configuration items, 8 baseline tags, an instantiated change-control process

---

## 🤖 AI Capabilities (V1 + V2 live)

| Capability | Form | Safety boundary |
|---|---|---|
| Customer-service chatbot | RAG retrieval + read-only live tools + SSE streaming | Three-layer medical guardrails — no diagnosis, no prescriptions |
| Business analytics agent | Read-only drill-down tools + structured reports (admin) | Static tool whitelist + dual RBAC checks |
| Community assistant | Personalized post drafts (drafts only, never auto-posts) | AI suggestions are advisory only |
| Text content review | LLM classification producing `PostReport` into a human queue | Never deletes posts or bans users directly |

Technical foundation: **langchain4j + a dedicated PgVector instance** (stores derived knowledge copies only; MySQL remains the source of truth) + local ONNX Chinese embeddings (BGE-small-zh-v1.5) + DeepSeek LLM.

Hard architectural boundaries — enforced by reflection in `AiProviderArchitectureTest` / `AiAgentArchitectureTest`: the AI never touches the database directly, never gives diagnoses/prescriptions/treatment promises, and its suggestions never mutate business data. Every billable AI endpoint sits behind three-tier rate limiting (per-minute / per-user daily / global daily). Full design in [`docs/09-ai-agent-design.md`](docs/09-ai-agent-design.md) (Chinese).

---

## ⚙️ Configuration Management & CI/CD (core practice)

### Configuration Management Plan (IEEE Std 828-2012)

| Deliverable | Location |
|---|---|
| Configuration Management Plan (CMP) v1.0 | [`docs/03-configuration-management-plan.md`](docs/03-configuration-management-plan.md) (Chinese) |
| CI register (76 items) | [`docs/配置项登记表.md`](docs/配置项登记表.md) |
| Baseline list (8 tags) | [`docs/基线清单.md`](docs/基线清单.md) |
| Change request instances (4 real CRs) | [`docs/变更申请单-实例/`](docs/变更申请单-实例/) |
| Build guide | [`docs/06-build-guide.md`](docs/06-build-guide.md) (Chinese) |
| Deployment guide | [`docs/07-deployment-guide.md`](docs/07-deployment-guide.md) (Chinese) |
| Jenkins guide | [`jenkins/README.md`](jenkins/README.md) |
| Week-16 audit evidence | [`docs/audit-evidence/week16/`](docs/audit-evidence/week16/) |

### CI/CD pipelines

```
commit → Jenkins auto-trigger → compile → test (1093+) → package → Docker build → deploy → health check
             ↳ GitHub Actions (push/PR) → backend + admin + H5, three parallel jobs
```

- **Jenkins** ([`Jenkinsfile`](Jenkinsfile)): Checkout → Backend Build → Backend Test → Backend Package → Docker Build → Deployment Check → Deploy → Health Check
- **GitHub Actions** ([`.github/workflows/ci.yml`](.github/workflows/ci.yml)): three parallel jobs + JaCoCo coverage
- **Dockerized deployment** ([`docker-compose.yml`](docker-compose.yml)): MySQL + PgVector + API + admin nginx + H5 nginx
- **Pre-launch hardening** (2026-08): loopback-bound ports, `${VAR:?}` required variables, prod-by-default profile, nginx security headers, AI/upload rate limiting, dual-track JWT HttpOnly cookies — checklist in [`docs/11-security-audit-2026-07.md`](docs/11-security-audit-2026-07.md) §7

### Baseline tags

```
v1.0.0-fb    functional baseline
v1.0.0-m1    M1: H5 foundation + reproducible demo data
v1.0.0-m2    M2: public browsing + marketing
v1.0.0-m3    M3: user profiles + booking
v1.0.0-m4    M4: products + orders
v1.0.0-m5    M5: community
v1.0.0-m6    M6: release closure
v1.0.0-rc1   product baseline (CI/CD green + Dockerized deploy)
```

---

## 🚀 Quick Start

### Option 1: Docker Compose one-command deploy (recommended)

```powershell
# 1. Prepare .env (copy the sample and fill real values; JWT_SECRET >= 32 bytes —
#    weak credentials are rejected by the prod startup check)
copy .env.example .env

# 2. One command (includes DB initialization; prod compose ships no demo seeds)
docker compose up -d --build --wait
```

Then visit:
- Admin console: http://localhost:8080 (demo seed account `admin / admin123456`, dev data only)
- Customer H5: http://localhost:8081
- API health: http://localhost:8082/api/v1/system/health

### Option 2: local development

```powershell
# Database: dev compose includes demo seeds and media (recommended)
docker compose -f docker-compose.dev.yml up -d mysql

# Backend (default profile is now prod; local dev must explicitly set dev)
$env:SPRING_PROFILES_ACTIVE='dev'; mvn spring-boot:run

# Admin console
cd frontend/admin-web && npm install && npm run dev

# Customer H5
cd frontend/miniapp && npm install && npm run dev:h5
```

See [`.env.example`](.env.example) and [`docs/07-deployment-guide.md`](docs/07-deployment-guide.md) for configuration details; a VPS demo-deploy cheatsheet (Caddy + HTTPS) is at [`docs/13-vps-deploy-cheatsheet.md`](docs/13-vps-deploy-cheatsheet.md).

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│        Users (browser / phone / WeChat devtools)            │
└──────────────┬────────────────────────────┬─────────────────┘
               │                            │
        ┌──────▼──────┐             ┌───────▼─────┐
        │ Admin Web   │             │ Customer H5 │
        │ Vue3+Vite   │             │ UniApp+Vue3 │
        │ :8080       │             │ :8081       │
        └──────┬──────┘             └───────┬─────┘
               │      nginx /api reverse proxy
               └────────────┬────────────────┘
                            │
                   ┌────────▼────────┐
                   │   Backend API   │
                   │ Spring Boot 3.3 │
                   │  langchain4j    │
                   │  :8082          │
                   └───┬─────────┬───┘
                       │         │
              ┌────────▼──┐  ┌───▼──────────────┐
              │ MySQL 8.0 │  │ PgVector         │
              │ source of │  │ derived AI index │
              │ truth     │  │ (rebuildable)    │
              └───────────┘  └──────────────────┘
```

- **Modular monolith**: organized by business domain (user / booking / product / service / wallet / community / marketing / ai / moderation), one deployable unit
- **AI boundary guards**: `AiProviderArchitectureTest` + `AiAgentArchitectureTest` reflectively forbid AI code from depending on Mappers/DataSources
- **Security**: dual-track JWT HttpOnly cookies (Bearer fallback for the Mini Program), RBAC, input validation, SQL-injection protection, BCrypt + password strength policy, three-tier rate limiting on login/upload/billable AI endpoints
- **Transactions**: multi-table state changes, inventory deduction, order amounts, booking capacity, wallet deduction — all validated server-side in transactions
- **Testing**: 1093+ unit/integration/contract tests (plus 33 Testcontainers real-MySQL ITs), risk-driven coverage (≥ 80% on critical modules)

---

## 📊 Quality Metrics

| Metric | Value |
|---|---|
| Backend tests | **1093+** (2026-08-15 full-regression baseline, growing with each slice) |
| Real-MySQL ITs (tc-mysql) | 33 (concurrency / idempotency / locking / state machines) |
| H5 contract tests | 160 (re-anchored after the UI redesign) |
| Controlled config items | 76 |
| Baseline tags | 8 |
| Change requests | 4 |
| Security audits | 2026-07 white-box: 2 high + 5 medium, all fixed; 2026-08 pre-launch hardening: 7 of 8 done |

---

## 📚 Key Documents

| Document | Purpose |
|---|---|
| [`AGENTS.md`](AGENTS.md) | Agent execution rules (top priority, Chinese) |
| [`AGENTS-TEMPLATE.md`](AGENTS-TEMPLATE.md) | **Reusable AGENTS.md template distilled from this project's methodology** |
| [`docs/00-project-boundary.md`](docs/00-project-boundary.md) | V1 scope and non-negotiable business boundaries |
| [`docs/01-architecture-design.md`](docs/01-architecture-design.md) | System architecture and key constraints |
| [`docs/02-task-breakdown.md`](docs/02-task-breakdown.md) | Delivery roadmap and milestones (M0–M8) |
| [`docs/03-configuration-management-plan.md`](docs/03-configuration-management-plan.md) | **Configuration Management Plan (CMP v1.0)** |
| [`docs/04-code-standards.md`](docs/04-code-standards.md) | Code and security standards |
| [`docs/05-testing-and-verification.md`](docs/05-testing-and-verification.md) | Risk-driven verification approach |
| [`docs/09-ai-agent-design.md`](docs/09-ai-agent-design.md) | **AI Agent v2 design** (PgVector RAG + agent tooling + moderation guardrails) |
| [`docs/11-security-audit-2026-07.md`](docs/11-security-audit-2026-07.md) | Security audits + pre-launch hardening checklist |
| [`docs/16-mp-native-ui-design-demo.md`](docs/16-mp-native-ui-design-demo.md) | Current UI design spec (2026-08 redesign baseline) |
| [`docs/requirements-source.md`](docs/requirements-source.md) | Product requirements baseline |

> Most project documents are written in Chinese; this README provides the English summary.

---

## 📝 Commit Convention

[Conventional Commits](https://www.conventionalcommits.org/): `<type>(<scope>): <description>` — types: feat | fix | refactor | test | docs | build | ci | chore.

---

## 🔒 Out of V1 Scope

Real online payment channels (WeChat/Alipay qualification), multi-store, coupons, membership points, a standalone staff client, AI disease diagnosis / prescriptions / treatment promises (permanently forbidden), and a production WeChat Mini Program listing (qualification/compliance; the demo form is fully presentable) — all deferred until driven by real operational needs.

---

## 📄 License & Disclaimer

This project is an undergraduate capstone for academic demonstration only. The demo seed account `admin/admin123456` exists only in dev data; production deployments must inject real credentials via environment variables (weak credentials fail the startup check). Hardcoding secrets is prohibited. Seed images come from Wikimedia Commons / Unsplash (licenses and credits in `uploads/images/seed/CREDITS.md`).
