import { describe, expect, it } from 'vitest'
import { readFileSync } from 'fs'
import { resolve } from 'path'

const read = (relativePath: string) => readFileSync(resolve(__dirname, '..', relativePath), 'utf-8')
const pagesJson = JSON.parse(read('pages.json'))
const demoPage = read('pages/design-demo/index.vue')
const wotIconStyles = read('../node_modules/wot-design-uni/components/wd-icon/index.scss')

describe('mini-program native-feel design demo', () => {
  it('registers the demo as an isolated subpackage page', () => {
    const designDemoPackage = pagesJson.subPackages.find(
      (item: { root: string }) => item.root === 'pages/design-demo',
    )

    expect(designDemoPackage?.pages).toContainEqual(expect.objectContaining({ path: 'index' }))
  })

  it('uses the installed Wot Design Uni components and icon set', () => {
    expect(demoPage).toContain('<wd-button')
    expect(demoPage).toContain('<wd-tag')
    expect(demoPage).toContain('<wd-icon')
    expect(demoPage).toContain('class="demo-tabbar"')
    expect(demoPage).not.toContain('<wd-tabbar')

    const iconNames = [
      'location',
      'heart-filled',
      'calendar',
      'notification',
      'service',
      'edit-outline',
      'home',
      'heart',
      'arrow-right',
      'photo',
      'star',
      'chat',
      'goods',
      'user',
    ]
    for (const iconName of iconNames) {
      expect(wotIconStyles).toContain(`.wd-icon-${iconName}:before`)
    }
  })

  it('stays static, lightweight and mini-program compatible', () => {
    expect(demoPage).not.toContain("from '@/api/")
    expect(demoPage).not.toContain('linear-gradient')
    expect(demoPage).not.toContain('box-shadow')
    expect(demoPage).not.toContain('backdrop-filter')
    expect(demoPage).not.toContain('aspect-ratio')
    expect(demoPage).not.toContain('env(')
    expect(demoPage).toContain('min-height: 88rpx')
    expect(demoPage).toContain('safeAreaInsets?.bottom')
    expect(demoPage).toContain(':style="tabbarStyle"')
  })
})
