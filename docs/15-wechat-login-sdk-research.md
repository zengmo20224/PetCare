# 微信小程序登录技术调研（SDK / API / 个人开发者限制）

> 日期：2026-08-01 ｜ 状态：**纯调研文档，不含代码改动** ｜ 关联：`docs/12-wechat-miniprogram-launch-plan.md`、`docs/08-pending-decisions.md` P-002、后端 `WechatLoginProvider` 三态实现
>
> **目的**：把微信小程序登录的完整技术方案一次说透——"即使个人开发者无法上线实现，也要知道完整流程"。涵盖 2026 年最新规则（2022 用户信息调整、2023 手机号收费、2024+ 现状）。

---

## 0. TL;DR（一张表读完）

| 问题 | 结论 |
|---|---|
| 微信登录核心接口 | 前端 `wx.login()` → code → 后端 `jscode2session` 换 openid/session_key |
| `jscode2session` 是否需要 access_token | **不需要**（用 appid+secret 直接鉴权）；`phonenumber.getPhoneNumber` 才需要 access_token |
| 用户头像昵称怎么拿 | **`wx.getUserProfile` 已废弃**（2022-10-25 起返回匿名数据）。当前唯一正确方式：`<button open-type="chooseAvatar">` + `<input type="nickname">` |
| 手机号怎么拿 | 新方案：`<button open-type="getPhoneNumber">` 拿动态 code → 后端调 `phonenumber.getPhoneNumber`。**2023-08-28 起收费 0.03 元/次** |
| 旧版 encryptedData+iv 解密还能用吗 | 仍可用但官方明确"建议升级"，**新项目直接用 code 方案** |
| 个人开发者能微信登录吗 | ✅ 能（`wx.login` + `jscode2session` 个人可用） |
| 个人开发者能拿手机号吗 | ❌ **不能**。`getPhoneNumber` 仅"非个人主体 + 已认证"小程序可用 |
| 个人开发者能用微信支付吗 | ❌ **不能**。需企业/个体工商户主体 + 商户号 |
| 个人开发者能做宠物/电商/社交类目吗 | ❌ **不能**。宠物、电商、社交类目均不对个人开放 |
| 项目现有实现 | `RealWechatLoginProvider` 已正确实现 jscode2session 调用；缺手机号获取、用户信息获取、session_key 缓存、access_token 获取 |
| 从 demo 到上线还差什么 | **资质是硬阻断**（营业执照 + 小程序认证 + 域名备案 ≈ 3-6 周），技术只差 1-2 周 |

---

## 1. 微信登录完整流程（核心时序）

### 1.1 文字版时序图

```
┌──────────┐    ┌──────────────┐    ┌──────────────┐    ┌──────────┐
│ 小程序前端 │    │ 开发者后端    │    │  微信服务器   │    │ 业务 DB  │
│(uni.login)│    │(Spring Boot) │    │(api.weixin.  │    │ (MySQL)  │
│           │    │              │    │ qq.com)      │    │          │
└─────┬─────┘    └──────┬───────┘    └──────┬───────┘    └────┬─────┘
      │                 │                   │                  │
      │ 1. wx.login()    │                   │                  │
      │ / uni.login      │                   │                  │
      │ {provider:weixin}│                   │                  │
      │                 │                   │                  │
      │ 2. 拿到临时 code │                   │                  │
      │   (有效期 5 分钟, │                   │                  │
      │    一次性使用)   │                   │                  │
      │                 │                   │                  │
      │ 3. POST /api/v1  │                   │                  │
      │   /auth/wechat   │                   │                  │
      │   { code }       │                   │                  │
      ├────────────────▶│                   │                  │
      │                 │ 4. GET jscode2session                 │
      │                 │   ?appid=&secret=                     │
      │                 │   &js_code=&grant_type=               │
      │                 │     authorization_code                │
      │                 ├──────────────────▶│                  │
      │                 │                   │                  │
      │                 │ 5. 返回 JSON:      │                  │
      │                 │   { openid,        │                  │
      │                 │     session_key,   │                  │
      │                 │     unionid?(可选)} │                  │
      │                 │◀──────────────────┤                  │
      │                 │                   │                  │
      │                 │ 6. 按 openid 查 user 表               │
      │                 │   - 不存在 → 建账号 (status=ACTIVE)   │
      │                 │   - 存在但非 ACTIVE → 拒绝登录        │
      │                 ├──────────────────────────────────────▶│
      │                 │◀──────────────────────────────────────┤
      │                 │                   │                  │
      │                 │ 7. JwtTokenService.signUserToken(uid) │
      │                 │    生成 USER 角色 JWT                  │
      │                 │                   │                  │
      │ 8. 返回 {        │                   │                  │
      │   tokenType,    │                   │                  │
      │   accessToken,  │                   │                  │
      │   expiresInSeconds,                 │                  │
      │   user:{id,nickname}}               │                  │
      │◀────────────────┤                   │                  │
      │                 │                   │                  │
      │ 9. 存 token 到   │                   │                  │
      │   uni.storage    │                   │                  │
      │   后续请求带     │                   │                  │
      │   Authorization: │                   │                  │
      │   Bearer xxx     │                   │                  │
      │                 │                   │                  │
```

