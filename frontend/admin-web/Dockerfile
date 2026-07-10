# 管理端镜像 — 多阶段构建
# 关联文档：docs/06-build-guide.md §5.2、docs/07-deployment-guide.md §5
# CI：CI-BS-009

# ---------- 阶段 1：构建 ----------
# 注意：build context 为仓库根（见 docker-compose.yml），前端源码在 frontend/admin-web/
# node 24 与本地开发环境保持一致（避免 npm 10/11 的 lock 文件格式差异）
FROM node:24-alpine AS builder
WORKDIR /workspace

# 利用层缓存：先复制 package 元数据
COPY frontend/admin-web/package.json frontend/admin-web/package-lock.json ./
# 用 npm install 适应容器内 npm 版本差异（同 miniapp Dockerfile 说明）
RUN npm install

# 复制源码并构建（产物在 /workspace/dist）
COPY frontend/admin-web/ .
RUN npm run build

# ---------- 阶段 2：nginx 托管静态资源 ----------
FROM nginx:1.27-alpine AS runtime

LABEL org.opencontainers.image.title="petcare-admin-web" \
      org.opencontainers.image.description="PetCare O2O 管理端（PC Web）"

# 清空默认页面与配置
RUN rm -rf /usr/share/nginx/html/* /etc/nginx/conf.d/default.conf

# 拷贝构建产物
COPY --from=builder /workspace/dist /usr/share/nginx/html

# 拷贝 nginx 配置（SPA fallback + /api 反代到后端）
COPY nginx/admin-web.conf /etc/nginx/conf.d/default.conf

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q --spider http://127.0.0.1/ || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
