import { describe, expect, it } from 'vitest'
import { readFileSync, statSync } from 'fs'
import { resolve, dirname } from 'path'
import { fileURLToPath } from 'url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const src = (rel: string) => resolve(__dirname, '..', '..', rel)
const project = (rel: string) => resolve(__dirname, '..', '..', '..', rel)
const read = (rel: string) => readFileSync(src(rel), 'utf-8')
const readProject = (rel: string) => readFileSync(project(rel), 'utf-8')
const sizeOf = (rel: string) => statSync(src(rel)).size

const packageJson = JSON.parse(readProject('package.json'))
const viteConfig = readProject('vite.config.ts')
const cleanMpWeixinOutputScript = readProject('scripts/clean-mp-weixin-output.mjs')
const ensureMpWeixinStaticScript = readProject('scripts/ensure-mp-weixin-static.mjs')
const pagesJson = JSON.parse(read('pages.json'))
const app = read('App.vue')
const bottomNav = read('components/PcBottomNav.vue')
const primaryButton = read('components/PcPrimaryButton.vue')
const serviceCard = read('components/PcServiceCard.vue')
const heroCard = read('components/PcHeroCard.vue')
const statePanel = read('components/PcStatePanel.vue')
const productCard = read('components/PcProductCard.vue')
const confirmSheet = read('components/PcConfirmSheet.vue')
const homePage = read('pages/home/index.vue')
const servicesPage = read('pages/services/index.vue')
const communityPage = read('pages/community/index.vue')
const productsPage = read('pages/products/index.vue')
const activityPage = read('pages/activity/index.vue')
const activityDetailPage = read('pages/activity/detail.vue')
const profilePage = read('pages/profile/index.vue')
const loginPage = read('pages/auth/login.vue')
const registerPage = read('pages/auth/register.vue')

function registeredPages(): string[] {
  const mainPages = pagesJson.pages.map((page: { path: string }) => page.path)
  const subPages = pagesJson.subPackages.flatMap((subpackage: {
    root: string
    pages: { path: string }[]
  }) => subpackage.pages.map(page => `${subpackage.root}/${page.path}`))

  return [...mainPages, ...subPages]
}

