# 14. UI 组件库与图标库调研报告

> 调研日期：2026-08-01
> 调研范围：uni-app 生态 UI 组件库 + 图标方案，解决"自绘 UI 组件图标十分丑"问题
> 项目定位：毕业设计 demo，H5 优先 + 微信小程序 demo（见 AGENTS.md §2）
> 状态：**调研结论**（未改任何 src/ 代码）

---

## 0. TL;DR（核心结论）

| 维度 | 结论 |
|---|---|
| **UI 组件库** | **继续用 wot-design-uni（v1.14.0），不换库**。已深度集成（easycom、PcBottomNav、wd-card/wd-button/wd-tag 等），换库成本远大于收益。 |
| **图标库** | **新增 iconfont（阿里字体图标）作为补充**，专治"宠物服务图标丑"。wot 内置 295 图标无宠物场景图标（无洗澡/美容/爪子/寄养），iconfont 有海量宠物图标集。 |
| **接入方式** | 在 `wd-icon` 的 `classPrefix` 上挂自定义前缀（官方支持的扩展点），或新建一个 `PcIcon` 组件封装。**不需要引入额外 npm 包**。 |
| **小程序兼容性** | iconfont 用 **base64 内联 @font-face**（< 40KB 时直接 base64；> 40KB 用 CDN 网络字体）——这正是 wot 自己加载图标的同一套机制（`at.alicdn.com` 网络字体），已被验证可行。 |
| **PcServiceIcon** | 重构为 `wd-icon` + iconfont class，删除内联 SVG（H5）和 emoji fallback（小程序），两端统一为字体图标。 |
| **tabBar PNG** | **保留不动**。小程序 tabBar 只支持 PNG/JPG 图片，字体图标改不了 tabBar。当前 PNG 质量可接受。 |
| **散落 emoji** | 分两类处理：装饰性 emoji（如空状态 🛍️/🎉）可保留；功能入口 emoji（如公告 📢、购物车 🛒）替换为 iconfont 图标。 |
| **工作量** | 中等（约 0.5–1 天）：建 iconfont 项目 → 选图标 → 生成字体 → 封装组件 → 替换 4 个服务图标 + 5–8 个功能 emoji。 |

---

## 1. 现状诊断

### 1.1 当前图标方案清单（全量扫描结果）

扫描 `frontend/miniapp/src/` 得到 4 类图标使用点：

#### A. PcServiceIcon.vue —— 自绘 SVG（核心痛点）

**文件**：`frontend/miniapp/src/components/PcServiceIcon.vue`

```vue
<!-- H5 端：内联 SVG（stroke-width 1.8，round 线帽）-->
<svg v-if="name === 'bath'" viewBox="0 0 24 24" ...>...</svg>
<!-- 小程序端：emoji 兜底（因为小程序不支持 <svg>）-->
<text class="pc-service-icon__glyph">{{ glyph }}</text>
```

**4 个服务图标**：
| name | H5 渲染 | 小程序渲染 | 使用位置 |
|---|---|---|---|
| `bath`（洗护） | 内联 SVG 浴缸+水滴 | `🛁` emoji | `pages/home/index.vue` 快捷入口 |
| `groom`（美容） | 内联 SVG 剪刀 | `✂️` emoji | 同上 |
| `home`（上门照护） | 内联 SVG 房子+爪印 | `🏠` emoji | 同上 |
| `foster`（安心寄养） | 内联 SVG 心形+爪印 | `🐾` emoji | 同上 |

**为什么丑**：
1. **双端不一致**：H5 是手画 SVG（线条粗细 1.8、几何粗糙），小程序直接退化成 emoji，视觉完全脱节。
2. **SVG 手绘质量低**：path 坐标手算，浴缸/剪刀/房子的比例和线条不如专业图标库（如 Iconify 的 `lucide`、`tabler`、`ph` 系列）。
3. **小程序 emoji 风格不统一**：🛁✂️🏠🐾 四个 emoji 来自不同系统字体，跨端显示差异大（iOS 彩色 vs Android 灰度）。
4. **与 wot 设计语言割裂**：wot 的图标是 295 个统一风格的字体图标，PcServiceIcon 自成一套。

**使用点**：仅 `pages/home/index.vue` 第 70 行（4 个彩色快捷入口圆角方块内）。

