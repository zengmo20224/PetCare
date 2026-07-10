# 用户端 H5 镜像 — 多阶段构建
# 关联文档：docs/06-build-guide.md §5.3、docs/07-deployment-guide.md §5.2
# CI：CI-BS-010
# 注意：build context 为仓库根（见 docker-compose.yml），源码在 frontend/miniapp/

# ---------- 阶段 1：构建 ----------
# node 24 与本地开发环境保持一致（避免 npm 10/11 的 lock 文件格式差异）
FROM node:24-alpine AS builder
WORKDIR /workspace

# 利用层缓存：先复制 package 元数据
COPY frontend/miniapp/package.json frontend/miniapp/package-lock.json ./
# 用 npm install 而非 npm ci：node:24-alpine 镜像内的 npm 版本可能与本地不同，
# 对 lock 文件中 @emnapi 等可选依赖的解析有差异，npm install 能自适应 reconcile，
# 而 npm ci 要求严格一致否则失败。
RUN npm install

# 复制源码并构建 H5
# uni-app build:h5 产物位于 /workspace/dist/build/h5/
COPY frontend/miniapp/ .
RUN npm run build:h5

# ---------- 阶段 2：nginx 托管静态资源 ----------
FROM nginx:1.27-alpine AS runtime

LABEL org.opencontainers.image.title="petcare-h5" \
      org.opencontainers.image.description="PetCare O2O 用户端 H5"

# 清空默认页面与配置
RUN rm -rf /usr/share/nginx/html/* /etc/nginx/conf.d/default.conf

# 拷贝 H5 构建产物（注意 uni-app 的产物路径是 dist/build/h5/）
COPY --from=builder /workspace/dist/build/h5 /usr/share/nginx/html

# 拷贝 nginx 配置（SPA fallback + /api + /uploads 反代）
COPY nginx/miniapp.conf /etc/nginx/conf.d/default.conf

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
  CMD wget -q --spider http://127.0.0.1/ || exit 1

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
