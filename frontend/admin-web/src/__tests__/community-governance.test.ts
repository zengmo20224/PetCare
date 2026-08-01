/**
 * H09 — Community Governance Tests (RED phase)
 *
 * Covers both posts.vue and reports.vue:
 *  1. Shared component usage (FilterBar, DataTableShell, independent el-dialog)
 *  2. PetCare design tokens & BEM naming
 *  3. Feedback utils — no direct ElMessage / ElMessageBox
 *  4. Permission-based action button gating
 *  5. Status enum rendering (POST_STATUS, REPORT_HANDLE_RESULT)
 *  6. Query param lifecycle (handlePageChange, handleReset)
 *  7. Reports: use REPORT_HANDLE_RESULT for status dropdown instead of hardcoded options
 *  8. Posts: conditional action visibility based on row status
 *  9. Reports: handle dialog form validation
 */

import { describe, it, expect } from 'vitest'
import { readFileSync } from 'fs'
import { resolve, dirname } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const src = (rel: string) => resolve(__dirname, '..', rel)
const read = (rel: string) => readFileSync(src(rel), 'utf-8')

const postsVue = read('views/community/posts.vue')
const reportsVue = read('views/community/reports.vue')

// ──────────────────────────────────────────
// 1. Posts — shared components
// ──────────────────────────────────────────
describe('H09: Posts — shared components', () => {
  it('uses FilterBar component', () => {
    expect(postsVue).toContain('FilterBar')
    expect(postsVue).toContain('@search')
    expect(postsVue).toContain('@reset')
  })

  it('uses DataTableShell component', () => {
    expect(postsVue).toContain('DataTableShell')
    expect(postsVue).toContain(':data=')
    expect(postsVue).toContain(':total=')
    expect(postsVue).toContain('@page-change=')
  })

  it('uses independent el-dialog for post actions (replaced ActionConfirmDialog)', () => {
    // 6913f7f 起 ActionConfirmDialog 组件的 emit('confirm') 链路有时序缺陷（请求发不出），
    // 帖子页沿用 booking 页的同款修复：每个动作一个独立 el-dialog。
    expect(postsVue).toContain('el-dialog')
    // 不应再 import 该组件，也不应把它作为模板标签使用
    expect(postsVue).not.toMatch(/import\s+ActionConfirmDialog\s+from/)
    expect(postsVue).not.toMatch(/<ActionConfirmDialog[\s>]/)
    // 也不应保留旧的"待执行闭包"三件套（作为代码标识符）
    expect(postsVue).not.toMatch(/\bconst\s+pendingAction\b/)
    expect(postsVue).not.toMatch(/\bconst\s+executeConfirmedAction\b/)
  })

  it('does NOT use raw el-pagination', () => {
    expect(postsVue).not.toMatch(/<el-pagination/)
  })

  it('does NOT use raw el-card wrapper', () => {
    expect(postsVue).not.toMatch(/<el-card/)
  })
})

// ──────────────────────────────────────────
// 2. Reports — shared components
// ──────────────────────────────────────────
describe('H09: Reports — shared components', () => {
  it('uses FilterBar component', () => {
    expect(reportsVue).toContain('FilterBar')
    expect(reportsVue).toContain('@search')
    expect(reportsVue).toContain('@reset')
  })

  it('uses DataTableShell component', () => {
    expect(reportsVue).toContain('DataTableShell')
    expect(reportsVue).toContain(':data=')
    expect(reportsVue).toContain(':total=')
    expect(reportsVue).toContain('@page-change=')
  })

  it('does NOT use raw el-pagination', () => {
    expect(reportsVue).not.toMatch(/<el-pagination/)
  })

  it('does NOT use raw el-card wrapper', () => {
    expect(reportsVue).not.toMatch(/<el-card/)
  })
})

// ──────────────────────────────────────────
// 3. Design tokens & BEM
// ──────────────────────────────────────────
describe('H09: Design tokens', () => {
  it('posts page uses --pc-* CSS variables', () => {
    expect(postsVue).toMatch(/--pc-/)
  })

  it('posts page uses BEM naming (pc-community-posts)', () => {
    expect(postsVue).toMatch(/pc-community-posts/)
  })

  it('reports page uses --pc-* CSS variables', () => {
    expect(reportsVue).toMatch(/--pc-/)
  })

  it('reports page uses BEM naming (pc-community-reports)', () => {
    expect(reportsVue).toMatch(/pc-community-reports/)
  })
})

