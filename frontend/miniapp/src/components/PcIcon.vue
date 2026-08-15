<template>
  <image
    class="pc-icon"
    :src="iconSrc"
    :style="iconStyle"
    mode="aspectFit"
  />
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * PcIcon —— demo 线性图标库的 1:1 小程序复刻
 *
 * 从 ui-demo/index.html 的 <symbol> 库逐个提取 path 数据，
 * 通过 SVG data URI 渲染（mp-weixin <image> 支持 base64 data URI）。
 * 保留 demo 原汁原味：stroke-width 1.8、圆角线帽、currentColor 着色。
 */

const props = withDefaults(defineProps<{
  /** 图标名（对应 demo symbol id，去掉 i- 前缀） */
  name: string
  /** 颜色，默认 currentColor 语义（这里用具体色值兜底） */
  color?: string
  /** 尺寸（px），默认 20 */
  size?: number
}>(), {
  color: '#333333',
  size: 20
})

// demo 所有 32 个图标的 path 数据（viewBox 0 0 24 24，stroke-width 1.8）
const PATHS: Record<string, string> = {
  // 导航/操作
  home: '<path d="M3 10.5L12 3l9 7.5"/><path d="M5 9.5V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9.5"/><path d="M9.5 21v-6h5v6"/>',
  calendar: '<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M3 9h18M8 3v4M16 3v4"/>',
  chat: '<path d="M21 11.5a8.38 8.38 0 0 1-8.5 8.5 8.5 8.5 0 0 1-3.6-.8L3 21l1.9-5.9A8.38 8.38 0 0 1 4 11.5 8.5 8.5 0 0 1 12.5 3 8.38 8.38 0 0 1 21 11.5z"/>',
  bag: '<path d="M6 2L3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><path d="M3 6h18M16 10a4 4 0 0 1-8 0"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4 21v-1a6 6 0 0 1 6-6h4a6 6 0 0 1 6 6v1"/>',
  // 服务/宠物
  bath: '<path d="M4 12h16a1 1 0 0 1 1 1 5 5 0 0 1-5 5H8a5 5 0 0 1-5-5 1 1 0 0 1 1-1z"/><path d="M6 12V6a2 2 0 0 1 2-2h1M7 18l-1 3M18 18l1 3"/><circle cx="10" cy="8" r="1"/>',
  scissors: '<circle cx="6" cy="6" r="2.5"/><circle cx="6" cy="18" r="2.5"/><path d="M8 8l12 8M8 16L20 8"/>',
  door: '<path d="M5 21V4a1 1 0 0 1 1-1h9a1 1 0 0 1 1 1v17"/><path d="M3 21h18M16 12h.01"/>',
  paw: '<circle cx="6" cy="8" r="1.8"/><circle cx="10" cy="5.5" r="1.8"/><circle cx="14" cy="5.5" r="1.8"/><circle cx="18" cy="8" r="1.8"/><path d="M12 11c-3 0-5 2.5-5 5 0 1.7 1.5 3 3.2 3 1 0 1.3-.5 1.8-.5s.8.5 1.8.5c1.7 0 3.2-1.3 3.2-3 0-2.5-2-5-5-5z"/>',
  // 商品分类
  bone: '<path d="M7 17a3 3 0 1 1-3-3 3 3 0 0 1 3-3 3 3 0 1 1 3 3M17 7a3 3 0 1 0 3 3 3 3 0 0 0-3-3 3 3 0 1 0-3 3"/><path d="M10 10l4 4"/>',
  ball: '<circle cx="12" cy="12" r="9"/><path d="M3 12c4-3 14-3 18 0M12 3c-3 4-3 14 0 18"/>',
  box: '<path d="M21 8L12 3 3 8l9 5 9-5z"/><path d="M3 8v8l9 5 9-5V8M12 13v8"/>',
  jar: '<path d="M7 3h10v3l1 1v13a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V7l1-1V3z"/><path d="M6 11h12"/>',
  // 通用/操作
  search: '<circle cx="11" cy="11" r="7"/><path d="M21 21l-4.3-4.3"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  shop: '<path d="M4 9l1-5h14l1 5M4 9h16v10a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V9z"/><path d="M4 9a2 2 0 0 0 4 0 2 2 0 0 0 4 0 2 2 0 0 0 4 0 2 2 0 0 0 4 0M10 21v-5h4v5"/>',
  bell: '<path d="M18 8a6 6 0 0 0-12 0c0 7-3 9-3 9h18s-3-2-3-9"/><path d="M13.5 21a2 2 0 0 1-3 0"/>',
  megaphone: '<path d="M3 11v2a1 1 0 0 0 1 1h2l5 4V6L6 10H4a1 1 0 0 0-1 1z"/><path d="M15 8a4 4 0 0 1 0 8M18 5a8 8 0 0 1 0 14"/>',
  heart: '<path d="M20.8 5.6a5.5 5.5 0 0 0-7.8 0L12 6.6l-1-1a5.5 5.5 0 0 0-7.8 7.8l1 1L12 22l7.8-7.6 1-1a5.5 5.5 0 0 0 0-7.8z"/>',
  bookmark: '<path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z"/>',
  edit: '<path d="M12 20h9"/><path d="M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/>',
  cart: '<circle cx="9" cy="21" r="1.5"/><circle cx="19" cy="21" r="1.5"/><path d="M1 1h4l2.7 13.4a2 2 0 0 0 2 1.6h9.7a2 2 0 0 0 2-1.6L23 6H6"/>',
  wallet: '<path d="M21 12V8a2 2 0 0 0-2-2H5a2 2 0 0 1 0-4h14v4"/><path d="M3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-4"/><circle cx="17" cy="14" r="1.2" fill="currentColor"/>',
  pin: '<path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/>',
  receipt: '<path d="M4 2v20l2-1 2 1 2-1 2 1 2-1 2 1 2-1 2 1V2l-2 1-2-1-2 1-2-1-2 1-2-1-2 1z"/><path d="M8 8h8M8 12h8M8 16h5"/>',
  shield: '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/>',
  lock: '<rect x="4" y="11" width="16" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>',
  'arrow-right': '<path d="M9 6l6 6-6 6"/>',
  robot: '<rect x="4" y="8" width="16" height="12" rx="3"/><path d="M12 8V4M9 4h6"/><circle cx="9" cy="14" r="1.2" fill="currentColor"/><circle cx="15" cy="14" r="1.2" fill="currentColor"/><path d="M2 13v2M22 13v2"/>',
  gift: '<rect x="3" y="8" width="18" height="4"/><path d="M5 12v9a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1v-9M12 8v13"/><path d="M12 8S10 2 7.5 2 5 5 7 8M12 8s2-6 4.5-6S19 5 17 8"/>',
  sparkle: '<path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z"/><path d="M19 16l.7 2 2 .7-2 .7-.7 2-.7-2-2-.7 2-.7z"/>',
  bottle: '<path d="M9 2h6v3l1 2v13a1 1 0 0 1-1 1H9a1 1 0 0 1-1-1V7l1-2V2z"/><path d="M8 11h8"/>'
}

