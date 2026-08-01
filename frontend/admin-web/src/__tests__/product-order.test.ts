/**
 * H08 — Product Order Management Tests (RED phase)
 *
 * Covers:
 *  1. Shared component usage (FilterBar, DataTableShell, DetailDrawer, independent el-dialog)
 *  2. PetCare design tokens in styles (BEM naming)
 *  3. Feedback utils — no direct ElMessage / ElMessageBox
 *  4. Permission-based action button gating
 *  5. Amount display via toFixed(2) — order total, item price, item subtotal
 *  6. Status enum rendering (PRODUCT_ORDER_STATUS, PAYMENT_STATUS)
 *  7. Action guard via getProductOrderActions with full OrderActionContext
 *  8. 409 conflict handling for concurrent order modifications
 *  9. Detail drawer closes cleanly (data cleared)
 * 10. Query param lifecycle (handlePageChange, handleReset)
 */

import { describe, it, expect } from 'vitest'
import { readFileSync } from 'fs'
import { resolve, dirname } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const src = (rel: string) => resolve(__dirname, '..', rel)

const read = (rel: string) => readFileSync(src(rel), 'utf-8')

const productOrderVue = read('views/product-order/index.vue')

// ──────────────────────────────────────────
// 1. Shared components
// ──────────────────────────────────────────
describe('H08: Shared components', () => {
  it('uses FilterBar component', () => {
    expect(productOrderVue).toContain('FilterBar')
    expect(productOrderVue).toContain('@search')
    expect(productOrderVue).toContain('@reset')
  })

  it('uses DataTableShell component', () => {
    expect(productOrderVue).toContain('DataTableShell')
    expect(productOrderVue).toContain(':data=')
    expect(productOrderVue).toContain(':total=')
    expect(productOrderVue).toContain(':loading=')
    expect(productOrderVue).toContain('@page-change=')
  })

  it('uses DetailDrawer for order details', () => {
    expect(productOrderVue).toContain('DetailDrawer')
  })

  it('uses independent el-dialog for each order action (replaced ActionConfirmDialog)', () => {
    // 6913f7f 起 ActionConfirmDialog 组件的 emit('confirm') 链路有时序缺陷（请求发不出），
    // 商品订单页沿用 booking 页的同款修复：每个动作一个独立 el-dialog。
    expect(productOrderVue).toContain('el-dialog')
    // 不应再 import 该组件，也不应把它作为模板标签使用
    expect(productOrderVue).not.toMatch(/import\s+ActionConfirmDialog\s+from/)
    expect(productOrderVue).not.toMatch(/<ActionConfirmDialog[\s>]/)
  })

  it('does NOT use raw el-pagination', () => {
    expect(productOrderVue).not.toMatch(/<el-pagination/)
  })

  it('does NOT use raw el-card wrapper', () => {
    expect(productOrderVue).not.toMatch(/<el-card/)
  })

  it('does NOT use raw el-dialog for detail view (uses DetailDrawer instead)', () => {
    expect(productOrderVue).not.toMatch(/<el-dialog[^>]*title.*订单详情/)
  })
})

// ──────────────────────────────────────────
// 2. PetCare design tokens
// ──────────────────────────────────────────
describe('H08: Design tokens', () => {
  it('uses --pc-* CSS variables', () => {
    expect(productOrderVue).toMatch(/--pc-/)
  })

  it('uses BEM naming (pc-product-order)', () => {
    expect(productOrderVue).toMatch(/pc-product-order/)
  })

  it('has page title with design token class', () => {
    expect(productOrderVue).toMatch(/pc-product-order__title/)
  })
})

// ──────────────────────────────────────────
// 3. Feedback utils — no raw ElMessage/ElMessageBox
// ──────────────────────────────────────────
describe('H08: Feedback utilities', () => {
  it('does NOT import ElMessage directly', () => {
    expect(productOrderVue).not.toMatch(/import.*ElMessage[^B].*from.*element-plus/)
  })

  it('does NOT import ElMessageBox directly', () => {
    expect(productOrderVue).not.toMatch(/import.*ElMessageBox.*from.*element-plus/)
  })

  it('imports showSuccess or showError from feedback utils', () => {
    expect(productOrderVue).toMatch(/showSuccess|showError/)
    expect(productOrderVue).toMatch(/from.*utils\/feedback/)
  })
})

// ──────────────────────────────────────────
// 4. Permission-based action buttons
// ──────────────────────────────────────────
describe('H08: Permission gating', () => {
  const permissions = [
    'product:order:confirm',
    'product:order:confirm-payment',
    'product:order:ready',
    'product:order:complete',
    'product:order:cancel',
  ]

  for (const perm of permissions) {
    it(`gates "${perm}" action`, () => {
      expect(productOrderVue).toContain(`hasPermission('${perm}')`)
    })
  }
})

