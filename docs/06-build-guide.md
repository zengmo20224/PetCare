# 构建指导书（Build Guide）

> 关联文档：`docs/03-configuration-management-plan.md` 附录 D、`docs/05-testing-and-verification.md`、`docs/07-deployment-guide.md`
>
> 适用范围：本地构建、CI 流水线构建、Docker 镜像构建。

---

## 1. 构建环境前置条件

| 工具 | 最低版本 | 用途 | 验证命令 |
|---|---|---|---|
| JDK | 17 | 后端编译/运行 | `java -version` |
| Maven | 3.9+ | 后端构建 | `mvn -v` |
| Node.js | 20 LTS | 前端构建 | `node -v` |
| npm | 10+ | 前端依赖管理 | `npm -v` |
| Docker | 24+ | 镜像构建/容器化部署 | `docker version` |
| Docker Compose | v2+ | 多容器编排 | `docker compose version` |
| Git | 2.40+ | 版本控制 | `git --version` |

> CI 环境（Jenkins / GitHub Actions）会自带 JDK/Maven/Node；详见 §3、§4。

---

## 2. 本地构建

### 2.1 后端（petcare-o2o-api）

```bash
# 1. 安装依赖 + 编译 + 单元测试（默认排除 tc-mysql 集成测试）
mvn clean test

# 2. 仅编译，不跑测试
mvn clean compile

# 3. 打包可执行 jar（产物：target/petcare-o2o-api-0.1.0-SNAPSHOT.jar）
mvn clean package -DskipTests

# 4. 同时生成 JaCoCo 覆盖率报告（默认随 test 阶段生成）
mvn clean test
# 报告位置：target/site/jacoco/index.html

# 5. 跑 Testcontainers MySQL 集成测试（需要本地 Docker daemon）
mvn -P tc-mysql test

# 6. 启动开发服务（默认 profile 为 prod，本地必须显式指定 dev）
SPRING_PROFILES_ACTIVE=dev mvn spring-boot:run
```

**Profile 说明**：
- `application.yml` 默认 `spring.profiles.active=prod`（安全默认：生产裸跑不会静默落入 dev）；
  本地开发需显式 `SPRING_PROFILES_ACTIVE=dev`，连接本地 MySQL。
- 测试自动激活 `test` profile，使用 H2 内存库。
- 集成测试通过 `@ActiveProfiles("tc-mysql")` 使用 Testcontainers MySQL 8.0.46。

### 2.2 管理端（admin-web）

```bash
cd frontend/admin-web

# 1. 安装依赖
npm install

# 2. 开发模式
npm run dev

# 3. 生产构建（产物：frontend/admin-web/dist/）
npm run build        # 等价于 vue-tsc -b && vite build

# 4. 契约测试
npm run test:contract

# 5. 预览构建产物
npm run preview
```

### 2.3 用户端 H5（miniapp）

```bash
cd frontend/miniapp

# 1. 安装依赖
npm install

# 2. H5 开发模式
npm run dev:h5

# 3. H5 生产构建（产物：frontend/miniapp/dist/build/h5/）
npm run build:h5

# 4. 单元测试（vitest）
npm run test

# 5. 类型检查
npm run typecheck

# 6. Lint
npm run lint
```

---

## 3. CI 流水线构建（Jenkins）

Jenkinsfile 位于仓库根目录，由 Jenkins 通过 Pipeline from SCM 拉取。

**流水线阶段**：

```
┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐
│ Checkout│─►│ Backend │─►│ Backend │─►│ Backend │─►│ Docker  │─►│ Deploy  │─►│ Report  │
│         │  │ Build   │  │ Test +  │  │ Package │  │ Build   │  │ Compose │  │ & Email │
│         │  │ (mvn -V)│  │ JaCoCo  │  │ (jar)   │  │ (3 镜像)│  │ up -d   │  │         │
└─────────┘  └─────────┘  └─────────┘  └─────────┘  └─────────┘  └─────────┘  └─────────┘
```