#### B. wd-icon（wot-design-uni 内置）—— 5 处

| 文件 | 图标名 | 用途 |
|---|---|---|
| `components/PcBottomNav.vue:11` | `home` / `calendar` / `chat` / `cart` / `user` | 底部导航 5 个 tab |
| `pages/home/index.vue:15` | `check-outline` / `lock` | 营业状态徽章 |
| `pages/home/index.vue:184` | `heart` | 帖子点赞数 |
| `pages/home/index.vue:188` | `chat` | 帖子评论数 |
| `pages/ai/status.vue:18` | `check` | AI 状态勾选 |

wd-button 的 `icon` prop 也用 wot 内置图标：`arrow-right`（home 页"逛逛商店"/"查看全部"按钮）。

**结论**：wd-icon 用法正确，图标名都在 wot 内置范围内，**这部分不丑、不需要改**。

#### C. tabBar PNG 图标 —— 5 组 10 张

**配置**：`src/pages.json` 第 164–204 行
**文件**：`src/static/icons/` 下 10 张 PNG（home/booking/community/product/profile 各 normal+active）

| tab | iconPath | selectedIconPath | 大小 |
|---|---|---|---|
| 首页 | home.png (2.3KB) | home-active.png (5.3KB) | 30px |
| 预约 | booking.png (2.7KB) | booking-active.png (5.5KB) | 30px |
| 社区 | community.png (3.2KB) | community-active.png (5.6KB) | 30px |
| 商品 | product.png (2.2KB) | product-active.png (5.0KB) | 30px |
| 我的 | profile.png (2.6KB) | profile-active.png (5.7KB) | 30px |

**结论**：PNG 质量可接受，且**小程序 tabBar 只支持图片，不能用字体图标**——这部分**无法改也无需改**。H5 端用的是自定义 `PcBottomNav.vue`（wd-icon 字体图标），PNG 仅小程序端生效。

#### D. 散落 emoji —— 约 25 处

分两类：

**功能入口 emoji（建议替换为 iconfont）**：
| 文件 | emoji | 用途 |
|---|---|---|
| `pages/home/index.vue:45` | 📢 | 公告卡片图标 |
| `pages/products/index.vue:72` | 🛒 | 购物车 FAB 按钮 |
| `pages/ai/chat.vue:24` | ✨ | "新对话"按钮 |
| `pages/ai/chat.vue:31` | 🔒 | 登录锁图标 |

**空状态/装饰 emoji（可保留，iconfont 也能替换以提升统一性）**：
| emoji | 用途 | 出现文件 |
|---|---|---|
| 🛍️ | 商品空状态 | home, products |
| 🎉 | 活动空状态 | home |
| 💬 | 社区空状态 | home |
| 📅 | 预约空状态 | booking/list |
| 🧾 | 订单空状态 | order/list |
| 🔔 | 通知空状态 | notifications |
| 🐾 | 服务空状态 / 兜底 | services, community, register |
| ❤️🤍 | 点赞 | community/detail, community/index |
| ⭐☆ | 收藏 | community/detail |
| 🔍 | 搜索 | community |
| ✓✕★ | 勾选/关闭/星标（文本符号，非 emoji） | cart, addresses, booking |

### 1.2 问题汇总

1. **PcServiceIcon 双端割裂**：H5 SVG + 小程序 emoji，视觉完全不同。
2. **SVG 手绘质量低**：非专业图标，线条/比例粗糙。
3. **功能入口用 emoji**：📢🛒✨🔒 跨端显示不一致，不够专业。
4. **宠物服务图标缺失**：wot 内置 295 图标无 bath/groom/paw/pet 相关图标（验证：扫了完整图标名列表，无任何宠物/洗护/爪子图标）。
5. **图标来源分散**：SVG 自绘 + wd-icon + emoji + PNG 四套并存，无统一设计语言。

---

## 2. UI 组件库对比

### 2.1 候选库对比表（2026 年现状）

