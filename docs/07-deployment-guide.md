# 部署指南（Deployment Guide）

> 关联文档：`docs/03-configuration-management-plan.md` 附录 E、`docs/06-build-guide.md`
>
> 部署形态：**Docker Compose 单机部署**，适用于本地笔记本演示与小规模生产。

---

## 1. 部署架构

```
                       ┌─────────────────────────────────────┐
                       │         宿主机（演示笔记本）          │
                       │                                       │
   浏览器 ──── :8080 ──┼──►  nginx (admin-web)  ── 静态资源    │
                       │     │  /api/*  反向代理                │
                       │     ▼                                  │
                       │   petcare-api (Spring Boot)            │
                       │     │  JDBC                            │
                       │     ▼                                  │
                       │   mysql:8 (业务数据，数据卷持久化)       │
                       │                                       │
   浏览器 ──── :8081 ──┼──►  nginx (miniapp H5)  ── 静态资源   │
                       │     │  /api/*  反向代理                │
                       │     └─────► petcare-api                │
                       │                                       │
                       │   postgres+pgvector (V2 向量库，可选)   │
                       │     仅 AI_AGENT_ENABLED=true 时依赖     │
                       └─────────────────────────────────────┘
```

| 容器 | 镜像 | 端口映射 | 作用 |
|---|---|---|---|
| `mysql` | `mysql:8.0` | 127.0.0.1:3306 | 业务数据库（唯一真源） |
| `postgres` | `pgvector/pgvector:pg16` | 127.0.0.1:5432 | V2 AI 向量库（独立 PG，派生知识副本；`AI_AGENT_ENABLED=false` 时可不启） |
| `api` | `petcare-api:1.0.0` | 127.0.0.1:8082 → 8080 | 后端 Spring Boot |
| `admin-web` | `petcare-admin-web:1.0.0` | 0.0.0.0:8080 → 80 | 管理端 nginx |
| `h5` | `petcare-h5:1.0.0` | 0.0.0.0:8081 → 80 | 用户端 H5 nginx |

> 演示访问入口：
> - 管理端：http://localhost:8080
> - 用户端 H5：http://localhost:8081
> - 后端 API 直连（调试用）：http://localhost:8082/actuator/health

---

## 2. 前置准备

### 2.1 系统要求

| 项 | 最低 | 推荐 |
|---|---|---|
| CPU | 2 核 | 4 核 |
| 内存 | 4 GB | 8 GB |
| 磁盘 | 10 GB | 20 GB |
| Docker | 24+ | 最新稳定版 |
| Docker Compose | v2+ | 最新稳定版 |

### 2.2 安装 Docker