详细配置见 `Jenkinsfile` 和 `jenkins/README.md`。

### 3.1 Jenkins 主要阶段命令

```groovy
// Backend Build
sh 'mvn -B -V clean compile'

// Backend Test + Coverage
sh 'mvn -B test'
junit 'target/surefire-reports/*.xml'
jacoco execPattern: '**/target/jacoco.exec', ... // 阈值由 CMP §7.2 决定

// Backend Package
sh 'mvn -B package -DskipTests'
archiveArtifacts artifacts: 'target/*.jar', fingerprint: true

// Docker Build
sh 'docker compose build'

// Deploy
sh 'docker compose up -d --wait'
```

### 3.2 Jenkins 触发方式

- **手动**：Jenkins UI 点击 "Build Now"
- **轮询 SCM**：每 5 分钟检查远程仓库（`pollSCM: 'H/5 * * * *'`）
- **Webhook**（推荐）：GitHub Push 事件触发（见 `jenkins/README.md` §3 配置步骤）

---

## 4. CI 流水线构建（GitHub Actions）

位于 `.github/workflows/`：

| 文件 | 触发 | 内容 |
|---|---|---|
| `ci.yml` | push 到任意分支 / PR 到 `main`、`develop` | 后端编译+测试、前端 build+test、上传 JaCoCo/Vitest 报告 artifact |
| `deploy.yml` | 推送 `v*` tag | 构建 Docker 镜像 → 触发部署（self-hosted runner 拉起 docker-compose） |

详见 `.github/workflows/ci.yml`、`.github/workflows/deploy.yml`。

---

## 5. Docker 镜像构建

### 5.1 后端镜像

```bash
# 在仓库根目录
docker build -t petcare-api:1.0.0 -f Dockerfile .
```

多阶段构建：
- **阶段 1**（builder）：`maven:3.9-eclipse-temurin-17` 编译打包 jar
- **阶段 2**（runtime）：`eclipse-temurin:17-jre` 仅运行 jar，镜像更小

### 5.2 管理端镜像

```bash
cd frontend/admin-web
docker build -t petcare-admin-web:1.0.0 -f Dockerfile .
```

多阶段：node 构建 → nginx 托管 `dist/`。

### 5.3 用户端 H5 镜像

```bash
cd frontend/miniapp
docker build -t petcare-h5:1.0.0 -f Dockerfile .
```

多阶段：node build:h5 → nginx 托管 `dist/build/h5/`。

### 5.4 一键构建全部镜像

```bash
docker compose build
```

---

## 6. 构建命令速查矩阵

| 目标 | 本地命令 | CI（Jenkins） | CI（GH Actions） |
|---|---|---|---|
| 后端编译 | `mvn clean compile` | `mvn -B -V clean compile` | `mvn -B clean compile` |
| 后端测试 | `mvn test` | `mvn -B test` | `mvn -B test` |
| 后端覆盖率 | 随 test 生成 | jacoco 步骤 | 上传 artifact |
| 后端打包 | `mvn package -DskipTests` | `mvn -B package -DskipTests` | 不打包（用 Docker） |
| TC MySQL 集成测试 | `mvn -P tc-mysql test` | 不在默认 pipeline | 可选 job |
| 管理端构建 | `npm run build` | `npm ci && npm run build` | `npm ci && npm run build` |
| 管理端测试 | `npm run test:contract` | `npm run test:contract` | `npm run test:contract` |
| H5 构建 | `npm run build:h5` | `npm ci && npm run build:h5` | `npm ci && npm run build:h5` |
| H5 测试 | `npm run test` | `npm run test` | `npm run test` |
| 镜像构建 | `docker compose build` | `docker compose build` | `docker compose build` |
| 部署 | `docker compose up -d` | `docker compose up -d --wait` | self-hosted runner |

---

## 7. 构建产物归档

