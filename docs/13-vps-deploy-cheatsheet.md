# 国外 VPS 部署速查表（课程演示场景）

> 日期：2026-07-04 ｜ 场景：**课程作业 / 演示，自用测试** ｜ 关联：`docs/07-deployment-guide.md`
>
> 目标：把现有的 `docker-compose` 四件套（MySQL + API + admin-web + H5）跑在一台国外 VPS 上，
> 用一个域名 + 自动 HTTPS 暴露给浏览器访问，**几小时内能给人看**。
>
> ⚠️ 本文档**不替代**生产部署指南。它针对"演示/自用"场景做了简化，并明确标注了哪些简化
> 在真实生产中**不能**这么干。

---

## 0. 适用前提（先确认）

| 项 | 要求 |
|---|---|
| 用途 | 课程演示 / 自测 / 给老师同学看。**不**对外营业，**不**收集真实用户敏感信息。 |
| 用户范围 | 你自己 + 少数演示对象。 |
| 数据敏感度 | 演示数据，丢了能重建。**不要**灌入真实客户信息。 |
| 服务器位置 | 国外（无需 ICP 备案）。 |
| 合规 | 不涉及（自用测试）。如未来转公开营业，请回到 `docs/07` + `docs/12`。 |

---

## 1. 选型一览（按"够用 + 便宜 + 简单"）

| 层 | 推荐 | 月成本 | 说明 |
|---|---|---|---|
| VPS | Vultr / Hetzner / DigitalOcean / 搬瓦工 任一 | $5-6 | 1 vCPU / 1-2GB RAM 即可跑起四件套 |
| 系统 | Ubuntu 22.04 LTS | 0 | 文档命令按 Ubuntu 写 |
| 域名 | 任意注册商（Namecheap / Cloudflare / Porkbun） | $1-10/年 | 国外域名无需备案 |
| HTTPS | **Caddy**（自动 Let's Encrypt） | 0 | 比 nginx+certbot 简单一个数量级 |
| 反代 | Caddy（同一个） | 0 | 把 :80/:443 转发到内部 :8080/:8081 |
| 部署 | 项目自带的 `docker compose` | 0 | 已打包好，直接用 |

**总成本：约 ¥40-50/月**（VPS）+ ¥10/年（域名）。

---

## 2. 服务器准备（VPS 上一次性操作）

### 2.1 基础初始化

```bash
# 以 root 登录 VPS 后

# 1. 创建非 root 用户（演示用，避免长期用 root）
adduser petcare
usermod -aG sudo petcare

# 2. 切到该用户后续操作
su - petcare

# 3. 系统更新 + 装基础工具
sudo apt update && sudo apt upgrade -y
sudo apt install -y git curl ufw
```

### 2.2 装 Docker + Docker Compose

```bash
# 官方一键脚本（最省事）
curl -fsSL https://get.docker.com | sudo sh

# 把当前用户加入 docker 组（免 sudo 调 docker）
sudo usermod -aG docker $USER
newgrp docker

# 验证
docker --version
docker compose version
```

### 2.3 装 Caddy（自动 HTTPS 的关键）

```bash
# 官方 APT 源（最稳）
sudo apt install -y debian-keyring debian-archive-keyring apt-transport-https
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/gpg.key' | sudo gpg --dearmor -o /usr/share/keyrings/caddy-stable-archive-keyring.gpg
curl -1sLf 'https://dl.cloudsmith.io/public/caddy/stable/debian.deb.txt' | sudo tee /etc/apt/sources.list.d/caddy-stable.list
sudo apt update
sudo apt install -y caddy
```

### 2.4 防火墙（只开必要端口）

```bash
sudo ufw allow 22/tcp    # SSH
sudo ufw allow 80/tcp    # HTTP（Caddy 用于 Let's Encrypt 校验 + 跳转）
sudo ufw allow 443/tcp   # HTTPS
sudo ufw enable
```

> **不要**对外开 3306（MySQL）、8082（API）、8080/8081（管理端/H5 容器）。它们只在 VPS 内部走，
> 由 Caddy 反代对外。下面的 `.env` 会把容器端口绑到 `127.0.0.1`。

---

## 3. 域名 + DNS（10 分钟）

### 3.1 买域名

任意国外注册商买一个，例如 `petcare-demo.com`（举例）。

### 3.2 加 A 记录

在注册商的 DNS 管理里加一条 A 记录，指向你 VPS 的公网 IP：

```
类型  主机             值               TTL
A     @                <你的VPS IP>     Auto
A     admin            <你的VPS IP>     Auto   （可选，给管理端单独子域名）
```