| 库 | 语言 | Vue3 | 小程序 | 组件数 | 图标方案 | 维护活跃度 | 文档 | TS 体验 |
|---|---|---|---|---|---|---|---|---|
| **wot-design-uni** ⭐当前 | TS 原生 | 原生 | ✅ 全端 | 70–80+ | 内置 295 图标（iconfont CDN） | 高（2026-01 发版 1.14.0，正迁移到 wot-ui.cn） | 优秀（v1.wot-ui.cn） | 一流 |
| **uView Plus** (uview-plus) | JS | 组合式 API 兼容 | ✅ 含鸿蒙/uni-app-x | 180+ | 自带 uicon（约 200+） | 中（社区维护，更新慢于 wot） | 良好 | 一般（需补类型） |
| **uv-ui** | JS | vue2/3 双兼容 | ✅ | 100+ | 复用 uView 图标 | 中低 | 一般 | 一般 |
| **uni-ui** | TS | ✅ | ✅ 官方全端 | 50+ | uni-icons（约 120） | 高（DCloud 官方） | 官方但简略 | 良好 |
| **TuniaoUI** | TS | 原生 | ✅ | 60+ | 自带图标集 | 中（新兴） | 良好 | 良好 |
| **uView Pro** | - | 原生重写 | ✅ | 新框架 | - | 早期 | 一般 | - |

### 2.2 是否换库的分析

**换库成本**（以迁移到 uView Plus 为例）：
- 替换 easycom 配置（`pages.json` 第 212–217 行）
- 全局替换 `wd-` 前缀组件（wd-button/wd-card/wd-tag/wd-icon/wd-input 等数十处）
- PcBottomNav、所有 wd-card 列表、wd-tag 徽章、wd-button CTA 全部重写
- 主题变量（`--pc-user-*` 与 wot 主题变量耦合）重新对齐
- 风险：uView Plus 是 JS 编写，丢失 wot 的 TS 类型推导；图标风格变化引发新的不一致

**换库收益**：
- uView Plus 组件更多（180+ vs 80），但本项目用到的组件 wot 基本都有
- uView 自带宠物图标？**否**——uView 的 uicon 同样无宠物服务图标（同样是通用图标集）

**结论**：**不换库**。

理由：
1. wot-design-uni 已深度集成，换库是纯成本无收益（uView 也没有宠物图标）。
2. wot 是 TS 原生，匹配项目 vue-tsc typecheck 流程。
3. wot 维护活跃（2026-01 仍发版），文档质量高。
4. **真正痛点是图标不是组件库**——wot 组件本身没问题，问题在图标资源。

---

## 3. 图标库方案对比

### 3.1 方案对比表

| 方案 | 小程序兼容 | 视觉质量 | 宠物图标覆盖 | 接入成本 | 包体积 | 跨端一致性 | 维护 |
|---|---|---|---|---|---|---|---|
| **① iconfont 字体图标** ⭐推荐 | ✅ base64/CDN | 优（专业设计师上传） | ✅ 海量（搜"宠物"数千结果） | 低（1 文件 + 1 css） | 小（15 图标 ~10KB） | ✅ 完全一致 | iconfont.cn 在线管理 |
| ② wot 内置图标 | ✅ 已验证 | 良 | ❌ 无宠物图标 | 零（已用） | 已含 | ✅ | 随库更新 |
| ③ @iconify/vue | ❌ 不兼容 | 极优（200k 图标） | ✅ | 高（需 UnoCSS 预编译或 lime-icon 插件） | 中 | 需重构 | 依赖构建链 |
| ④ UnoCSS + Iconify 预生成 | ✅ 构建期转 SVG | 极优 | ✅ | 高（引入 UnoCSS 全套） | 小（按需） | ✅ | 中 |
| ⑤ uni-icons（官方） | ✅ | 中（约 120 通用图标） | ❌ 无宠物图标 | 低 | 小 | ✅ | 随 uni-ui |
| ⑥ 纯 emoji | ✅ | 差（跨端不一致） | 部分（🐾🏠） | 零 | 零 | ❌ 差 | 无 |
| ⑦ Lottie/PNG/SVG base64 | ✅ | 优 | 自定义 | 高（逐个制作） | 大 | ✅ | 高 |
| ⑧ lime-icon 插件（Iconify 封装） | ✅ | 极优 | ✅ | 中（装插件） | 中 | ✅ | 依赖插件 |

### 3.2 关键技术验证：iconfont 在小程序的可行性

**这是最重要的验证点**，因为 PcServiceIcon 当初用 emoji 兜底就是"以为小程序不支持字体图标"。实际验证：