**关键点**：
- **code 是临时凭证**：有效期 **5 分钟**，**只能消费一次**（重复用会报 `40029 invalid code`）。
- **jscode2session 不需要 access_token**：用 appid + secret 作为请求参数鉴权，这是微信登录体系里唯一例外。
- **session_key 绝不下发前端**：仅后端用于解密敏感数据（如旧版手机号 encryptedData）。项目用 JWT，不需要把 session_key 给前端。
- **unionid 是可选的**：只有当小程序绑定了微信开放平台账号时才会返回，用于跨应用（公众号/小程序/APP）识别同一用户。本项目单小程序场景，可不用。

### 1.2 项目现有实现对照

项目已实现的链路（见 `WechatLoginApplicationService.login()`）：
1. ✅ 接收前端 code
2. ✅ 调 `WechatLoginProvider.login(code)` 换 openid（real 模式调 jscode2session）
3. ✅ 按 openid 查 `user` 表，不存在则建账号（默认昵称"微信用户"+openid 后 4 位）
4. ✅ 封禁用户拒绝登录（`USER_BANNED`）
5. ✅ 签 JWT 返回 token（与密码登录返回结构完全一致）

**未实现的环节**（详见 §7、§8）：
- ❌ 手机号获取（getPhoneNumber）
- ❌ 用户头像昵称获取（chooseAvatar / nickname input）
- ❌ session_key 缓存（如果将来要解密旧版 encryptedData 才需要）
- ❌ access_token 获取与缓存（手机号接口才需要）

---

## 2. API 清单（按调用顺序）

### 2.1 前端 API（小程序 / uni-app）

| API | 用途 | 关键字段 | 备注 |
|---|---|---|---|
| `wx.login()` / `uni.login({provider:'weixin'})` | 获取临时登录 code | `res.code`（5 分钟有效，一次性） | 项目已用：`login.vue:96` |
| `wx.getUserProfile()` | ~~获取用户头像昵称~~ | — | **已废弃**（2022-10-25 起返回匿名数据），不要用 |
| `<button open-type="chooseAvatar">` + `bindchooseavatar` | 让用户选头像 | `e.detail.avatarUrl`（临时文件路径，需上传到自己服务器） | 2026 现状正确方式 |
| `<input type="nickname">` | 让用户填昵称 | `value`（用户微信昵称或自定义） | 2026 现状正确方式 |
| `<button open-type="getPhoneNumber">` + `bindgetphonenumber` | 获取手机号动态 code | `e.detail.code`（5 分钟有效，一次性，**与 wx.login 的 code 不同**） | **个人主体不可用** |
| `wx.request(...)` | 调自己后端 | — | 域名必须在白名单 |

### 2.2 后端 API（开发者服务器 → 微信服务器）

