import { describe, it, expect } from 'vitest'
import { readFileSync } from 'fs'
import { resolve } from 'path'

describe('Design Tokens Integrity', () => {
  const tokensPath = resolve(__dirname, '../styles/tokens.css')
  const tokensContent = readFileSync(tokensPath, 'utf-8')

  it('contains all required color tokens', () => {
    const requiredColors = [
      '--pc-user-primary',
      '--pc-user-dark',
      '--pc-user-soft',
      '--pc-user-cream',
      '--pc-user-accent',
      '--pc-user-accent-soft',
      '--pc-user-danger',
      '--pc-user-ink',
      '--pc-user-muted',
      '--pc-user-line',
    ]
    for (const token of requiredColors) {
      expect(tokensContent).toContain(token)
    }
  })

  it('contains spacing tokens', () => {
    expect(tokensContent).toContain('--pc-page-padding')
    expect(tokensContent).toContain('--pc-card-gap')
    expect(tokensContent).toContain('--pc-section-gap')
  })

  it('contains radius tokens', () => {
    expect(tokensContent).toContain('--pc-radius-card')
    expect(tokensContent).toContain('--pc-radius-card-lg')
  })

  it('contains typography tokens', () => {
    expect(tokensContent).toContain('--pc-font-title')
    expect(tokensContent).toContain('--pc-font-card-title')
    expect(tokensContent).toContain('--pc-font-body')
    expect(tokensContent).toContain('--pc-font-caption')
  })

  it('contains sizing tokens', () => {
    expect(tokensContent).toContain('--pc-btn-height')
    expect(tokensContent).toContain('--pc-tab-bar-height')
  })

  it('uses correct primary color', () => {
    expect(tokensContent).toContain('#11796F')
  })

  it('uses correct page background (V2 unified #F7F7F7)', () => {
    expect(tokensContent).toContain('#F7F7F7')
  })
})