describe('mp-weixin UI page contracts', () => {
  it('keeps restored user pages registered in pages.json', () => {
    expect(registeredPages()).toEqual(expect.arrayContaining([
      'pages/activity/index',
      'pages/activity/detail',
      'pages/addresses/index',
      'pages/addresses/edit',
      'pages/announcement/detail',
      'pages/my-community/index',
      'pages/notifications/index',
      'pages/pets/index',
      'pages/pets/edit',
      'pages/profile-edit/index',
    ]))
  })

  it('lets mp-weixin use the native designed tabBar instead of hiding it', () => {
    expect(bottomNav).toContain('isWeixinMiniProgram')
    expect(bottomNav).toContain('v-if="!isWeixin"')
    expect(bottomNav).toContain('if (!isWeixin)')
    expect(pagesJson.tabBar.list).toHaveLength(5)
    expect(pagesJson.tabBar.color).toBe('#314D48')
    expect(pagesJson.tabBar.selectedColor).toBe('#11796F')
    expect(pagesJson.tabBar.list.map((tab: { iconPath: string }) => tab.iconPath)).toEqual([
      'static/icons/home.png',
      'static/icons/booking.png',
      'static/icons/community.png',
      'static/icons/product.png',
      'static/icons/profile.png',
    ])
  })

  it('keeps the mini-program shell free of NutUI runtime components', () => {
    const shellFiles = [app, bottomNav, homePage]
    for (const file of shellFiles) {
      expect(file).not.toContain('nutui-uniapp')
      expect(file).not.toContain('<Nut')
      expect(file).not.toMatch(/Nut[A-Za-z]+/)
    }
    expect(viteConfig).not.toContain('nutui-uniapp')
  })

  it('avoids fragile mp-weixin CSS in critical tab surfaces', () => {
    // CSS custom properties (var(--pc-...)) ARE supported on libVersion 2.x+ and the
    // project pins libVersion to 3.17.0, so they are no longer forbidden. We keep
    // forbidding features that remain unstable in the mini-program runtime.
    const criticalStyleFiles = [
      heroCard,
      statePanel,
      productCard,
      primaryButton,
      confirmSheet,
      homePage,
      servicesPage,
      communityPage,
      productsPage,
      profilePage,
    ]

    for (const file of criticalStyleFiles) {
      expect(file).not.toContain('env(')
      expect(file).not.toContain('backdrop-filter')
      expect(file).not.toContain('aspect-ratio')
    }

    const criticalComponentFiles = [bottomNav, productCard, heroCard, serviceCard]
    for (const file of criticalComponentFiles) {
      expect(file).not.toMatch(/\.[A-Za-z0-9_-]+(?:--[A-Za-z0-9_-]+)?(?:\s+\.[A-Za-z0-9_-]+)*\s+text\b/)
    }

    // Sizing values were migrated px → rpx (×2) for cross-device scaling.
    expect(communityPage).toContain('right: 40rpx')
    expect(communityPage).toContain('bottom: 192rpx')
    expect(productsPage).toContain('products-cart-fab')
    expect(productsPage).toContain('right: 40rpx')
    expect(productsPage).toContain('bottom: 192rpx')
    expect(productsPage).not.toContain('products-fab')
    expect(productsPage).not.toContain('products-cart-entry')
    expect(productCard).toContain('height: 276rpx')
  })

  it('loads tab page data from lifecycle hooks instead of setup side effects', () => {
    expect(homePage).toContain('onLoad(loadHomeData)')
    expect(communityPage).not.toContain('\nloadPopularTags()\n')
    expect(communityPage).toContain('onLoad((query) =>')
    expect(productsPage).toContain('onLoad(() =>')
    expect(productsPage).not.toContain('\nloadProducts()\nloadCartCount()\n')
  })

  it('cleans stale mp-weixin output and blocks generated NutUI references', () => {
    expect(packageJson.scripts['build:mp-weixin']).toContain('clean-mp-weixin-output.mjs')
    expect(packageJson.scripts['build:mp-weixin']).toContain('ensure-mp-weixin-static.mjs')
    expect(cleanMpWeixinOutputScript).toContain('cleanOutputDirContents(outputDir)')
    expect(cleanMpWeixinOutputScript).toContain('readdirSync(targetDir)')
    expect(cleanMpWeixinOutputScript).toContain("resolve(projectRoot, 'dist', 'build', 'mp-weixin')")
    expect(ensureMpWeixinStaticScript).toContain('assertNoForbiddenMpReferences')
    expect(ensureMpWeixinStaticScript).toContain('nutui-uniapp')
    expect(ensureMpWeixinStaticScript).toContain('node-modules')
    // var(--pc-*) is now allowed (libVersion 3.17.0 supports CSS custom properties),
    // so it was removed from the forbidden list; the script still forbids the rest.
    expect(ensureMpWeixinStaticScript).toContain('backdrop-filter')
    expect(ensureMpWeixinStaticScript).toContain('aspect-ratio')
    expect(ensureMpWeixinStaticScript).toContain('&gt;')
  })

  it('does not use literal greater-than arrows that render as entities in WXML', () => {
    const arrowFiles = [
      homePage,
      servicesPage,
      productsPage,
      profilePage,
      read('pages/addresses/index.vue'),
      read('pages/booking/create.vue'),
      read('pages/order/confirm.vue'),
      read('pages/pets/edit.vue'),
    ]

    for (const file of arrowFiles) {
      expect(file).not.toContain('>></text>')
      expect(file).not.toContain('&gt;')
    }
  })

  it('uses visible PNG assets for native tabBar icons', () => {
    const icons = pagesJson.tabBar.list.flatMap((tab: {
      iconPath: string
      selectedIconPath: string
    }) => [tab.iconPath, tab.selectedIconPath])

    for (const icon of icons) {
      expect(sizeOf(icon)).toBeGreaterThan(2000)
    }
  })

  it('keeps mini-program critical buttons visibly styled via brand tokens', () => {
    // Brand color is now expressed through the design token (var(--pc-user-primary))
    // rather than a literal hex, so wot components and custom elements stay aligned.
    expect(primaryButton).toContain('var(--pc-user-primary)')
    expect(primaryButton).toContain('loading?: boolean')
    expect(serviceCard).toContain('pc-service-card__action')
    expect(serviceCard).toContain("priceFrom ? '选体型预约' : '立即预约'")
    expect(serviceCard).toContain('var(--pc-user-primary)')
    expect(profilePage).toContain('PcPrimaryButton text="手机号登录"')
    expect(profilePage).toContain('profile-login-card')
    expect(loginPage).toContain('PcPrimaryButton text="登录"')
    expect(registerPage).toContain('PcPrimaryButton text="注册"')
  })

  it('keeps the restored service, community and product visual shells in mp-weixin', () => {
    expect(heroCard).toContain('linear-gradient(135deg, #16877C, #0B3D39)')
    expect(servicesPage).toContain('services-categories__track')
    expect(servicesPage).toContain('linear-gradient(135deg, #F4FFFB 0%, var(--pc-user-surface) 46%, #E7F6F1 100%)')
    expect(communityPage).toContain('community-tags__track')
    expect(communityPage).toContain('community-fab__icon')
    expect(communityPage).toContain('background: var(--pc-user-primary)')
    expect(productsPage).toContain('products-tabs__track')
    expect(productsPage).toContain('products-cart-fab__icon')
    expect(productsPage).toContain('🛒')
    expect(productsPage).toContain('background: var(--pc-user-primary)')
  })

  it('keeps marketing activity pages tolerant of missing association arrays', () => {
    expect(homePage).toContain('activityProductCount')
    expect(homePage).toContain('activity.productNames?.length ?? 0')
    expect(activityPage).toContain('productCount(item)')
    expect(activityPage).toContain('item.productNames?.length ?? 0')
    expect(activityDetailPage).toContain('activity.value?.productNames ?? []')
    expect(activityDetailPage).toContain('activity.value?.serviceNames ?? []')
  })
})