#### wot-design-uni 自己就在小程序上用字体图标
查看 `node_modules/wot-design-uni/components/wd-icon/index.scss`：
```scss
@font-face {
  font-family: 'wd-icons';
  src: url('https://at.alicdn.com/t/c/font_4245058_s5cpwl25n7o.woff2?t=...') format('woff2'),
    url('https://at.alicdn.com/t/c/font_4245058_s5cpwl25n7o.woff?t=...') format('woff'),
    url('https://at.alicdn.com/t/c/font_4245058_s5cpwl25n7o.ttf?t=...') format('truetype');
}
/* #ifdef APP-PLUS || H5 */
@font-face { src: url('./wd-icons.ttf') format('truetype'); }
/* #endif */
```
**结论**：wot 在小程序端用 **阿里 CDN 网络字体**（at.alicdn.com，正是 iconfont 的 CDN），在 H5/App 端用本地 ttf。这就是 iconfont 的标准用法——**已经在生产环境跑通**。

#### 小程序 @font-face 的两条铁律（官方文档 + 社区共识）
1. **小程序不支持 CSS 引用本地字体文件路径**（`url('./xx.ttf')` 在小程序无效）。
2. **支持两种替代**：
   - **base64 内联**：`src: url('data:font/ttf;base64,xxxx...')` —— 字体文件 < 40KB 时首选，完全离线。
   - **网络字体**：`src: url('https://...woff2')` —— 必须 https，需联网。

**体积参考**：wot 的 wd-icons.ttf 含 295 个图标 = **53KB**（略超 40KB，所以 wot 小程序端走 CDN）。我们只需 15–25 个宠物/功能图标，生成的 ttf 约 **8–15KB**，**完全可以用 base64 内联**，离线可用、无 CDN 依赖。

### 3.3 推荐方案：iconfont 字体图标（方案①）

**为什么选 iconfont 而非 Iconify/UnoCSS**：
1. **接入成本最低**：不引入新构建工具（UnoCSS 需重配 vite）、不引入新运行时依赖（@iconify/vue）。
2. **wot 已验证同款机制**：wot 用的就是 iconfont 的 at.alicdn.com CDN，技术栈一致。
3. **宠物图标海量**：iconfont.cn 搜"宠物"/"pet"/"爪子"/"洗澡"有数千个专业图标，可建项目挑选。
4. **在线管理**：后续加图标只需在 iconfont.cn 项目里加，重新下载字体即可，不改代码结构。
5. **匹配毕业设计 demo 规模**：不需要 Iconify 20 万图标的体量，15–25 个精选图标够用。

**与 wd-icon 的关系**：
- wd-icon 的 `classPrefix` prop（默认 `wd-icon`）是官方扩展点，支持自定义图标字体（见 `types.ts`）。
- 两种接入风格：
  - **风格 A（推荐）**：新建 `PcIcon.vue` 组件，内部用 `<view class="pc-icon pc-icon-{{name}}">`，独立于 wd-icon，避免与 wot 内置图标 class 冲突。
  - **风格 B**：复用 wd-icon，传 `class-prefix="pc-icon"`，name 传自定义图标名。代码改动更小但耦合 wot。

---

## 4. 图标清单与映射表

### 4.1 服务图标（PcServiceIcon 替换）—— 必须改

| 当前 | 当前渲染 | 推荐替代 | iconfont 搜索词 | 优先级 |
|---|---|---|---|---|
| `bath`（洗护） | SVG 浴缸 / 🛁 | iconfont 洗浴/浴缸图标 | `洗澡` `浴缸` `bath` `pet bath` | P0 |
| `groom`（美容） | SVG 剪刀 / ✂️ | iconfont 剪刀/美容图标 | `剪刀` `美容` `grooming` `scissor` | P0 |
| `home`（上门照护） | SVG 房子 / 🏠 | iconfont 房子+爪印 或 上门服务图标 | `上门` `home service` `房子 爪` | P0 |
| `foster`（寄养） | SVG 心形+爪 / 🐾 | iconfont 爪印/爱心宠物图标 | `爪子` `paw` `寄养` `pet care` | P0 |

**配套 emoji 替换**（与上面同主题）：
| 当前 emoji | 位置 | 替换为 |
|---|---|---|
| 🐾（services 空状态） | `pages/services/index.vue:48` | iconfont 爪印（与服务图标复用） |