| 接口 | 方法 / URL | 是否需 access_token | 用途 |
|---|---|---|---|
| **jscode2session**（auth.code2Session） | `GET https://api.weixin.qq.com/sns/jscode2session` | **否**（用 appid+secret） | code 换 openid/session_key/unionid |
| **phonenumber.getPhoneNumber** | `POST https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=TOKEN` | **是** | 手机号动态 code 换明文手机号 |
| **auth.getAccessToken** | `GET https://api.weixin.qq.com/cgi-bin/token` | — | 获取 access_token（7200 秒，需缓存） |
| **getStableAccessToken** | `POST https://api.weixin.qq.com/cgi-bin/stable_token` | — | 稳定版 access_token（推荐，与 getAccessToken 互隔离） |

#### 2.2.1 jscode2session 详解（项目已实现）

**请求**：
```
GET https://api.weixin.qq.com/sns/jscode2session
    ?appid=APPID
    &secret=SECRET
    &js_code=CODE
    &grant_type=authorization_code
```

**成功响应**：
```json
{
  "openid": "oZbsdJIxxxxxx",
  "session_key": "HyVFkGl5F5OQYJZ6Kxxxxxx",
  "unionid": "o6_bmasdasMxxxxxx",   // 可选，仅绑定开放平台时返回
  "scope": "..."
}
```

**失败响应**（errcode ≠ 0）：
```json
{ "errcode": 40029, "errmsg": "invalid code, ..." }
```

**错误码清单**：

| errcode | 含义 | 排查方向 |
|---|---|---|
| `0` | 成功 | — |
| `-1` | 系统繁忙 | 微信侧临时故障，做指数退避重试 |
| `40029` | invalid code（最常见） | code 重复使用 / 已过期(>5分钟) / appid+secret 与小程序不匹配 / 开发版与正式版 appid 不一致 |
| `45011` | 频率限制 / 资源未授权 | 短期调用过频（多次刷 wx.login）；或第三方平台未获授权 |
| `40226` | 高风险用户 / 缺少跨平台配置 | 用户被风控；或小程序未绑定开放平台导致拿不到 unionid |

> 项目 `RealWechatLoginProvider` 已正确处理：把所有 errcode≠0 收敛成 `WECHAT_JS_CODE_INVALID`，把网络/HTTP 错误收敛成 `WECHAT_UNAVAILABLE`，**绝不外泄 errmsg**（合规要求）。

#### 2.2.2 phonenumber.getPhoneNumber 详解（项目未实现）

**请求**：
```
POST https://api.weixin.qq.com/wxa/business/getuserphonenumber
     ?access_token=ACCESS_TOKEN
Content-Type: application/json

{ "code": "动态code（来自前端 getPhoneNumber 回调）" }
```

> 注意：access_token 走 query 参数，code 走 JSON body。

**成功响应**：
```json
{
  "errcode": 0,
  "errmsg": "ok",
  "phone_info": {
    "phoneNumber": "8613800138000",      // 带区号
    "purePhoneNumber": "13800138000",     // 纯手机号（业务用这个）
    "countryCode": "86",
    "watermark": {
      "appid": "你的小程序appid",
      "timestamp": 1637744274
    }
  }
}
```

**前置条件**（缺一不可）：
1. 小程序主体为**非个人**（企业/个体工商户/政府等）
2. 已完成**微信认证**（300 元/年）
3. 已在小程序后台**申请 getPhoneNumber 接口权限**
4. 已**购买接口额度**（1000 次免费，超出 0.03 元/次）
5. 后端能拿到**有效 access_token**（需先调 `cgi-bin/token` 或 `stable_token`，并缓存 7200 秒）

---

## 3. 用户信息（头像昵称）获取方案 —— 2026 现状

### 3.1 历史演变

| 时间 | API | 状态 |
|---|---|---|
| 早期 | `wx.getUserInfo()` | ✅ 自动弹窗授权，返回真实数据 |
| 2021-04 | `wx.getUserProfile()` | ✅ 需用户主动触发按钮，返回真实数据（替代 getUserInfo） |
| **2022-10-25 起** | `wx.getUserProfile()` / `wx.getUserInfo()` | ❌ **被收回**。新版本小程序调用只会返回**默认灰色头像 + "微信用户"匿名昵称** |

### 3.2 当前（2026）正确方式：头像昵称填写能力

不再由开发者"主动获取"，而是由用户"主动填写"——微信提供两个专用组件：