const iconSrc = computed(() => {
  const inner = PATHS[props.name]
  if (!inner) {
    // 未知图标：渲染空 svg 避免破图
    return 'data:image/svg+xml;base64,PHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciLz4='
  }
  // 构造完整 SVG：与 demo 完全一致的 stroke 配置
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="${props.color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${inner}</svg>`
  // base64 编码 —— mp-weixin <image> 官方支持 base64 data URI
  // 用 Uint16 -> base64 路径，避免 btoa 的 Unicode 异常
  return `data:image/svg+xml;base64,${uniToBase64(svg)}`
})

/**
 * Unicode 安全的 base64 编码（SVG 内含中文/特殊字符也能正确编码）
 * 方案：先用 encodeURIComponent + unescape 转成纯 Latin1，再 btoa
 */
function uniToBase64(str: string): string {
  // #ifdef H5
  return btoa(unescape(encodeURIComponent(str)))
  // #endif
  // #ifndef H5
  // mp-weixin / app 无 btoa/unescape，手动实现
  const utf8Bytes = utf8Encode(str)
  let binary = ''
  for (let i = 0; i < utf8Bytes.length; i++) {
    binary += String.fromCharCode(utf8Bytes[i])
  }
  return base64Encode(binary)
  // #endif
}

function utf8Encode(str: string): number[] {
  const bytes: number[] = []
  for (let i = 0; i < str.length; i++) {
    const code = str.charCodeAt(i)
    if (code < 0x80) {
      bytes.push(code)
    } else if (code < 0x800) {
      bytes.push(0xc0 | (code >> 6), 0x80 | (code & 0x3f))
    } else {
      bytes.push(0xe0 | (code >> 12), 0x80 | ((code >> 6) & 0x3f), 0x80 | (code & 0x3f))
    }
  }
  return bytes
}

const BASE64_CHARS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/'

function base64Encode(str: string): string {
  let result = ''
  let i = 0
  while (i < str.length) {
    const a = str.charCodeAt(i++)
    const b = i < str.length ? str.charCodeAt(i++) : -1
    const c = i < str.length ? str.charCodeAt(i++) : -1
    result += BASE64_CHARS[a >> 2]
    result += BASE64_CHARS[((a & 3) << 4) | ((b >> 4) & 0xf)]
    result += b === -1 ? '=' : BASE64_CHARS[((b & 0xf) << 2) | ((c >> 6) & 3)]
    result += c === -1 ? '=' : BASE64_CHARS[c & 0x3f]
  }
  return result
}

const iconStyle = computed(() => ({
  width: `${props.size}px`,
  height: `${props.size}px`
}))
</script>

<style scoped>
.pc-icon {
  display: inline-block;
  vertical-align: middle;
  flex-shrink: 0;
}
</style>