// ──────────────────────────────────────────
// 5. Amount display — toFixed(2)
// ──────────────────────────────────────────
describe('H08: Amount display uses toFixed(2)', () => {
  it('renders totalAmount with toFixed(2) in table', () => {
    expect(productOrderVue).toMatch(/Number\(.*\.totalAmount\)\.toFixed\(2\)/)
  })

  it('renders totalAmount with toFixed(2) in detail', () => {
    // Detail view also shows totalAmount
    const toFixedMatches = productOrderVue.match(/Number\(.*\.totalAmount\)\.toFixed\(2\)/g)
    expect(toFixedMatches).toBeTruthy()
    expect(toFixedMatches!.length).toBeGreaterThanOrEqual(2)
  })

  it('renders item price with toFixed(2) in detail', () => {
    expect(productOrderVue).toMatch(/Number\(.*\.price\)\.toFixed\(2\)/)
  })
})

// ──────────────────────────────────────────
// 6. Status enum rendering
// ──────────────────────────────────────────
describe('H08: Status enums', () => {
  it('imports PRODUCT_ORDER_STATUS', () => {
    expect(productOrderVue).toContain('PRODUCT_ORDER_STATUS')
  })

  it('imports PAYMENT_STATUS', () => {
    expect(productOrderVue).toContain('PAYMENT_STATUS')
  })

  it('imports getProductOrderActions', () => {
    expect(productOrderVue).toContain('getProductOrderActions')
  })

  it('passes full row context to getProductOrderActions (not just status string)', () => {
    // The action guard should receive OrderActionContext with paymentStatus + pickupStatus
    expect(productOrderVue).toMatch(/getProductOrderActions\(\{[^}]*status/)
    expect(productOrderVue).toMatch(/getProductOrderActions\(\{[^}]*paymentStatus/)
    expect(productOrderVue).toMatch(/getProductOrderActions\(\{[^}]*pickupStatus/)
  })
})

// ──────────────────────────────────────────
// 7. 409 Conflict handling
// ──────────────────────────────────────────
describe('H08: 409 conflict handling', () => {
  it('imports showConflict from feedback utils', () => {
    expect(productOrderVue).toMatch(/showConflict/)
  })

  it('handles 409 status code', () => {
    expect(productOrderVue).toMatch(/409/)
  })
})

// ──────────────────────────────────────────
// 8. Detail drawer lifecycle
// ──────────────────────────────────────────
describe('H08: Detail drawer lifecycle', () => {
  it('has handleDetailClose to clear detail data', () => {
    expect(productOrderVue).toContain('handleDetailClose')
  })

  it('DetailDrawer has @close event handler', () => {
    expect(productOrderVue).toMatch(/DetailDrawer[\s\S]*@close/)
  })
})

// ──────────────────────────────────────────
// 9. Query param lifecycle
// ──────────────────────────────────────────
describe('H08: Query param lifecycle', () => {
  it('has handlePageChange handler', () => {
    expect(productOrderVue).toContain('handlePageChange')
  })

  it('has handleReset handler', () => {
    expect(productOrderVue).toContain('handleReset')
  })
})

// ──────────────────────────────────────────
// 10. Independent action dialogs (replaces the buggy ActionConfirmDialog pattern)
// ──────────────────────────────────────────
describe('H08: Independent action dialogs', () => {
  it('does NOT use the buggy ActionConfirmDialog emit chain (the open/execute/pending trio)', () => {
    // 6913f7f 用 Playwright 复现：emit('confirm') → 待执行闭包内的请求发不出。
    // 仅检查"作为代码标识符"出现，避免命中解释性注释。
    expect(productOrderVue).not.toMatch(/\bconst\s+pendingAction\b/)
    expect(productOrderVue).not.toMatch(/\bfunction\s+openConfirmDialog\b|\bconst\s+openConfirmDialog\b/)
    expect(productOrderVue).not.toMatch(/\bfunction\s+executeConfirmedAction\b|\bconst\s+executeConfirmedAction\b/)
  })

  it('gives each order action an independent el-dialog with its own loading guard', () => {
    // 每个 action 直调 API + 独立 loading 防"点不到/重复点"
    expect(productOrderVue).toMatch(/confirmVisible/)
    expect(productOrderVue).toMatch(/confirmLoading/)
    expect(productOrderVue).toMatch(/cancelVisible/)
    expect(productOrderVue).toMatch(/cancelLoading/)
    expect(productOrderVue).toMatch(/outOfStockVisible/)
    expect(productOrderVue).toMatch(/outOfStockLoading/)
  })

  it('cancel/out-of-stock require a reason (audit-trail)', () => {
    expect(productOrderVue).toMatch(/cancelRules[\s\S]*reason[\s\S]*required/)
    expect(productOrderVue).toMatch(/outOfStockRules[\s\S]*reason[\s\S]*required/)
  })
})