**头像**：用 `<button open-type="chooseAvatar">`，监听 `bindchooseavatar` 事件
```html
<button open-type="chooseAvatar" @bindchooseavatar="onChooseAvatar">
  <image src="{{ avatarUrl }}" />
</button>
```
```js
// uni-app 写法
function onChooseAvatar(e) {
  const tempPath = e.detail.avatarUrl  // 临时文件路径（wxfile://...）
  // 必须上传到自己服务器/对象存储，否则小程序重启后丢失
  uni.uploadFile({ url: '后端上传接口', filePath: tempPath, name: 'file' })
}
```

**昵称**：用 `<input type="nickname">`，用户点击会弹出微信昵称快捷填充键盘
```html
<input type="nickname" v-model="nickname" placeholder="请输入昵称" />
```

### 3.3 对项目的影响

项目当前 `WechatLoginApplicationService` 建账号时用默认昵称"微信用户"+openid 后 4 位，**这是合理的过渡方案**：
- 首次登录快速完成（不阻塞用户）
- 用户后续在"个人资料"页用 chooseAvatar + nickname input 自行完善
- 后端需要新增一个 `PUT /api/v1/user/profile`（头像 + 昵称更新）端点 —— 项目 `User` 表已有 `avatar`/`nickname` 字段，改动量小

> ⚠️ 不要再引入 `wx.getUserProfile`，会被审核驳回。

---

## 4. 手机号获取方案（新旧对比 + 推荐）

### 4.1 两种方案对比

| 维度 | 旧方案（encryptedData + iv） | 新方案（code → phonenumber.getPhoneNumber） |
|---|---|---|
| 前端 | `getPhoneNumber` 回调拿 `encryptedData` + `iv` | `getPhoneNumber` 回调拿 `code` |
| 后端 | 用 `session_key` + `iv` 做 **AES-128-CBC 解密** | 调 `phonenumber.getPhoneNumber` HTTP 接口换明文 |
| 依赖 | 需先 `jscode2session` 拿到 session_key 并缓存 | 需先拿 access_token（cgi-bin/token）并缓存 |
| 是否需 wx.login | **必须**先调 wx.login 拿 session_key | **不必**（code 来自 getPhoneNumber 回调，独立） |
| 安全性 | session_key 下发/缓存有泄露风险 | 无 session_key 传递，更安全 |
| 官方态度 | 仍可用，但明确"建议升级" | **推荐** |
| 收费 | 免费 | **0.03 元/次**（2023-08-28 起，1000 次免费额度） |
| 主体要求 | 非个人 + 已认证 | 非个人 + 已认证 |

### 4.2 推荐方案

**新项目直接用新方案（code → phonenumber.getPhoneNumber）**，理由：
1. 官方推荐，长期可维护
2. 不需要在后端维护 session_key 缓存（少一个状态）
3. 不需要写 AES 解密代码（少一个安全风险点）
4. 0.03 元/次的成本可控，且有 1000 次免费额度（demo 阶段够用）

**项目当前策略**（P-002 决定的 demo 形态）：**不实现手机号获取**。微信登录建账号时直接用 openid 作唯一标识，用户用密码注册的账号（手机号）和微信账号是两套——通过 user 表的 `openid` 字段判别。未来要做"微信账号绑定手机号"时再上 getPhoneNumber。

### 4.3 AES 解密方案（仅供参考，不推荐用于新代码）

如要兼容旧版（基础库 < 2.21.2）：
- 算法：AES-128-CBC，PKCS#7 填充
- key = `session_key`
- iv = 前端回调的 `iv`
- 解密后 JSON 含 `phoneNumber` / `purePhoneNumber` / `watermark.appid`（需校验 appid 防伪造）
- Java 实现：`javax.crypto.Cipher` + `SecretKeySpec` + `IvParameterSpec`

---

## 5. 个人开发者限制清单（重点）

### 5.1 能力对照表