// ──────────────────────────────────────────
// 4. Feedback utils — no raw ElMessage/ElMessageBox
// ──────────────────────────────────────────
describe('H09: Feedback utilities', () => {
  it('posts page does NOT import ElMessage directly', () => {
    expect(postsVue).not.toMatch(/import.*ElMessage[^B].*from.*element-plus/)
  })

  it('posts page does NOT import ElMessageBox directly', () => {
    expect(postsVue).not.toMatch(/import.*ElMessageBox.*from.*element-plus/)
  })

  it('posts page imports showSuccess/showError from feedback utils', () => {
    expect(postsVue).toMatch(/showSuccess|showError/)
    expect(postsVue).toMatch(/from.*utils\/feedback/)
  })

  it('reports page does NOT import ElMessage directly', () => {
    expect(reportsVue).not.toMatch(/import.*ElMessage[^B].*from.*element-plus/)
  })

  it('reports page imports showSuccess/showError from feedback utils', () => {
    expect(reportsVue).toMatch(/showSuccess|showError/)
    expect(reportsVue).toMatch(/from.*utils\/feedback/)
  })
})

// ──────────────────────────────────────────
// 5. Permission checks
// ──────────────────────────────────────────
describe('H09: Permission gating', () => {
  it('posts approve gated by community:post:approve', () => {
    expect(postsVue).toContain("hasPermission('community:post:approve')")
  })

  it('posts reject gated by community:post:reject', () => {
    expect(postsVue).toContain("hasPermission('community:post:reject')")
  })

  it('posts hide gated by community:post:hide', () => {
    expect(postsVue).toContain("hasPermission('community:post:hide')")
  })

  it('posts delete gated by community:post:delete', () => {
    expect(postsVue).toContain("hasPermission('community:post:delete')")
  })

  it('reports handle gated by community:report:handle', () => {
    expect(reportsVue).toContain("hasPermission('community:report:handle')")
  })
})

// ──────────────────────────────────────────
// 6. Status enums
// ──────────────────────────────────────────
describe('H09: Status enums', () => {
  it('posts page imports POST_STATUS', () => {
    expect(postsVue).toContain('POST_STATUS')
  })

  it('reports page imports REPORT_HANDLE_RESULT', () => {
    expect(reportsVue).toContain('REPORT_HANDLE_RESULT')
  })

  it('reports status dropdown uses REPORT_HANDLE_RESULT enum (not hardcoded)', () => {
    // Should not have hardcoded <el-option label="待处理" value="PENDING">
    // The report status filter should use an enum or at minimum not duplicate labels
    // For the status column, the template should use a status map not inline v-if chains
    expect(reportsVue).not.toMatch(/<el-option label="待处理" value="PENDING"/)
    expect(reportsVue).not.toMatch(/<el-option label="已处理" value="PROCESSED"/)
    expect(reportsVue).not.toMatch(/<el-option label="已忽略" value="IGNORED"/)
  })
})

// ──────────────────────────────────────────
// 7. Query param lifecycle
// ──────────────────────────────────────────
describe('H09: Query param lifecycle', () => {
  it('posts page has handlePageChange', () => {
    expect(postsVue).toContain('handlePageChange')
  })

  it('posts page has handleReset', () => {
    expect(postsVue).toContain('handleReset')
  })

  it('reports page has handlePageChange', () => {
    expect(reportsVue).toContain('handlePageChange')
  })

  it('reports page has handleReset', () => {
    expect(reportsVue).toContain('handleReset')
  })
})

// ──────────────────────────────────────────
// 8. Posts: conditional action visibility
// ──────────────────────────────────────────
describe('H09: Posts conditional actions', () => {
  it('approve only shown for PENDING_REVIEW', () => {
    expect(postsVue).toMatch(/v-if.*PENDING_REVIEW.*approve|v-if.*PENDING_REVIEW[\s\S]*?通过/)
  })

  it('reject only shown for PENDING_REVIEW', () => {
    expect(postsVue).toMatch(/v-if.*PENDING_REVIEW.*reject|v-if.*PENDING_REVIEW[\s\S]*?拒绝/)
  })

  it('hide shown for PUBLISHED and PENDING_REVIEW', () => {
    expect(postsVue).toMatch(/PUBLISHED.*PENDING_REVIEW|PENDING_REVIEW.*PUBLISHED/)
  })
})

// ──────────────────────────────────────────
// 9. Reports: handle dialog
// ──────────────────────────────────────────
describe('H09: Reports handle dialog', () => {
  it('uses REPORT_HANDLE_RESULT enum for handle result dropdown', () => {
    expect(reportsVue).toMatch(/REPORT_HANDLE_RESULT.*el-option|v-for.*REPORT_HANDLE_RESULT/)
  })

  it('handle form has validation rules', () => {
    expect(reportsVue).toMatch(/handleRules|handleResult.*required/)
  })

  it('handle dialog resets form on open', () => {
    expect(reportsVue).toMatch(/handleResult.*=.*'PROCESSED'|handleResult.*PROCESSED/)
  })
})