### 3.3 等 DNS 生效（1-10 分钟）

```bash
# 在本地或 VPS 上验证
dig +short petcare-demo.com
# 应返回你的 VPS IP
```

> DNS 没生效时 Caddy 拿不到证书，会反复重试。一定要先确认解析通了再启 Caddy。

---

## 4. 拉代码 + 配 `.env`（5 分钟）

### 4.1 克隆项目

```bash
cd ~
git clone <你的仓库地址> petcare
cd petcare
git checkout <某个基线 tag，例如 v1.0.0-rc1>   # 演示用基线，别用开发分支
```

### 4.2 生成强密钥（演示也要改默认密码）

```bash
# 生成 JWT_SECRET（≥32 字符）
openssl rand -base64 48

# 生成 DB 密码
openssl rand -base64 24
```

把上面两条输出**记下来**，下一步填进 `.env`。

### 4.3 配 `.env`

```bash
cp .env.example .env
nano .env
```

**必须改动**的字段（演示版最小集）：

```dotenv
# 镜像版本（与 git tag 对应）
IMAGE_TAG=1.0.0

# 数据库 —— 用第 4.2 步生成的强密码替换 changeme
MYSQL_ROOT_PASSWORD=<生成的强密码1>
MYSQL_DATABASE=petcare_o2o
DB_USERNAME=petcare
DB_PASSWORD=<生成的强密码2>

# JWT —— 用第 4.2 步生成的串替换空值
JWT_SECRET=<生成的JWT密钥>
JWT_ISSUER=petcare-o2o-api
JWT_EXPIRATION_MINUTES=120

# AI Provider：VPS 演示场景默认关闭（未配 DEEPSEEK_API_KEY）；如需启用客服/分析，设 true 并配 key
AI_PROVIDER_ENABLED=false

# V2 AI Agent：演示场景默认关闭；启用需同时起 postgres 容器并配 PgVector 密码
AI_AGENT_ENABLED=false
AI_RAG_ENABLED=false
PGVECTOR_DB=petcare_ai
PGVECTOR_USER=petcare
PGVECTOR_PASSWORD=<生成的强密码3>

# 关键：所有容器端口绑到 127.0.0.1，不暴露公网，由 Caddy 反代
ADMIN_WEB_PORT=8080
H5_PORT=8081
API_PORT=8082
MYSQL_PORT=3317
```

> `docker-compose.yml` 已默认把 admin-web / h5 端口绑到 `127.0.0.1`（2026-08-15 安全加固），
> 无需再改本地文件；对外只留 Caddy。数据库密码等变量已改为 `${VAR:?}` 强制语法，
> `.env` 缺 `MYSQL_ROOT_PASSWORD / DB_PASSWORD / PGVECTOR_PASSWORD / JWT_SECRET` 会直接拒绝启动。

---

## 5. 启动后端服务栈

```bash
# 在项目根目录
docker compose up -d --build --wait

# 验证四件套都 healthy
docker compose ps
# 期望全部为 Up (healthy)

# 内部验证 API（注意是 127.0.0.1，不走公网）
curl -s http://127.0.0.1:8082/api/v1/system/health
# 期望 {"status":"UP", ...}
```

> 首次构建要下载 Maven 依赖和 npm 包，国外 VPS 速度正常，5-15 分钟。

---

## 6. 配置 Caddy 反代 + 自动 HTTPS

### 6.1 改 Caddyfile

```bash
sudo nano /etc/caddy/Caddyfile
```

替换为以下内容（把域名换成你自己的）：

```caddy
# 用户端 H5 —— 主域名
petcare-demo.com {
    encode gzip

    # API 反代到后端容器
    handle /api/* {
        reverse_proxy 127.0.0.1:8082
    }

    # 上传文件反代
    handle /uploads/* {
        reverse_proxy 127.0.0.1:8082
    }

    # 其余 → H5 静态站点容器
    reverse_proxy 127.0.0.1:8081
}

# 管理端 —— 子域名（可选，也可挂在 /admin 路径，但子域名更省事）
admin.petcare-demo.com {
    encode gzip

    handle /api/* {
        reverse_proxy 127.0.0.1:8082
    }

    handle /uploads/* {
        reverse_proxy 127.0.0.1:8082
    }

    reverse_proxy 127.0.0.1:8080
}
```

### 6.2 启动 / 重载 Caddy

```bash
sudo systemctl enable caddy
sudo systemctl restart caddy

# 看日志确认证书签发成功
sudo journalctl -u caddy -f
# 期望看到 "certificate obtained successfully" 字样
```

Caddy 会**自动**向 Let's Encrypt 申请证书，首次约 10-30 秒。证书过期前会自动续期，无需任何手动操作。