| 能力 | 个人主体 | 非个人主体（企业/个体工商户等） | 备注 |
|---|---|---|---|
| 注册小程序账号 | ✅ | ✅ | — |
| `wx.login` 获取 code | ✅ | ✅ | — |
| `jscode2session` 换 openid | ✅ | ✅ | 个人也能完成"微信登录"主流程 |
| 获取 unionid | ⚠️ 需绑定开放平台（个人也可注册开放平台） | ✅ | — |
| `<button open-type="chooseAvatar">` 选头像 | ✅ | ✅ | — |
| `<input type="nickname">` 填昵称 | ✅ | ✅ | — |
| **getPhoneNumber（手机号）** | ❌ | ✅（需认证 + 付费） | 官方明确"针对非个人主体" |
| **微信支付** | ❌ | ✅（需商户号） | 个人主体无法申请商户号 |
| 卡券接口 | ❌ | ✅ | — |
| 附近的小程序 | ❌ | ✅ | — |
| 微信认证 | ❌（个人主体不支持认证） | ✅（300 元/年） | **这是根因**——认证是 getPhoneNumber/支付的前置 |
| 客服消息（`button open-type="contact"`） | ⚠️ 受限 | ✅ | — |

### 5.2 类目限制（个人主体可选）

**个人主体不能选的类目**（与本项目强相关）：
- ❌ **电商 / 商家自营**（商品零售模块） → 需营业执照
- ❌ **宠物 > 宠物食品 / 宠物医疗**（活体交易、医疗需资质） → 需食品经营许可证 / 动物诊疗许可证
- ❌ **社交 / 社区**（UGC 类） → 个人主体不允许，需企业 + ICP 备案 + 内容审核机制

**个人主体能选的类目**（与本项目关系不大）：
- ✅ 工具类（天气、计算器、转换器）
- ✅ 出行与交通（公交查询）
- ✅ 文娱（资讯、阅读，部分）
- ✅ 生活服务（快递查询，不涉及交易）

**结论**：**本项目（宠物服务预约 + 商品零售 + 社区）完全无法用个人主体上架**。任何一条业务线都踩在个人主体的禁区上。

### 5.3 测试号 vs 正式号

| 维度 | 测试号（沙盒号） | 正式号 |
|---|---|---|
| 申请方式 | 微信扫码即得（`mp.weixin.qq.com/debug/cgi-bin/sandbox`） | 需注册主体 + 认证 |
| 有效期 | 最长 60 天 | 长期 |
| `wx.login` / jscode2session | ✅ 可用 | ✅ |
| getPhoneNumber | ❌ 无法真正调通付费链路 | ✅（需购买额度） |
| 微信支付 | ❌ | ✅（需商户号 + 沙盒白名单调试） |
| 域名白名单 | ⚠️ 测试号可在开发者工具勾选"不校验域名"绕过 | 强制 HTTPS + 备案 + 白名单 |
| 上架发布 | ❌ 不可发布 | ✅ |

**对本项目的意义**：当前 demo 阶段（P-002 "非上架 demo"）用开发者工具 + 体验版 + 测试号/正式号 appid 都能跑通"前端 wx.login → 后端 jscode2session → 建 openid 账号 → JWT"。**真要上架，必须走正式号 + 企业主体 + 认证**。

---

## 6. 所需 SDK / 依赖清单

### 6.1 前端（uni-app 小程序）

| 项 | 是否需要 | 说明 |
|---|---|---|
| `wx.login` / `uni.login` | ✅ 已用 | 小程序原生 API，uni-app 已封装，**无需额外 SDK** |
| `button open-type` | ✅ 已用 | 原生组件 |
| 任何 npm 包 | ❌ | 微信登录不依赖前端第三方库 |

**前端零依赖**——这是微信登录最简洁的地方。

### 6.2 后端（Spring Boot）

| 项 | 是否需要 | 说明 |
|---|---|---|
| **Spring RestClient** | ✅ 已用 | 项目 `RealWechatLoginProvider` 已用它调 jscode2session，**推荐保持** |
| WxJavaSDK / weixin-java-mp | ⚠️ 可选 | 见 §6.3 评估 |
| Jackson（JSON 解析） | ✅ 已用 | 项目已全局引入 |
| AES 解密（旧版手机号方案） | ❌ 不需要 | 项目采用新方案（code），无需 AES |
| HTTP 客户端（access_token 获取） | ✅ 已有 | 复用 RestClient |