| 产物 | 位置 | 归档方式 |
|---|---|---|
| 后端 jar | `target/*.jar` | Jenkins `archiveArtifacts`；GitHub Actions `upload-artifact` |
| JaCoCo 报告 | `target/site/jacoco/` | Jenkins jacoco 插件；GitHub Actions upload-artifact |
| 后端 surefire XML | `target/surefire-reports/` | Jenkins junit 插件 |
| 管理端 dist | `frontend/admin-web/dist/` | 用于 Docker 镜像构建 |
| H5 dist | `frontend/miniapp/dist/build/h5/` | 用于 Docker 镜像构建 |
| 前端测试报告 | `frontend/*/coverage/` | GitHub Actions upload-artifact |
| Docker 镜像 | 本地 daemon / registry | `docker save` / push 到 registry |

---

## 8. 构建失败排查

| 现象 | 可能原因 | 解决 |
|---|---|---|
| `mvn test` 报 JaCoCo agent 错误 | jacoco-maven-plugin 与 JDK 版本不匹配 | 确认 JDK 17；锁定 plugin 0.8.12 |
| Testcontainers 测试报 docker daemon 错 | 本地 Docker 未启动 | `docker info` 检查 |
| `npm run build:h5` 报 uni-app 错误 | node 版本不兼容 | 用 Node 20 LTS |
| 前端构建报 `vite` 找不到 | 依赖未安装 | `npm ci`（CI）或 `npm install`（本地） |
| Docker build 报 `mvn not found` | 基础镜像不对 | 后端镜像必须用 `maven:*` 构建阶段 |
| `docker compose up` 后端起不来 | MySQL 未就绪 | compose 已配 `depends_on.healthcheck`，等待 `--wait` |

---

## 9. 前端覆盖率启用（加分项，可选）

> 后端已通过 JaCoCo 覆盖；前端两端默认未启用覆盖率采集。本节给出启用步骤，启用后可在 CI 中归档三端统一覆盖率报告，满足 CMP §7.2 "关键模块覆盖率 ≥ 80%" 的可度量要求。

### 9.1 admin-web 启用步骤

```bash
cd frontend/admin-web

# 1. 安装覆盖率工具
npm install -D @vitest/coverage-v8

# 2. 在 package.json 的 scripts 中新增：
#    "test": "vitest run",
#    "test:coverage": "vitest run --coverage"

# 3. 在 vitest.config.ts 的 test 段追加 coverage 配置：
#    coverage: {
#      provider: 'v8',
#      reporter: ['text', 'html', 'lcov'],
#      reportsDirectory: './coverage',
#      include: ['src/**/*.{ts,vue}'],
#      exclude: ['src/__tests__/**', 'src/**/*.d.ts'],
#      thresholds: { lines: 70, functions: 70, branches: 60, statements: 70 }
#    }

# 4. 运行
npm run test:coverage
# 报告：frontend/admin-web/coverage/index.html
```

### 9.2 miniapp 启用步骤

```bash
cd frontend/miniapp

# 1. 安装
npm install -D @vitest/coverage-v8

# 2. 在 package.json scripts 新增：
#    "test:coverage": "vitest run --coverage"

# 3. vitest.config.ts 追加同上 coverage 配置（include: ['src/**/*.{ts,vue}']）

# 4. 运行
npm run test:coverage
```

### 9.3 在 GitHub Actions 中归档前端覆盖率

在 `.github/workflows/ci.yml` 的 `admin-web` / `h5` job 测试步骤后加：

```yaml
- name: Upload coverage
  if: always()
  uses: actions/upload-artifact@v4
  with:
    name: <端名>-coverage
    path: frontend/<端名>/coverage/
```

### 9.4 为什么默认不启用

- 避免向两个前端的 `package-lock.json` 引入新依赖而影响现有契约测试。
- 覆盖率工具约增加 30-50MB 依赖体积。
- 按需启用：小组交付前如需展示三端统一覆盖率，按本节步骤一次性开启即可。

---

## 修订记录

| 版本 | 日期 | 修订人 | 说明 |
|---|---|---|---|
| v1.0 | 2026-06-22 | 配置管理组 | 首次发布 |
