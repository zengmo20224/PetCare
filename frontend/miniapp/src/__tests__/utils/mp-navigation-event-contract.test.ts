import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'fs'
import { resolve, dirname, join } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const srcRoot = resolve(__dirname, '..', '..')
const componentsDir = resolve(srcRoot, 'components')
const pagesDir = resolve(srcRoot, 'pages')

const read = (absPath: string) => readFileSync(absPath, 'utf-8')

/** 递归收集某目录下所有 .vue 文件的绝对路径 */
function collectVueFiles(dir: string): string[] {
  const out: string[] = []
  for (const name of readdirSync(dir)) {
    const full = join(dir, name)
    const st = statSync(full)
    if (st.isDirectory()) out.push(...collectVueFiles(full))
    else if (name.endsWith('.vue')) out.push(full)
  }
  return out
}

/** 受本契约约束的公共交互组件：禁止对外声明/发送原生 tap，必须改用 press */
const publicInteractiveComponents = [
  'PcProductCard.vue',
  'PcServiceCard.vue',
  'PcBookingCard.vue',
  'PcFab.vue',
  'PcPrimaryButton.vue',
]

/** 调用方禁止在这些公共组件上写 @tap */
const componentTagNames = [
  'PcProductCard',
  'PcServiceCard',
  'PcBookingCard',
  'PcFab',
  'PcPrimaryButton',
]

/**
 * 提取 Vue SFC 的 <template> 片段，避免误判 <script>/<style> 中合法的 'tap' 字面量。
 * SFC 模板中可能含 <template>...</template>；正则非贪婪取第一段。
 */
function templateOf(content: string): string {
  const m = content.match(/<template[^>]*>([\s\S]*?)<\/template>/)
  return m ? m[1] : ''
}

describe('mp-weixin navigation event contract', () => {
  describe('公共交互组件只发 press，不发 tap', () => {
    for (const file of publicInteractiveComponents) {
      it(`${file} 不对外声明/发送 tap 事件`, () => {
        const content = read(resolve(componentsDir, file))
        // script 内 emits 不再声明 tap
        expect(content).not.toMatch(/emits[^\n]*['"]tap['"]/i)
        // 模板/脚本不再 $emit('tap') 或 emit('tap')
        expect(content).not.toMatch(/\$emit\(\s*['"]tap['"]\s*\)/)
        expect(content).not.toMatch(/[^.$]emit\(\s*['"]tap['"]\s*\)/)
        // 必须声明并发送 press
        expect(content).toMatch(/['"]press['"]/)
      })

      it(`${file} 模板层不再向外冒泡原生 tap`, () => {
        const tpl = templateOf(read(resolve(componentsDir, file)))
        // PcPrimaryButton 内部仍可监听 wd-button 的 @click，但它不应再向外 $emit('tap')
        // 其余组件不应在模板上出现 @tap="$emit('tap')" 形态
        expect(tpl).not.toMatch(/\$emit\(\s*['"]tap['"]\s*\)/)
      })
    }
  })

  describe('调用方不对公共交互组件使用 @tap', () => {
    const allVueFiles = [...collectVueFiles(componentsDir), ...collectVueFiles(pagesDir)]

    for (const file of allVueFiles) {
      const rel = file.replace(srcRoot + '\\', '').replace(srcRoot + '/', '')
      it(`${rel} 不在公共交互组件上写 @tap`, () => {
        const tpl = templateOf(read(file))
        for (const tag of componentTagNames) {
          // 匹配跨行：<PcPrimaryButton ... @tap=  /  <PcFab @tap=
          const re = new RegExp(`<${tag}\\b[^>]*?\\@tap\\b`, 's')
          expect(tpl).not.toMatch(re)
        }
      })
    }
  })

  describe('一次触摸只产生一次 press（语义契约）', () => {
    it('PcPrimaryButton 通过单一 handleClick 桥接 wd-button click -> press', () => {
      const content = read(resolve(componentsDir, 'PcPrimaryButton.vue'))
      // 内部仍监听 wd-button 的 click，但对外只 emit press
      expect(content).toContain('@click="handleClick"')
      expect(content).toMatch(/emit\(\s*['"]press['"]\s*\)/)
      // 不再保留向外的 tap 桥接
      expect(content).not.toMatch(/emit\(\s*['"]tap['"]\s*\)/)
    })
  })
})