### 6.3 是否引入 weixin-java-mp（WxJavaSDK）？

| 维度 | 直接用 RestClient（项目现状） | 引入 weixin-java-mp |
|---|---|---|
| 学习成本 | 0（团队已熟悉） | 需学 SDK API |
| 依赖体积 | 0 | ~2-3 MB（含依赖） |
| 维护成本 | 自己写错误处理/重试 | SDK 已封装 |
| 功能覆盖 | 只覆盖 jscode2session（够用） | 覆盖微信支付/消息/客服/扫码等全套 |
| 与项目风格一致性 | ✅ 与 `DeepSeekAiProviderClient` 一致（直接 RestClient） | 引入新风格 |

**建议**：**保持现状（直接用 RestClient）**。理由：
1. 项目当前微信登录只用到 jscode2session 一个接口，SDK 是杀鸡用牛刀
2. 项目架构（`AiProviderClient` 端口 + RestClient 实现）已成范式，保持一致
3. 未来如要接微信支付，再单独评估引入 SDK（支付签名/回调/退款用 SDK 更稳）

### 6.4 配置项（从哪里拿）

| 配置 | 来源 | 项目现有位置 |
|---|---|---|
| `appid` | 微信公众平台 → 开发 → 开发管理 → 开发设置 | `WechatProperties.appid`（环境变量） |
| `secret` | 同上（AppSecret，只显示一次，需妥善保存） | `WechatProperties.secret`（环境变量） |
| access_token | 后端运行时调 `cgi-bin/token` 获取并缓存 | 项目未实现（手机号接口才需要） |
| 服务器域名白名单 | 微信公众平台 → 开发管理 → 服务器域名 | 需配（见 §6.5） |

### 6.5 微信公众平台需配置的项

| 配置类型 | 用途 | 要求 |
|---|---|---|
| **request 合法域名** | `wx.request` 调后端 API | HTTPS + 备案域名，最多约 200 个 |
| **uploadFile 合法域名** | `wx.uploadFile` 传头像/图片 | 同上 |
| **downloadFile 合法域名** | `wx.downloadFile` 下载图片 | 同上 |
| **socket 合法域名** | WebSocket（项目暂未用） | wss |
| **业务域名** | `<web-view>` 嵌入网页 | 需在校验文件，最多 200 个 |

> 项目当前 `request.ts` 默认连 `http://127.0.0.1:8080`，这是 demo 形态——真机/上架前必须改为已备案的 HTTPS 域名并加入白名单。开发者工具可在"详情 → 本地设置 → 不校验合法域名"勾选绕过（仅开发期）。

---

## 7. 项目现有实现评估

### 7.1 做对了什么 ✅

**`RealWechatLoginProvider.java`（jscode2session 调用）**——实现质量高：
1. ✅ **URL 与参数完全正确**：`https://api.weixin.qq.com/sns/jscode2session` + appid/secret/js_code/grant_type=authorization_code
2. ✅ **不需要 access_token**：直接用 appid+secret，与官方文档一致
3. ✅ **错误处理安全合规**（AGENTS.md §4）：
   - errcode≠0 → `WECHAT_JS_CODE_INVALID`，**绝不外泄 errmsg**
   - 网络/超时/HTTP 错误 → `WECHAT_UNAVAILABLE`
   - 日志只记 errcode 数值，不记 secret
4. ✅ **空值防御**：code 空抛 `WECHAT_JS_CODE_INVALID`，appid/secret 未配抛 `WECHAT_UNAVAILABLE`
5. ✅ **超时可配**：从 `WechatProperties.connectTimeout` / `readTimeout` 读取，有默认值（10s/15s）
6. ✅ **RestClient.Builder 注入**：与 DeepSeek 客户端同模式，便于 MockRestServiceServer 测试

**`WechatLoginApplicationService.java`（建账号 + 签 token）**：
1. ✅ **事务正确**：建账号在 `@Transactional` 内
2. ✅ **openid 唯一约束**：依赖 `user.uk_openid`（schema.sql 已有）
3. ✅ **封禁用户拦截**：非 ACTIVE 直接抛 `USER_BANNED`
4. ✅ **返回结构与密码登录一致**：`tokenType`/`accessToken`/`expiresInSeconds`/`user`
5. ✅ **默认昵称合理**："微信用户"+openid 后 4 位（与微信原生体验一致）