### 4.2 功能入口 emoji —— 建议改

| 当前 | 位置 | 推荐替代 | iconfont 搜索词 |
|---|---|---|---|
| 📢（公告） | `pages/home/index.vue:45` | iconfont 喇叭/公告图标 | `公告` `喇叭` `notification` `megaphone` |
| 🛒（购物车 FAB） | `pages/products/index.vue:72` | iconfont 购物车（或复用 wd-icon `cart`） | `购物车` `cart`（**可直接用 wd-icon cart，无需 iconfont**） |
| ✨（新对话） | `pages/ai/chat.vue:24` | iconfont 闪光/AI 图标 | `闪光` `sparkle` `AI` `magic` |
| 🔒（登录锁） | `pages/ai/chat.vue:31` | iconfont 锁图标（或复用 wd-icon `lock-on`） | `锁` `lock`（**可直接用 wd-icon lock-on**） |

### 4.3 空状态/装饰 emoji —— 可选改

这些是 `PcStatePanel` 的 `empty-icon` prop（接受任意字符串渲染为 emoji）。替换需改 PcStatePanel 支持图标组件，工作量较大，**建议保留 emoji 或后续迭代**：

| emoji | 用途 | 是否改 |
|---|---|---|
| 🛍️ | 商品空状态 | 可保留（或换 iconfont 购物袋） |
| 🎉 | 活动空状态 | 可保留 |
| 💬 | 社区空状态 | 可保留 |
| 📅 | 预约空状态 | 可保留 |
| 🧾 | 订单空状态 | 可保留 |
| 🔔 | 通知空状态 | 可保留 |
| ❤️🤍 | 点赞 | 可保留（或换 wd-icon heart/heart-filled，已在用） |
| ⭐☆ | 收藏 | 可保留（或换 wd-icon star/star-filled） |

### 4.4 tabBar PNG —— 不改

| tab | PNG 现状 | 处理 |
|---|---|---|
| home/booking/community/product/profile | 10 张 PNG，质量可接受 | **保留不动**（小程序 tabBar 仅支持图片，H5 用 PcBottomNav 的 wd-icon） |

### 4.5 wd-icon 用法 —— 不改

| 图标 | 用途 | 处理 |
|---|---|---|
| home/calendar/chat/cart/user（PcBottomNav） | 底部导航 | **保留**（wot 内置，质量良好） |
| check-outline/lock/heart/chat/check/arrow-right | 各处功能图标 | **保留** |

---

## 5. 接入步骤（iconfont 方案落地）

### 步骤 1：在 iconfont.cn 建立项目并选图标

1. 访问 https://www.iconfont.cn/（需登录，可用阿里/微信/GitHub 账号）
2. 创建项目：命名 `petcare-miniapp`
3. 搜索并添加图标到项目（建议先选 15 个，覆盖 P0+P1）：
   - 洗护：搜 `洗澡` / `bath` → 选 1 个浴缸或宠物洗澡图标
   - 美容：搜 `剪刀` / `scissor` → 选 1 个剪刀图标
   - 上门：搜 `上门服务` / `home` → 选 1 个房子/定位图标
   - 寄养：搜 `爪子` / `paw` → 选 1 个爪印图标
   - 公告：搜 `喇叭` / `megaphone` → 选 1 个
   - AI/闪光：搜 `sparkle` / `AI` → 选 1 个
   - 备用：宠物/骨头/疫苗/医疗 等 5–8 个扩展图标
4. 在项目设置里：
   - **FontClass/Symbol 前缀**：设为 `pc-icon-`（避免与 wd-icon 冲突）
   - **Font Family**：设为 `pc-icons`

### 步骤 2：下载字体并转 base64

1. 在 iconfont 项目页点"下载至本地"，解压得到 `iconfont.ttf` / `iconfont.css` 等
2. 检查 `iconfont.ttf` 大小：
   - **若 < 40KB**（15 个图标通常 ~10KB）：用 https://transfonter.org/ 把 ttf 转 base64（选 base64 encode + ttf 格式）
   - **若 > 40KB**：直接上传 ttf 到任意 HTTPS 静态资源（或 iconfont 自带的 at.alicdn.com 项目 CDN 链接），用网络字体

### 步骤 3：在项目里放置字体资源

