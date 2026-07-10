# PetCare O2O 配置管理计划（CMP）

> 版本：v1.0 ｜ 生效日期：2026-06-22 ｜ 编制：配置管理组 ｜ 状态：基线
>
> 参考标准：IEEE Std 828-2012《Configuration Management in Systems and Software Engineering》、GB/T 11457-2006《信息技术 软件工程术语》、ISO 10007《质量管理体系—配置管理指南》。

---

## 1. 引言

### 1.1 目的

本计划规定 PetCare O2O（单门店宠物 O2O 服务/商品/社区平台）在软件开发与交付全生命周期中配置管理（Configuration Management, CM）的目标、组织、活动、流程与工具，确保：

- 所有配置项（Configuration Item, CI）可被**唯一标识**、**版本可追溯**、**状态可审计**。
- 基线（Baseline）建立后，任何变更都经过**评估—审批—实施—验证—归档**受控流程。
- 通过 Jenkins + GitHub Actions + Docker Compose 实现"提交即编译、打包、测试、部署"的自动化流水线，产出可运行系统。
- 满足毕业设计配置管理实践要求，覆盖配置管理计划、提交日志、实施证据、自动化流水线与演示验证等维度。

### 1.2 范围

适用对象：

| 层 | 范围 |
|---|---|
| 后端 | `src/main/java/**`、`src/test/java/**`、`pom.xml`、`src/main/resources/**` |
| 管理端 | `frontend/admin-web/**`（Vue 3 + Vite + Element Plus） |
| 用户端 H5 | `frontend/miniapp/**`（UniApp + Vue 3，构建目标 H5） |
| 数据库 | `schema.sql`、`src/main/resources/*.sql`（建表与增量迁移脚本） |
| 构建与部署 | `Dockerfile`、`docker-compose.yml`、`Jenkinsfile`、`.github/workflows/**`、`nginx/**` |
| 文档 | `README.md`、`AGENTS.md`、`docs/**`、`CHANGELOG.md` |

不适用：第三方依赖（node_modules、Maven 依赖由包管理器与锁文件管控）、构建产物（target/、dist/，由 .gitignore 排除，不纳入版本库）。

### 1.3 参考资料

- IEEE Std 828-2012 Configuration Management in Systems and Software Engineering
- GB/T 11457-2006 信息技术 软件工程术语
- ISO 10007:2017 质量管理体系—配置管理指南
- 毕业设计软件配置管理实践要求
- 项目内部：`AGENTS.md`、`docs/00-project-boundary.md`、`docs/01-architecture-design.md`、`docs/02-task-breakdown.md`、`docs/04-code-standards.md`、`docs/05-testing-and-verification.md`、`docs/08-pending-decisions.md`

### 1.4 术语

| 术语 | 含义 |
|---|---|
| CI | Configuration Item，配置项 |
| CM | Configuration Management，配置管理 |
| CCB | Configuration Control Board，配置控制委员会 |
| 基线 Baseline | 在某时间点通过正式评审并固化的配置项集合，后续变更须经 CCB |
| 功能基线 | 定义系统功能需求的基线（需求文档） |
| 里程碑基线 | 阶段交付节点对应的代码与文档基线（M1–M6） |
| 产品基线 | 最终交付给用户的产品对应的基线 |

---

## 2. 配置管理组织

### 2.1 角色与职责

| 角色 | 职责 | 本组成员（占位） |
|---|---|---|
| **配置经理（CM 经理）** | 总体负责 CMP 的制定、推行与维护；主持 CCB 会议；签发基线；审核配置审计结果 | ___ |
| **配置管理员** | 日常执行版本控制、打 tag、维护基线清单、配置项登记表、变更记录；管理 CI 工具（Git/GitHub/Jenkins） | ___ |
| **CCB（配置控制委员会）** | 对所有"基线后"变更申请进行影响分析、评审、批准或驳回；由 CM 经理、技术负责人、测试负责人组成 | ___、___、___ |
| **开发员** | 在受控分支上提交代码，遵守 Conventional Commits 规范；提交变更申请前完成自测 | 全体成员 |
| **测试员** | 执行 CI 流水线中的自动化测试；对变更结果做回归验证 | ___ |

> *实际成员名单请小组在交付前填入。*

### 2.2 CCB 运作