**`WechatLoginConfig.java`（三态开关）**：
1. ✅ **`petcare.wechat.mode`**：disabled/mock/real 三态，`@ConditionalOnProperty` 精准切换
2. ✅ **默认 disabled**：`@ConditionalOnMissingBean` 兜底，未配置时返回 422
3. ✅ **dev 用 mock**：按 code 确定性派生 openid，无需真实 appid/secret

**前端 `login.vue` + `store/user.ts`**：
1. ✅ `uni.login({provider:'weixin'})` 调用正确
2. ✅ code 传给 `doWechatLogin` → 后端换 token
3. ✅ token 存 storage，与密码登录复用同一套
4. ✅ 条件编译 `#ifdef MP-WEIXIN` 隔离，H5 不会出现微信按钮

### 7.2 缺什么 ❌

按"上架完整度"排序：

| # | 缺口 | 影响 | 工作量 |
|---|---|---|---|
| 1 | **手机号获取（getPhoneNumber）** | 微信用户无法绑定手机号；与密码注册体系无法打通 | 2 天（后端 + access_token 缓存 + 前端按钮） |
| 2 | **access_token 获取与缓存** | 手机号接口前置依赖 | 0.5 天（RestClient + 本地缓存，TTL 7000s） |
| 3 | **头像昵称完善页面** | 微信用户进来是"微信用户xxxx"，体验差 | 1 天（前端 chooseAvatar + nickname input + 上传 + PUT 接口） |
| 4 | **session_key 缓存** | 仅当用旧版 encryptedData 解密手机号时需要；新方案不需要 | 0（采用新方案则无此缺口） |
| 5 | **隐私授权弹窗（`wx.requirePrivacyAuthorize`）** | 微信 2023 起强制，不弹会被审核驳回 | 0.5 天 |
| 6 | **真实 appid/secret 配置** | real 模式跑不起来（需企业主体 + 认证） | 资质依赖（非技术） |
| 7 | **域名白名单 + HTTPS + 备案** | 真机/上架必须 | 资质依赖（备案 7-20 天） |

### 7.3 与 docs/12 一致性

`docs/12-wechat-miniprogram-launch-plan.md` §1.2 列的三大硬阻断：
- **B1（微信登录是占位）** → ✅ 已解决（三态 Provider + 真实实现）
- **B2（登录页无微信入口）** → ✅ 已解决（`login.vue` 加了微信按钮）
- **B3（默认连 127.0.0.1:8080）** → ⚠️ 未解决（仍是 demo 形态，上架前需改）

---

## 8. 从 demo 到上线的差距清单

### 8.1 技术差距（约 1-2 周）

| 任务 | 文件 | 优先级 |
|---|---|---|
| 后端：手机号获取端点 + access_token 缓存 | 新增 `WechatPhoneNumberService` + `AccessTokenService` | P1 |
| 后端：头像昵称更新端点 | `UserController` + `UserService` 加 `updateProfile` | P1 |
| 前端：手机号授权按钮（getPhoneNumber） | `login.vue` 或新页 `bind-phone.vue` | P1 |
| 前端：头像昵称完善页（chooseAvatar + nickname） | `profile/edit.vue` | P1 |
| 前端：隐私授权弹窗 | `App.vue` 加 `wx.requirePrivacyAuthorize` | P0（审核硬门槛） |
| 前端：用户协议 + 隐私政策页面 | 新增 2 页 | P0 |
| 前端：客服按钮（`open-type="contact"`） | tabBar 或设置页 | P0（宠物服务类目要求） |
| 配置：`.env.production` 改真实 HTTPS 域名 | `frontend/miniapp/.env.production` | P0 |
| 配置：`manifest.json` 填真实 appid | `frontend/miniapp/src/manifest.json` | P0 |
| 测试：jscode2session 真实联调 + 错误码用例 | `RealWechatLoginProviderTest` | P0 |

### 8.2 资质差距（硬阻断，约 3-6 周）