在 `frontend/miniapp/src/` 下新建：
```
src/
  static/
    fonts/
      pc-icons.css      # @font-face + .pc-icon-xxx::before 规则
      pc-icons.ttf      # （仅 H5/App 用，>40KB 时不用）
  components/
    PcIcon.vue          # 新图标组件（替代 PcServiceIcon 的角色）
```

`pc-icons.css` 内容（base64 方案示例）：
```css
@font-face {
  font-family: 'pc-icons';
  src: url('data:font/ttf;base64,AAEAAAARAQAA....') format('truetype');
  font-weight: normal;
  font-style: normal;
}
.pc-icon { font-family: 'pc-icons' !important; ... }
.pc-icon-bath:before { content: "\e001"; }
.pc-icon-groom:before { content: "\e002"; }
/* ... */
```

### 步骤 4：全局引入字体 CSS

在 `frontend/miniapp/src/App.vue` 的 `<style>` 里（或 `main.ts`）全局 import：
```ts
import '@/static/fonts/pc-icons.css'
```
> 注意：必须在全局引入一次，不能 scoped，否则 ::before 不生效。

### 步骤 5：封装 PcIcon 组件（替代 PcServiceIcon）

新建 `frontend/miniapp/src/components/PcIcon.vue`：
```vue
<template>
  <view class="pc-icon" :class="`pc-icon-${name}`" :style="rootStyle" />
</template>
<script setup lang="ts">
import { computed } from 'vue'
const props = defineProps<{
  name: string
  size?: string | number   // '20px' | 20
  color?: string
}>()
const rootStyle = computed(() => ({
  fontSize: props.size ? (typeof props.size === 'number' ? `${props.size}px` : props.size) : undefined,
  color: props.color,
}))
</script>
<style scoped>
.pc-icon { display: inline-block; line-height: 1; }
</style>
```

> 或更省事：直接复用 `wd-icon`，传 `class-prefix="pc-icon"`：
> ```vue
> <wd-icon class-prefix="pc-icon" name="bath" size="26px" :color="item.color" />
> ```
> 这样连新组件都不用建（wd-icon 的 `classPrefix` prop 官方支持自定义图标，见 types.ts）。

### 步骤 6：替换 PcServiceIcon 调用点

`pages/home/index.vue` 第 70 行：
```vue
<!-- 改前 -->
<PcServiceIcon :name="item.icon" :color="item.color" />
<!-- 改后（方案 B：复用 wd-icon）-->
<wd-icon class-prefix="pc-icon" :name="item.icon" size="26px" :color="item.color" />
```

`serviceShortcuts` 数据（第 240 行）的 `icon` 字段值改为 iconfont 图标名（bath/groom/home/foster 对应 iconfont 项目里的 class 名）。

### 步骤 7：删除 PcServiceIcon.vue

确认无其他引用后（grep 已验证仅 home/index.vue 用），删除该文件。

### 步骤 8：替换功能入口 emoji（可选，P1）

- `pages/home/index.vue:45` `📢` → `<wd-icon class-prefix="pc-icon" name="notification" .../>` 或 `<wd-icon name="notification" .../>`（wot 内置有 notification）
- `pages/products/index.vue:72` `🛒` → `<wd-icon name="cart" .../>`（wot 内置）
- `pages/ai/chat.vue:24` `✨` → `<wd-icon class-prefix="pc-icon" name="sparkle" .../>`
- `pages/ai/chat.vue:31` `🔒` → `<wd-icon name="lock-on" .../>`（wot 内置）

---

## 6. 风险与工作量评估

### 6.1 风险

| 风险 | 等级 | 说明 | 缓解 |
|---|---|---|---|
| iconfont base64 > 40KB | 低 | 15 个图标通常 ~10KB | 控制图标数量；超限时切 CDN 网络字体（同 wot 做法） |
| 小程序字体首次加载闪烁 | 低 | 字体加载有微小延迟 | 用 base64 内联则无此问题；CDN 方案可接受（wot 也这样） |
| iconfont.cn 图标版权 | 低 | 部分图标有商用限制 | 选"免费可商用"筛选；毕业设计 demo 风险极低 |
| 与 wot 图标风格不一致 | 中 | iconfont 图标线条粗细可能与 wot 的 295 图标不统一 | 服务图标在彩色圆背景内（独立视觉单元），不与 wot 图标并排显示，影响小；选图标时尽量挑线性风格 |
| PcStatePanel empty-icon 改造 | 中 | empty-icon 是字符串 prop，换图标组件需改组件签名 | 本轮不改空状态 emoji，保留现状 |
| 删除 PcServiceIcon 破坏 mp-weixin-ui-contract 测试 | 中 | 有测试断言 `🛒` 等存在（见 `__tests__/utils/mp-weixin-ui-contract.test.ts:203`） | 替换后同步更新测试断言 |