### 6.3 验证 HTTPS

浏览器打开：

- `https://petcare-demo.com` → 看到 H5 首页（绿色小锁 ✅）
- `https://admin.petcare-demo.com` → 看到管理端登录页

---

## 7. 演示访问清单

| 入口 | 地址 | 账号 |
|---|---|---|
| 用户端 H5 | `https://petcare-demo.com` | `13800138001` / 部署时自行设置（见下） |
| 管理端 | `https://admin.petcare-demo.com` | 部署时自行创建，勿用种子数据 |
| API 健康 | `https://petcare-demo.com/api/v1/system/health` | 无需登录 |

> ⚠️ H-2 安全修复（2026-08-14）：生产部署**不再默认挂载 dev 种子数据**（data-dev.sql 已收敛到
> `docker-compose.dev.yml`，仅本地开发叠加使用）。旧文档此处的 `admin/admin123456`、
> `user123456` 是仓库公开的弱口令——生产库若已导入种子，应用会在 prod profile 启动时被
> `AdminWeakCredentialStartupCheck` 直接拒绝启动。生产账号请在初始化后立即设置强口令，
> 或通过管理端改密。

---

## 8. 日常运维命令（备查）

```bash
cd ~/petcare

# 看实时日志
docker compose logs -f api
docker compose logs -f mysql

# 重启单个服务
docker compose restart api

# 拉新版代码 + 重建
git pull
docker compose up -d --build --wait

# 停掉整套（保留数据）
docker compose down

# 完全清库重来（演示重置，谨慎！会删所有数据）
docker compose down -v
docker compose up -d --build --wait
```

---

## 9. 演示前的自检清单

部署完成后，对外演示前逐项确认：

- [ ] `https://petcare-demo.com` 浏览器绿锁，能打开 H5 首页
- [ ] `https://admin.petcare-demo.com` 管理端能登录
- [ ] H5 用户登录 → 预约服务 → 看到预约成功
- [ ] H5 商品下单 → 库存扣减 → 订单详情正确
- [ ] 管理端能看到新预约 / 新订单
- [ ] 社区发帖 → 上传图片 → 显示正常
- [ ] API 健康检查返回 `UP`
- [ ] `.env` 里所有密码都已改、JWT_SECRET 非空且 ≥32 字符
- [ ] UFW 已启用，仅 22/80/443 对外
- [ ] MySQL(3306) / API(8082) / 容器 nginx(8080/8081) **未**暴露公网

---

## 10. 演示结束后的安全收尾

国外 VPS 公网暴露，即使只是演示，也建议演示完后做一件事：

```bash
# 方案 A：直接停服务（最省心）
cd ~/petcare
docker compose down

# 方案 B：保留服务但关掉公网入口（保留数据继续自测）
sudo ufw deny 80/tcp
sudo ufw deny 443/tcp
# 需要再次演示时 sudo ufw allow 80/tcp && sudo ufw allow 443/tcp
```

---

## 11. ⚠️ 与生产部署的差异（重要）

本速查表为演示场景做了以下简化，**真实生产不能照搬**：

| 项 | 本文档（演示） | 生产要求（`docs/07`） |
|---|---|---|
| 服务器位置 | 国外（无备案） | 大陆境内需 ICP 备案 |
| 数据库备份 | 无（演示数据可重建） | 定时备份 + 异地存档 + 恢复演练 |
| 监控告警 | 仅 health 探针 | Prometheus + 告警通知 |
| 密钥管理 | `.env` 文件 | Vault / 云 KMS / docker secret |
| 种子数据 | 不挂载（H-2 修复后 compose 默认无种子） | 保持不挂载，初始化后强制改密 |
| 日志 | 容器本地 | 集中收集 + 保留策略 |
| HTTPS | Caddy 自动 | nginx + certbot 或 Caddy（皆可） |
| 高可用 | 单机 | 至少数据库主从、应用多副本 |
| 安全漏洞修复 | 暂缓（演示） | 必须修（见 `docs/11` H1/H2/M1/M3） |
| 用户协议/隐私政策 | 无 | 必须有（个人信息保护法） |
| 在线支付 | 不涉及 | 小程序场景需商户号 |

如果未来场景升级为"真实对外营业"，请回到 `docs/07-deployment-guide.md` + `docs/12-wechat-miniprogram-launch-plan.md`，按生产要求重做。

---

## 修订记录

| 版本 | 日期 | 作者 | 说明 |
|---|---|---|---|
| v1.0 | 2026-07-04 | Agent | 首版，针对课程演示 + 国外 VPS 场景 |
