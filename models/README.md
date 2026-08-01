# V2 AI Agent 模型文件目录

> 关联：`docs/09-ai-agent-design.md` §7.2（embedding）、§8（图片审核）、`docs/08-pending-decisions.md` D-013

本目录存放 V2 AI Agent 所需的 ONNX 模型文件。**模型文件不入 git**（`.gitignore` 已排除 `*.onnx/*.bin/*.safetensors`），需在部署/构建时下载并校验 sha256。

## 模型清单

| 模型 | 用途 | 来源 | 大小 | 本地路径（相对项目根） | 状态 |
|---|---|---|---|---|---|
| all-MiniLM-L6-v2 | Embedding（384 维，本地 ONNX 推理） | HuggingFace `Xenova/all-MiniLM-L6-v2` | ~90 MB | `models/all-MiniLM-L6-v2/model.onnx` | ✅ 渠道通畅（M8.0 落地时下） |
| all-MiniLM-L6-v2 tokenizer | 配套分词器 | 同上仓库 | ~71 KB | `models/all-MiniLM-L6-v2/tokenizer.json` | ✅ 可入 git（小文件） |
| nsfw_model | 社区图片 NSFW 审核 | **渠道待定**（`GantMan/nsfw_model` 现 401 鉴权墙） | ~10 MB | `models/nsfw_model/nsfw_model.onnx` | ⚠️ M8.4 决策（D-013 Q-4） |

## 下载与校验

### Embedding 模型（M8.0 落地时执行）

```bash
# 1. 下载 ONNX 模型（匿名 CDN 可达，~90MB）
mkdir -p models/all-MiniLM-L6-v2
curl -L -o models/all-MiniLM-L6-v2/model.onnx \
  https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx

# 2. 下载 tokenizer（小文件，可入 git）
curl -L -o models/all-MiniLM-L6-v2/tokenizer.json \
  https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/tokenizer.json

# 3. 计算 sha256（填入下表，CI 校验用）
sha256sum models/all-MiniLM-L6-v2/model.onnx
```

| 文件 | sha256（M8.0 落地后填） |
|---|---|
| `model.onnx` | _待填_ |

### NSFW 模型（M8.4 决策后再下）

渠道待定（见 D-013 Q-4）。候选方案：
- (a) 注册 HF 账号 + `HF_TOKEN` 鉴权下载原 `GantMan/nsfw_model`
- (b) 团队本地训练/转换后托管到 GitHub Release 或私有对象存储
- (c) 改用第三方内容安全 API（如腾讯云/阿里云）替代本地 ONNX

**M8.0-M8.3 不依赖此模型**，M8.4 启动前由决策结果决定下载方式。

## 配置引用

模型路径由以下配置项控制（见 `.env.example` / `application.yml`）：
- `AI_EMBEDDING_ONNX_PATH`（默认 `./models/all-MiniLM-L6-v2/model.onnx`）
- `AI_MODERATION_NSFW_ONNX_PATH`（M8.4 落地时加，默认 `./models/nsfw_model/nsfw_model.onnx`）

## 边界约束

- 模型文件**不进 git**（`.gitignore` 规则），CI 构建时按本说明下载。
- PgVector 向量库只存派生知识副本（embedding），业务真源仍在 MySQL（`docs/09` §2 B6）。
- 图片审核输出"建议+置信度"，超阈值产 `PostReport` 走人工，不自动删帖（`docs/09` §5.3.2 B4）。