**Windows**：安装 [Docker Desktop](https://www.docker.com/products/docker-desktop)，启用 WSL2 后端。

验证：

```bash
docker version
docker compose version
```

---

## 3. 配置环境变量

复制 `.env.example` 为 `.env`，按实际填写：

```bash
cp .env.example .env
# Windows PowerShell: Copy-Item .env.example .env
```

关键字段（详见 `.env.example`）：

| 变量 | 说明 | 默认值 |
|---|---|---|
| `MYSQL_ROOT_PASSWORD` | MySQL root 密码 | changeme |
| `MYSQL_DATABASE` | 业务库名 | petcare_o2o |
| `DB_USERNAME` | 业务库用户 | petcare |
| `DB_PASSWORD` | 业务库密码 | changeme |
| `JWT_SECRET` | JWT 签名密钥（生产必须改） | 必填，≥32 字符 |
| `JWT_EXPIRATION_MINUTES` | JWT 过期分钟 | 120 |
| `AI_PROVIDER_ENABLED` | AI Provider 开关（V1 客服/分析） | false |
| `AI_AGENT_ENABLED` | V2 AI Agent 开关（RAG/工具调用，D-013） | false |
| `AI_RAG_ENABLED` | V2 向量检索开关（依赖 `AI_AGENT_ENABLED`） | false |
| `PGVECTOR_HOST/PORT/DB/USER/PASSWORD` | PgVector 连接（独立 PG 实例，V2 才用） | postgres/5432/petcare_ai/... |
| `IMAGE_TAG` | 镜像 tag | 1.0.0 |

> V2 AI Agent 默认关闭。仅当 `AI_AGENT_ENABLED=true` 时才依赖 `postgres` 容器；未启用 Agent 的演示部署可不启 PG（`docker compose up mysql api admin-web h5`）。详见 `docs/09-ai-agent-design.md`。

> **安全提醒**：`.env` 已在 `.gitignore` 中，不会被提交。生产部署请通过密钥管理工具（Vault、云 KMS）注入，不要明文落盘。

---

## 4. 首次部署

### 4.1 一键启动

```bash
# 在仓库根目录
docker compose up -d --build --wait
```

该命令会：

1. 构建后端 jar（多阶段 Dockerfile）
2. 构建管理端/H5 静态资源（多阶段 Dockerfile）
3. 构建 4 个镜像
4. 启动 MySQL、API、admin-web nginx、h5 nginx
5. 等待健康检查通过

### 4.2 数据库初始化

`docker-compose.yml` 中 MySQL 容器启动时自动挂载 `schema.sql` 与 `data-dev.sql` 到 `/docker-entrypoint-initdb.d/`，首次启动自动建表 + 灌种子数据。

> 仅**数据卷为空**时执行；卷已存在则跳过。重置方法见 §7。

### 4.3 验证部署

```bash
# 容器状态
docker compose ps

# 后端健康检查
curl http://localhost:8082/actuator/health
# 期望：{"status":"UP"}

# 管理端访问
# 浏览器打开 http://localhost:8080  → 看到登录页
# 默认管理员：admin / admin123456

# 用户端 H5 访问
# 浏览器打开 http://localhost:8081  → 看到 H5 首页
```

---

## 5. nginx 反向代理说明

### 5.1 管理端（`nginx/admin-web.conf`）

- 静态资源：`/` → `/usr/share/nginx/html/`
- API 反代：`/api/` → `http://api:8080/api/`
- SPA fallback：`try_files $uri $uri/ /index.html`

### 5.2 用户端 H5（`nginx/miniapp.conf`）

- 静态资源：`/` → `/usr/share/nginx/html/`
- API 反代：`/api/` → `http://api:8080/api/`
- SPA fallback：`try_files $uri $uri/ /index.html`

> 容器间通过 docker compose 网络 `petcare-net` 通信，故用服务名 `api` 作为主机名。

---

## 6. CI/CD 自动部署

### 6.1 Jenkins（主力演示）

`Jenkinsfile` 的 `Deploy` 阶段会执行：

```bash
docker compose up -d --build --wait
```

并在部署后跑健康检查与冒烟测试，结果通过邮件发送组员。

详见 `jenkins/README.md`。

### 6.2 GitHub Actions

`deploy.yml` 由 `v*` tag 触发，在 self-hosted runner 上执行 `docker compose up -d --build`。

详见 `.github/workflows/deploy.yml`。

---

## 7. 运维操作

### 7.1 查看日志

```bash
# 全部
docker compose logs -f

# 单个服务
docker compose logs -f api
docker compose logs -f mysql
```

### 7.2 重启服务

```bash
docker compose restart api
docker compose restart
```

### 7.3 升级版本

```bash
# 拉取新代码
git pull
git checkout v1.0.1   # 切换到目标基线 tag

# 重建并启动
docker compose up -d --build --wait
```

### 7.4 回滚

```bash
# 回到上一个基线
git checkout v1.0.0
docker compose up -d --build --wait
```

> 因每次发版都用独立 tag + 独立镜像版本号，回滚即切 tag 重新部署。

### 7.5 重置数据库（仅开发/演示）

```bash
docker compose down -v          # 删除卷，谨慎！会清库
docker compose up -d --build --wait
```

### 7.6 完全卸载

```bash
docker compose down -v --rmi local    # 停容器、删卷、删本地构建的镜像
```

---

## 8. 健康检查与监控

`docker-compose.yml` 为每个服务定义了 `healthcheck`：

| 服务 | 检查方式 | 间隔 | 超时 | 重试 |
|---|---|---|---|---|
| mysql | `mysqladmin ping` | 10s | 5s | 5 |
| api | `curl /actuator/health` | 15s | 5s | 5 |
| admin-web / h5 | `curl -f http://localhost/` | 30s | 5s | 3 |

`api` 依赖 mysql 的 healthcheck，确保数据库就绪后再启动后端。

---

## 9. 常见问题

| 问题 | 排查 |
|---|---|
| 端口 8080/8081 被占用 | `docker compose down`，或在 `.env` 改端口映射 |
| MySQL 启动失败 | 检查 `MYSQL_ROOT_PASSWORD` 是否设置；查看 `docker compose logs mysql` |
| 后端连不上 MySQL | 容器间用服务名 `mysql:3306`；检查 `depends_on` 健康检查 |
| H5 请求 404 | 检查 nginx SPA fallback；确认 H5 构建产物路径 `dist/build/h5/` |
| 镜像构建慢 | 配置国内镜像源；或先本地 `mvn package` 再 `docker compose build` |
| JaCoCo 不生成 | 确认测试阶段执行；`target/site/jacoco/index.html` |

---

## 10. 演示流程（推荐脚本）

配合课程演示录屏：

1. `git log --oneline -10` — 展示提交历史
2. `git tag -l` — 展示所有基线
3. 改一行 H5 文案 → `git commit -m "feat(h5): tweak hero text"` → `git push`
4. 观察 Jenkins 自动触发 → 直播编译/测试/打包/部署日志
5. 等待流水线绿 → 浏览器刷新 http://localhost:8081 → 看到改动生效
6. 展示 JaCoCo 报告 + 组员收到的测试邮件
7. 展示 CMP 文档 + 配置项登记表 + 变更申请单实例

---

## 修订记录

| 版本 | 日期 | 修订人 | 说明 |
|---|---|---|---|
| v1.0 | 2026-06-22 | 配置管理组 | 首次发布 |