- 例会节奏：每两周一次；重大变更随时召集临时会议。
- 决策机制：CCB 成员一致同意通过；意见不统一时由 CM 经理裁决。
- 输出物：会议纪要（归档于 `docs/ccb-meetings/`）+ 更新 `docs/变更申请单-实例/` 与 `docs/08-pending-decisions.md`。

---

## 3. 配置项识别与命名规则

### 3.1 配置项分类

| 类别 | 示例 |
|---|---|
| **源代码** | 后端 Java、前端 Vue/TS、单元/集成测试代码 |
| **构建脚本** | `pom.xml`、`package.json`、`Dockerfile`、`docker-compose.yml` |
| **CI/CD 流水线** | `Jenkinsfile`、`.github/workflows/*.yml` |
| **运行配置** | `application*.yml`、`nginx/*.conf`、`.env.example` |
| **数据库脚本** | `schema.sql`、`data-dev.sql`、`migration-phase*.sql` |
| **项目文档** | 本 CMP、构建指导书、部署指南、需求、架构、测试策略、决策记录 |
| **基线产物** | git tag、JaCoCo/Vitest 测试报告、构建产物 jar、Docker 镜像 |

### 3.2 配置项标识规则

所有配置项以**文件路径 + Git 版本号**作为唯一标识。登记表见 `docs/配置项登记表.md`。

命名约定：

| 类型 | 命名规则 | 示例 |
|---|---|---|
| 源代码文件 | 既有项目命名规范（后端 PascalCase、前端 kebab-case） | `BookingController.java`、`pc-booking-card.vue` |
| 配置项 ID | `CI-<类别码>-<序号>`，类别码：SC 源码/BS 构建脚本/CI 流水线/RC 运行配置/DB 数据库脚本/DOC 文档/BL 基线产物 | `CI-SC-001`、`CI-CI-003` |
| Git 分支 | 见 §4.2 | `main`、`develop`、`phase-11-*`、`hotfix/*` |
| Git tag | 见 §5.2 | `v1.0.0-m3`、`v1.0.0-rc1` |
| Docker 镜像 | `petcare-<服务名>:<版本>` | `petcare-api:1.0.0` |
| 构建产物 jar | `petcare-o2o-api-<版本>.jar` | `petcare-o2o-api-1.0.0.jar` |
| 变更申请 | `CR-<YYYYMMDD>-<序号>` | `CR-20260622-001` |

### 3.3 配置项登记

完整的配置项登记表维护于 `docs/配置项登记表.md`，由配置管理员在每次新增/退役 CI 时更新，每次基线建立时复核。

---

## 4. 版本控制方案

### 4.1 工具与仓库

- **版本控制工具**：Git
- **远程仓库**：GitHub（`https://github.com/zengmo20224/-------2-.git`）
- **本地仓库**：开发机 clone 自远程
- **分支保护**：`main` 与 `develop` 分支设为受保护，禁止 `git push --force`，合并须经 Pull Request 评审。

### 4.2 分支模型（简化 GitFlow）

```
            ┌──────── 产品发布基线（tag: v1.0.0, v1.0.1...） ────────┐
            │                                                          │
            ▼                                                          │
   ┌─── main ──────────────●───────────●───────────●───────────●─────►  (稳定主干，只接受合并)
   │                       │           │           │           │
   │   合并                │           │           │           │
   │                       ▼           ▼           ▼           ▼
   ├── develop ────────────●───────────●───────────●───────────●─────►  (集成分支，CI 全量跑)
   │                       │
   │  feature/phase-N      │
   ├── phase-2-backend ────┤   (阶段交付分支，按里程碑或大特性切)
   ├── phase-7-product ────┤
   ├── phase-10-frontend ──┤
   ├── phase-11-user ──────┘
   │
   └── hotfix/CR-YYYYMMDD-001 ──► 从 main 拉出，修复后合并回 main + develop
```

| 分支 | 用途 | 来源 | 合并去向 | 命名 |
|---|---|---|---|---|
| `main` | 生产发布基线，只存放可发布代码 | — | — | `main` |
| `develop` | 日常集成分支，CI 在此全量验证 | `main` | `main`（发布时） | `develop` |
| `phase-N-*` | 阶段交付分支（按里程碑或功能模块） | `develop` | `develop` | `phase-<编号>-<主题>` |
| `hotfix/*` | 紧急修复 | `main` | `main` + `develop` | `hotfix/CR-<日期>-<序号>` |