### 6.2 工作量估算

| 任务 | 估时 | 优先级 |
|---|---|---|
| iconfont.cn 建项目选 15 个图标 | 1h | P0 |
| 下载字体 + 转 base64 + 放置资源 | 0.5h | P0 |
| 封装 PcIcon 或 wd-icon classPrefix 接入 | 0.5h | P0 |
| 替换 home/index.vue 4 个服务图标 + 删除 PcServiceIcon | 0.5h | P0 |
| 替换 4 个功能入口 emoji | 0.5h | P1 |
| 更新 mp-weixin-ui-contract 测试 | 0.5h | P0 |
| H5 + 小程序双端视觉验证 | 1h | P0 |
| **合计** | **~4.5h（0.5–1 天）** | |

### 6.3 不建议做的事

1. **不要引入 UnoCSS / @iconify/vue** —— 为了图标引入整套构建链，过度工程，不符合 demo 定位。
2. **不要换 UI 库** —— wot 已深度集成，换库成本高、收益零（uView 也没宠物图标）。
3. **不要改 tabBar PNG** —— 小程序限制，图片是唯一选择，当前 PNG 可用。
4. **不要急着删所有 emoji** —— 空状态 emoji（🛍️🎉💬）是 PcStatePanel 的字符串 prop，改造工作量不成比例，优先级低。
5. **不要用 lime-icon 等 Iconify 封装插件** —— 多一个三方依赖，iconfont 已够用。

---

## 7. 附录：关键文件路径

| 文件 | 作用 |
|---|---|
| `frontend/miniapp/src/components/PcServiceIcon.vue` | 自绘 SVG 图标（待删除） |
| `frontend/miniapp/src/components/PcBottomNav.vue` | wd-icon 底部导航（H5） |
| `frontend/miniapp/src/pages/home/index.vue` | 服务快捷入口 + 公告 emoji |
| `frontend/miniapp/src/pages.json` | tabBar PNG 配置（第 164–204 行） |
| `frontend/miniapp/src/static/icons/` | 10 张 tabBar PNG |
| `frontend/miniapp/node_modules/wot-design-uni/components/wd-icon/` | wd-icon 源码（验证 classPrefix 扩展点 + at.alicdn.com CDN 用法） |
| `frontend/miniapp/node_modules/wot-design-uni/components/wd-icon/index.scss` | wot 图标 @font-face 实现（第 4–22 行） |
| `frontend/miniapp/src/__tests__/utils/mp-weixin-ui-contract.test.ts` | UI 契约测试（第 203 行断言 emoji，替换后需更新） |

## 8. 参考资料

- [wot-design-uni DCloud 插件页](https://ext.dcloud.net.cn/plugin?id=13889)
- [wot-design-uni 官方文档（Wot UI）](https://v1.wot-ui.cn/)
- [uni-icons 官方文档（iconfont 接入说明）](https://uniapp.dcloud.net.cn/component/uniui/uni-icons.html)
- [uni-app 页面样式与布局（@font-face 支持说明）](https://uniapp.dcloud.net.cn/tutorial/syntax-css.html)
- [uni-app loadFontFace API（小程序仅支持网络字体）](https://en.uniapp.dcloud.io/api/ui/font.html)
- [iconfont.cn 阿里巴巴矢量图标库](https://www.iconfont.cn/)
- [uView Plus 官网](https://uview-plus.jiangruyi.com/)
- [unibest UI 库选型（社区对比）](https://unibest.tech/base/ui/ui)
- [uni-app 小程序使用 iconfont 字体图标教程（掘金）](https://juejin.cn/post/7417729014813425715)
- [uni-app 引入 base64 字体图标（掘金）](https://juejin.cn/post/7134326088943009823)
- [微信小程序离线引入 iconfont（阿里云）](https://developer.aliyun.com/article/1262403)
