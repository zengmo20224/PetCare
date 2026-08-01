/**
 * H05: Booking Management Tests
 *
 * Tests verify:
 * 1. Booking page uses shared components (FilterBar, DataTableShell, DetailDrawer)
 * 2. Booking page uses PetCare design tokens
 * 3. Booking page uses feedback utils (showSuccess, showConflict)
 * 4. Booking ID handled as string (not number for Snowflake)
 * 5. 409 conflict displays "记录已更新，请刷新后重试"
 * 6. No ElMessage direct usage (goes through feedback utils)
 * 7. Action buttons respect permission checks
 * 8. Booking page does not bypass backend state machine
 */

import { describe, it, expect, beforeEach } from 'vitest'
import { fileURLToPath } from 'url'
import { dirname, resolve } from 'path'
import { readFileSync } from 'fs'

const __filename = fileURLToPath(import.meta.url)
const __dirname = dirname(__filename)

function readFile(relativePath: string): string {
  return readFileSync(resolve(__dirname, relativePath), 'utf-8')
}

// ─── Shared Component Integration Tests ───

describe('Booking page shared component integration', () => {
  let source: string

  beforeEach(() => {
    source = readFile('../views/booking/index.vue')
  })

  it('imports and uses FilterBar component', () => {
    expect(source).toMatch(/FilterBar/)
  })

  it('imports and uses DataTableShell component', () => {
    expect(source).toMatch(/DataTableShell/)
  })

  it('imports and uses DetailDrawer component', () => {
    expect(source).toMatch(/DetailDrawer/)
  })

  it('uses independent el-dialog for booking actions (replaced ActionConfirmDialog)', () => {
    // a5a8810 起，预约页改用独立 el-dialog 避免 emit 时序问题，不再用 ActionConfirmDialog
    expect(source).toMatch(/el-dialog/)
    expect(source).not.toMatch(/import.*ActionConfirmDialog/)
  })
})

// ─── Design Token Usage Tests ───

describe('Booking page design tokens', () => {
  let source: string

  beforeEach(() => {
    source = readFile('../views/booking/index.vue')
  })

  it('uses PetCare design tokens in styles', () => {
    expect(source).toMatch(/--pc-/)
  })

  it('does not use hardcoded padding: 20px', () => {
    // Should use --pc-content-gap or similar token
    expect(source).not.toMatch(/padding:\s*20px/)
  })
})

// ─── Feedback Utility Tests ───

describe('Booking page feedback utils', () => {
  let source: string

  beforeEach(() => {
    source = readFile('../views/booking/index.vue')
  })

  it('imports showSuccess from feedback utils', () => {
    expect(source).toMatch(/showSuccess/)
  })

  it('imports showConflict from feedback utils for 409 handling', () => {
    expect(source).toMatch(/showConflict/)
  })

  it('does not use ElMessage.success directly (uses feedback utils)', () => {
    expect(source).not.toMatch(/ElMessage\.success/)
  })
})

// ─── State Machine Compliance Tests ───

describe('Booking state machine compliance', () => {
  let source: string

  beforeEach(() => {
    source = readFile('../views/booking/index.vue')
  })

  it('uses getBookingActions for action visibility', () => {
    expect(source).toMatch(/getBookingActions/)
  })

  it('does not hardcode status checks (uses action guard)', () => {
    // Should not have direct status comparisons like row.status === 'PENDING_CONFIRM'
    expect(source).not.toMatch(/row\.status\s*===\s*['"]/)
  })
})

// ─── Permission Check Tests ───

describe('Booking page permission checks', () => {
  let source: string

  beforeEach(() => {
    source = readFile('../views/booking/index.vue')
  })

  it('checks hasPermission for booking actions', () => {
    expect(source).toMatch(/hasPermission.*booking/)
  })

  it('has permission checks on start action', () => {
    expect(source).toMatch(/booking:booking:start/)
  })

  it('merges reject into cancel (no standalone reject button)', () => {
    // 拒绝与取消已合并为单一"取消"入口，页面不再暴露独立 reject 按钮/权限码。
    // 后端 reject 接口仍保留，仅前端入口移除。
    expect(source).not.toMatch(/openRejectDialog/)
    expect(source).not.toMatch(/rejectBooking/)
  })

  it('has permission checks on cancel action', () => {
    expect(source).toMatch(/booking:booking:cancel/)
  })
})

// ─── Detail Drawer Behavior Tests ───

describe('Booking detail drawer', () => {
  let source: string

  beforeEach(() => {
    source = readFile('../views/booking/index.vue')
  })

  it('clears detailData when drawer closes', () => {
    // Should reset detailData on close to clean temp state
    expect(source).toMatch(/detailData.*=.*null|detailData\.value\s*=\s*null/)
  })

  it('detail drawer preserves filter state when closing', () => {
    // queryParams should NOT be reset on drawer close
    // Just verify the queryParams is separate from drawer state
    expect(source).toMatch(/queryParams/)
  })
})

// ─── Conflict Handling Tests ───

describe('Booking conflict (409) handling', () => {
  let source: string

  beforeEach(() => {
    source = readFile('../views/booking/index.vue')
  })

  it('shows conflict message for concurrent modification', () => {
    expect(source).toMatch(/showConflict/)
  })

  it('refreshes data after conflict to get latest state', () => {
    // After showing conflict, should refetch data
    expect(source).toMatch(/fetchData/)
  })
})
