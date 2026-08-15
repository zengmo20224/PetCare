# UI 组件库与图标选型决策记录

> 决策日期：2026-08-01 ｜ 实施完成：2026-08 UI 改版（`64291f1`，契约守卫重锚定 `bad0747` 160/160 全绿）
>
> 本文档合并原 `14-ui-library-icon-research.md`（UI 组件库 + iconfont 初步调研）与 `17-icon-library-selection.md`（开源线性图标库 7 候选横评）两份调研的**终局结论**，原两份文档已删除（git 历史可查）。全量设计规格见 `docs/16-mp-native-ui-design-demo.md`（现行 UI 设计基线）。

## 0. TL;DR

| 维度 | 终局决策 | 状态 |
|---|---|---|
| **UI 组件库** | **保持 wot-design-uni（v1.14.0），不换库**——已深度集成（easycom、PcBottomNav、wd-card/wd-button/wd-tag），换库成本远大于收益；uView 等替代品的组件数量优势本项目用不上，且图标同样无宠物场景 | ✅ 已落地 |
| **图标库** | **Tabler Icons（MIT，6184 个线性图标，24×24 网格）**，描边 1.8 | ✅ 已落地 |
| **接入方式** | **方案 B：SVG → iconfont 字体 + base64 内联 @font-face**（< 40KB），封装 `PcIcon` 组件；不引入额外运行时依赖 | ✅ 已落地 |
| **tabBar PNG** | 保留不动——小程序 tabBar 只支持 PNG/JPG，字体图标改不了 | ✅ 维持 |
| **emoji** | 功能入口 emoji 全部替换为字体图标；空状态装饰 emoji 视情况保留或替换 | ✅ 已随改版处理 |

## 1. 决策一：UI 组件库不换（原 docs/14 §2）

**候选对比结论**：uView Plus（180+ 组件，JS 编写）、uv-ui、uni-ui、TuniaoUI 均无不可替代组件；uView 的 uicon 同样没有宠物场景图标（与 wot 一样是通用图标集），换库只带来 TS 类型推导退化与全站 `wd-` 前缀重写风险。

**换库成本**（若未来重新评估）：easycom 配置替换 + 数十处 `wd-` 组件重写 + 主题变量（`--pc-user-*` 与 wot 主题耦合）重新对齐。触发条件应为新组件库提供 wot 完全缺失且业务必需的能力。

## 2. 决策二：Tabler Icons + 字体 base64（原 docs/17 全文结论）

### 2.1 为什么是 Tabler

7 候选横评（Tabler / Lucide / Phosphor / MingCute / IconPark / Remix / Iconify 运行时）关键数据：

| 库 | 许可证 | 唯一图标数 | 宠物场景覆盖（25 项实测） | 结论 |
|---|---|---|---|---|
| **Tabler** ⭐ | MIT | 6184 | **25/25**（dog/cat/bone/paw/bath/scissors/building-store 全有） | 选定 |
| Lucide | ISC（≈MIT） | ~1500 | 20/25（缺 shower/door/store） | 备选 |
| Phosphor | MIT | 1512×6 粗细 | 21/25（命名偏差：bathtub vs bath） | 备选 |
| MingCute | Apache 2.0（许可标注不一致） | 3324 | 18/25 | 降权 |
| IconPark | **Apache 2.0 非 MIT** | 2658×4 | 16/25（缺 paw/shower/bath） | 降权 |
| Remix | MIT | ~3000 | 11/25（**缺 paw/dog/cat/bath 核心**） | 排除 |
| Iconify 运行时 | MIT | 20 万+聚合 | - | **小程序不兼容**（运行时 SVG） |

注意：网上流传的"Tabler 2 万+图标"是错的，官方实测 6184；Lucide 约 1500 也常被高估。

### 2.2 小程序兼容性真相（选型的硬约束）

微信小程序渲染引擎对 SVG 支持极其有限：`<svg>` 内联 ❌、`cover-image` 引 SVG ❌、SVG sprite `<use>` ❌、`background-image` data-URI 仅 webview 渲染可用。**`@font-face` 字体图标（ttf/woff base64 内联或 HTTPS CDN）是唯一全端稳定方案**——这正是 wot-design-uni 自己加载图标的同一套机制。

### 2.3 方案 B 落地要点

- SVG 源（stroke 2）导出 → iconfont 字体 → base64 内联 `@font-face`（< 40KB 时直接内联，超出走 CDN）
- `PcIcon` 组件封装（替代原 PcServiceIcon 的 H5 SVG + 小程序 emoji 双轨实现，两端统一）
- 描边统一 1.8（SVG 版可调；字体版固定，靠字体生成时统一）
- 备选进阶方案 D：unplugin-icons 静态编译 + 显式 import（按需、tree-shakable，但需构建链改造，本项目未采用）

## 3. 演进链与现状

```
docs/14（08-01 下午）：诊断四套图标并存问题 → 组件库不换 + iconfont 初步方向
  ↓
docs/16（08-01 晚）：全量 UI 设计稿（设计语言 + 全页面规格 + 图标/配色全集）
  ↓
docs/17（08-01 深夜）：图标库深化横评 → 终局：Tabler + 方案 B
  ↓
2026-08 UI 改版落地（64291f1）→ 契约守卫重锚定（bad0747，160/160）
  ↓
本文档（2026-08-15）：合并 14+17 终局结论，原始调研细节见 git 历史
```

现行规范：组件用法与图标全集以 `docs/16-mp-native-ui-design-demo.md` 为准（§7.2 图标全集、§1.6 图标方案）。