| 资质 | 用途 | 周期 | 费用 | 来源 |
|---|---|---|---|---|
| **营业执照**（企业或个体工商户） | 小程序主体认证前置 | 3-15 工作日 | 几十~几百元 | 当地市监局 |
| **小程序账号 + 微信认证** | 拿 appid、解锁 getPhoneNumber | 认证 1-3 工作日 | 300 元/年 | mp.weixin.qq.com |
| **微信支付商户号**（若保留商品零售） | 小程序内支付 | 3-7 工作日 | 0（费率 0.6%） | pay.weixin.qq.com |
| **ICP 备案域名** | 后端 API 必须备案 + HTTPS | **7-20 工作日**（最长） | 域名费 + 服务器 | 阿里云/腾讯云 |
| **SSL 证书** | HTTPS 必备 | 即时（Let's Encrypt） | 0~几百元 | — |
| **食品经营许可证**（若保留宠物食品零售） | 商品零售类目资质 | 15-30 工作日 | 视地区 | 市监局 |

**关键路径**：域名备案 ≈ 营业执照 ≈ 商户号，总周期 **3-6 周**。这期间技术可以并行做完 §8.1。

> 完整资质清单与策略见 `docs/12-wechat-miniprogram-launch-plan.md` §2、§5。

---

## 9. 安全注意事项

### 9.1 secret 保护（最高优先级）

- **AppSecret 永不入库、不入 git、不下发前端**。项目用环境变量（`WechatProperties.secret`），符合要求。
- **jscode2session 必须在后端调用**——若在前端调，secret 会暴露在小程序包里（可被反编译）。
- **日志绝不记 secret**。项目 `RealWechatLoginProvider` 已遵守（只记 `appid present=true/false`）。

### 9.2 session_key 不下发前端

- `session_key` 是微信返回给后端的"解密钥匙"，**绝不通过任何 API 返回前端**。
- 项目用 JWT 维护登录态，**不需要把 session_key 给前端**——前端只拿自己的 JWT。
- 如未来用旧版 encryptedData 解密手机号，session_key 仅在后端内存/Redis 缓存（TTL 跟随 session 有效期）。

### 9.3 openid 隐私

- openid 是用户在本小程序内的唯一标识，**属个人信息**，需在隐私政策中披露用途。
- 数据库存储时建议**脱敏或加密**（项目当前明文存 `user.openid`，单门店 demo 可接受；规模化后建议加密）。
- 日志中 openid 建议截断（如只记前 6 后 4）。

### 9.4 code 防重放

- code **一次性**，重复用会报 40029。但业务侧仍需防"同一 code 被多次提交到后端"：
  - 前端 `login.vue` 已用 `wxLoginLoading` 防连点 ✅
  - 后端可选：对同一 code 做短期幂等缓存（如 Redis 5 分钟），重复提交直接返回上次结果

### 9.5 access_token 缓存（如启用手机号接口）

- access_token 有效期 7200 秒（2 小时），**必须服务端集中缓存**，不能每次调接口都重新获取（会被微信限频）。
- 多实例部署时，缓存需集中（Redis）或用 `stable_token`（多实例安全）。
- **绝不下发前端**。

### 9.6 错误信息脱敏

- 微信返回的 `errmsg` 可能含 secret 邻近信息或用户敏感数据，**绝不直接透传给前端**。
- 项目已实现：所有 errcode≠0 收敛为 `WECHAT_JS_CODE_INVALID` + 中文"微信授权码无效"。

### 9.7 域名白名单

- 只在白名单内的域名可被小程序访问，相当于一层"出站防火墙"。
- 后端 API 域名、对象存储域名、CDN 域名都需配置。
- 切勿把内网/开发域名遗留到生产配置。

---

## 10. 修订记录

| 版本 | 日期 | 作者 | 说明 |
|---|---|---|---|
| v1.0 | 2026-08-01 | Agent（微信登录调研） | 首次完整调研：登录时序、API 清单、用户信息/手机号方案、个人开发者限制、SDK 评估、现有实现评估、demo→上线差距、安全注意事项。基于微信官方文档（developers.weixin.qq.com）2026 年最新规则。 |