> **现状说明**：`main` 与 `develop` 已于 2026-06-22 建立，二者均基于 `phase-11-user-prerequisites` 的基线 commit（`441b5a6`）。此后 `main` 只接受基线合并与 hotfix，日常开发在 `develop` 与 `feature/*` 上推进。`phase-*` 分支保留为历史里程碑记录。

### 4.3 版本号规则（SemVer + 里程碑后缀）

采用语义化版本 `MAJOR.MINOR.PATCH[-里程碑后缀]`：

- **MAJOR**：不兼容的接口变更（V1 阶段固定为 1）
- **MINOR**：向下兼容的功能新增（每个里程碑发布递增）
- **PATCH**：向下兼容的缺陷修复
- **里程碑后缀**：`-m1`、`-m2`、...、`-rc1`（Release Candidate）、`-rcN`

示例：`v1.0.0-m3`（M3 里程碑基线）、`v1.0.0-rc1`（第一个发布候选）、`v1.0.0`（正式发布）。

### 4.4 提交规范（Conventional Commits + Scope）

采用 [Conventional Commits 1.0](https://www.conventionalcommits.org/)，并扩展 **scope**：

```
<type>(<scope>): <简短描述>

[可选 body：说明动机、变更要点]

[可选 footer：BREAKING CHANGE: ... / Refs: #issue]
```

| type | 用途 |
|---|---|
| `feat` | 新功能 |
| `fix` | 缺陷修复 |
| `refactor` | 重构（不改变外部行为） |
| `test` | 新增或修改测试 |
| `docs` | 文档变更 |
| `build` | 构建系统、依赖、Dockerfile、CI 配置 |
| `ci` | CI 流水线变更 |
| `chore` | 杂项（不修改源码也不修改测试） |

scope 取值：`booking`、`order`、`community`、`auth`、`product`、`service`、`marketing`、`admin-web`、`h5`、`cm`（配置管理）、`ci`、`docs`。

示例：
- `feat(booking): 支持按服务人员过滤可预约时段`
- `fix(auth): 修正格式错误 Authorization 头导致匿名访问`
- `build(ci): 新增 Jenkinsfile 与 GitHub Actions 流水线`

---

## 5. 基线管理

### 5.1 基线类型

| 基线 | 建立时机 | 内容 | 审批 |
|---|---|---|---|
| **功能基线** | 需求评审通过后 | `docs/requirements-source.md`、`docs/00-project-boundary.md` | CM 经理 + CCB |
| **里程碑基线** | 各里程碑（M1–M6）退出条件达成 | 全部源码 + 测试 + 文档 | CCB |
| **产品基线** | 正式发布前（如 v1.0.0-rc1） | 里程碑基线 + 通过全量回归 + 部署清单 | CCB + 用户验收 |

### 5.2 基线 tag 命名

里程碑基线：`v1.0.0-m1`、`v1.0.0-m2`、`v1.0.0-m3`、`v1.0.0-m4`、`v1.0.0-m5`、`v1.0.0-m6`

发布候选与正式版：`v1.0.0-rc1`、`v1.0.0-rc2`、`v1.0.0`

补丁版本：`v1.0.1`、`v1.0.2`

hotfix：`v1.0.1-hf1` 或遵循 SemVer 在 hotfix 分支上递增 PATCH。

### 5.3 基线建立流程

1. 里程碑退出条件全部达成（见 `docs/02-task-breakdown.md`）。
2. 配置管理员运行全量构建与测试，确认通过。
3. 在 `develop`（或对应 phase 分支）上打 tag：`git tag -a v1.0.0-mN -m "..."`。
4. 更新 `docs/基线清单.md`：记录 tag、commit SHA、变更摘要、审计状态。
5. 推送 tag：`git push origin v1.0.0-mN`。
6. 通过 CCB 评审后，将里程碑基线合并至 `main` 并打产品基线 tag。

### 5.4 基线变更

基线一旦建立，任何修改都必须走 §6 的变更控制流程；严禁直接修改已打 tag 的历史（禁止 `git rebase` 已发布 tag、禁止 `git push --force` 到受保护分支）。

---

## 6. 变更控制

### 6.1 变更分类

| 类别 | 说明 | 审批 |
|---|---|---|
| **A 类（轻微）** | 注释、文档错别字、不影响接口的代码格式 | 配置管理员审批即可 |
| **B 类（一般）** | 新增功能、接口扩展、配置调整 | CCB 评审 |
| **C 类（重大）** | 修改已基线的接口、数据库结构、核心业务规则（价格/库存/订单状态） | CCB 评审 + 用户确认 |

### 6.2 变更流程

```
   ┌────────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐    ┌──────────┐
   │ 1. 提交申请 │───►│ 2. 影响  │───►│ 3. CCB   │───►│ 4. 实施  │───►│ 5. 验证  │───►│ 6. 归档  │
   │  CR 表单   │    │  分析    │    │  审批    │    │ 受控分支 │    │ CI+回归  │    │  关闭 CR │
   └────────────┘    └──────────┘    └──────────┘    └──────────┘    └──────────┘    └──────────┘
```

1. **提交申请**：发起人在 `docs/变更申请单-实例/` 下复制 `docs/变更申请单-模板.md`，填写 CR-YYYYMMDD-NNN。
2. **影响分析**：配置管理员评估涉及哪些 CI、哪些基线、风险等级、回滚方案。
3. **CCB 审批**：A 类配置管理员即可批；B/C 类由 CCB 评审，结果记录在 CR 表单"审批"段。
4. **实施**：在受控分支（`feature/CR-...` 或对应 phase 分支）实施；提交信息以 `ref(CR-...): ...` 关联。
5. **验证**：CI 流水线全量通过 + 测试员回归测试通过 + 配置审计通过。
6. **归档**：更新配置项登记表、基线清单（如建新基线）、决策记录；关闭 CR。

### 6.3 紧急变更（Hotfix）

- 从 `main` 拉 `hotfix/CR-...` 分支，最小化修复。
- 修复后必须同时合并回 `main` 与 `develop`。
- 24 小时内补齐 CR 表单与测试。

### 6.4 变更申请单模板

见 `docs/变更申请单-模板.md`。已填写的实例位于 `docs/变更申请单-实例/`，至少包含 2 个真实案例（取自 `docs/08-pending-decisions.md` 的决策素材）。

---

## 7. 配置审计

### 7.1 审计类型

| 类型 | 目的 | 频次 |
|---|---|---|
| **功能配置审计（FCA）** | 验证基线满足需求与功能要求 | 每个里程碑 |
| **物理配置审计（PCA）** | 验证基线包含的 CI 与登记表一致、版本号正确、无遗漏 | 每个里程碑 |
| **过程审计** | 检查变更流程、提交规范、分支策略是否被遵守 | 项目收尾阶段 |

### 7.2 审计清单

**功能配置审计（FCA）**：
- [ ] 基线对应的需求条目全部实现且有测试覆盖
- [ ] JaCoCo 覆盖率报告显示关键模块 ≥ 80%
- [ ] 集成测试（Testcontainers MySQL）全部通过
- [ ] 前端契约测试（Vitest）全部通过

**物理配置审计（PCA）**：
- [ ] `git tag -l` 列出的 tag 与 `docs/基线清单.md` 完全一致
- [ ] 每个配置项的当前版本与 `docs/配置项登记表.md` 一致
- [ ] `.gitignore` 正确排除 target/、dist/、node_modules/、.env*
- [ ] 仓库无大文件、无密钥泄露（`.env`、JWT secret、数据库密码等）
- [ ] Docker 镜像可由 tag 对应 commit 一键构建复现

**过程审计**：
- [ ] 所有 commit 遵循 Conventional Commits
- [ ] 所有"基线后"变更均有对应 CR 表单
- [ ] 分支命名符合 §4.2
- [ ] CI 流水线历史中无红色失败被合并到基线

### 7.3 配置审计证据

配置审计的图片或视频证据存放于 `docs/audit-evidence/week16/`。

**已收集的命令文本类证据（自动化导出，见同目录 .md 文件）：**

| 文件 | 对应证据 |
|---|---|
| `audit-evidence-text.md` | git tag/branch/log、docker ps、三端可访问、API 数据 |
| `pca-checklist.md` | 配置项物理一致性核对、基线 tag 一致性 |
| `jacoco-coverage.md` | JaCoCo 覆盖率（class 93% / line 72% / 843 测试） |
| `jenkins-build-history.md` | Jenkins 构建历史（多次 SUCCESS 证明 CI 可重复） |

**需人工截图补充的证据（图片）：**

1. `git-tag-list.png` — 所有基线 tag 截图（在 IDE/git GUI 里截 `git tag -l` 输出）
2. `git-branch-list.png` — 分支模型截图（GitHub 仓库分支列表或 git GUI）
3. `ci-pipeline-green.png` — Jenkins 最近一次全绿构建截图（Jenkins #28 SUCCESS）
4. `coverage-report.png` — JaCoCo HTML 报告截图（Jenkins 构建页 → JaCoCo Coverage Report）
5. `docker-compose-up.png` — docker compose ps 截图（终端执行命令）
6. `running-system.png` — 浏览器三端访问截图（8080/8081/8082）
7. `change-control-records.png` — 变更申请单实例截图（docs/变更申请单-实例/）
8. `config-item-register.png` — 配置项登记表截图（docs/配置项登记表.md）
9. `audit-video.mp4` — 完整审计过程录屏（可选，加分）

---

## 8. 配置状态报告

### 8.1 周期与内容

- **频次**：每个里程碑结束时输出一次。
- **内容**：
  - 当前所有基线 tag 及对应 commit
  - 本周期新增/变更/退役的配置项
  - 本周期处理的 CR 列表与状态
  - CI 流水线运行统计（成功/失败次数、平均时长）
  - 覆盖率趋势
  - 配置审计发现的问题与整改情况

### 8.2 归档

配置状态报告归档于 `docs/status-reports/`，命名 `CSR-<里程碑或日期>.md`。

---

## 9. 配置管理工具

| 用途 | 工具 | 配置文件/位置 |
|---|---|---|
| 版本控制 | Git + GitHub | `.gitignore`、`.gitattributes`、远程仓库 |
| CI（主力） | Jenkins | `Jenkinsfile`、`jenkins/README.md` |
| CI（补充） | GitHub Actions | `.github/workflows/ci.yml`、`deploy.yml` |
| 容器化 | Docker + Docker Compose | `Dockerfile`、`docker-compose.yml`、`nginx/*.conf` |
| 后端构建 | Maven | `pom.xml` |
| 前端构建 | Vite / uni-app | `frontend/*/package.json`、`vite.config.ts` |
| 后端覆盖率 | JaCoCo 0.8.12 | `pom.xml` |
| 前端覆盖率 | Vitest coverage-v8 | `frontend/*/vitest.config.ts` |
| 静态检查（前端） | ESLint | `frontend/miniapp`（已配置）；admin-web 待补 |
| 通知 | Jenkins Email Extension | `jenkins/email-template.groovy` |

---

## 10. 配置管理实践维度映射

| 实践维度 | 本 CMP 对应章节 | 对应交付物 |
|---|---|---|
| 配置管理计划 | 全文 | 本文件 + 配套文档 |
| 提交日志 | §4.4 提交规范、§5 基线 | git log + CHANGELOG.md |
| 实施证据：配置项管理 | §3 | `docs/配置项登记表.md` |
| 实施证据：版本管理 | §4 | Git 分支与版本号 |
| 实施证据：基线管理 | §5 | `docs/基线清单.md` + git tag |
| 实施证据：变更管理 | §6 | `docs/变更申请单-*` |
| 实施证据：分支管理 | §4.2 | Git 分支模型 |
| 自动化流水线 | §9 | Jenkinsfile + GitHub Actions + Docker Compose |
| 演示验证 | §7.3 | 录屏 + 现场 |

---

## 11. 附录

- 附录 A：配置项登记表（独立文件 `docs/配置项登记表.md`）
- 附录 B：基线清单（独立文件 `docs/基线清单.md`）
- 附录 C：变更申请单模板（独立文件 `docs/变更申请单-模板.md`）
- 附录 D：构建指导书（独立文件 `docs/06-build-guide.md`）
- 附录 E：部署指南（独立文件 `docs/07-deployment-guide.md`）
- 附录 F：CHANGELOG（仓库根 `CHANGELOG.md`）

---

## 修订记录

| 版本 | 日期 | 修订人 | 说明 |
|---|---|---|---|
| v1.0 | 2026-06-22 | 配置管理组 | 首次发布，建立 CMP 基线 |
