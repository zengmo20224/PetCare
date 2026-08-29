<template>
  <view class="pc-page home-page">
    <!-- 品牌门店卡（V2 改版：Hero 收敛并入门店信息，全应用唯一大面积品牌渐变） -->
    <view class="home-hero">
      <view class="home-hero__glow" />
      <view class="home-hero__row">
        <text class="home-hero__name">萌宠家园 · 上海徐汇店</text>
        <view
          class="home-hero__status"
          :class="storeStatus === 'OPEN' ? 'home-hero__status--open' : 'home-hero__status--closed'"
        >
          <view class="home-hero__dot" />
          <text>{{ storeStatus === 'OPEN' ? '营业中' : '已休息' }}</text>
        </view>
      </view>
      <text class="home-hero__addr">徐汇区漕溪北路 88 号</text>
      <text class="home-hero__slogan">把每一份牵挂，都妥帖安放</text>
    </view>

    <!-- 公告条（demo notice-bar：扁平白底通栏，裸图标无底色方块） -->
    <view
      v-if="latestAnnouncement"
      class="home-notice-bar"
      @tap="goAnnouncementDetail(latestAnnouncement.id)"
    >
      <PcIcon name="megaphone" :size="16" color="#11796F" />
      <text class="home-notice-bar__text">{{ latestAnnouncement.title }}</text>
      <view v-if="hasUnreadAnnouncement" class="home-notice-bar__dot" />
      <PcIcon name="arrow-right" :size="14" color="#B2B2B2" />
    </view>

    <!-- AI 助手入口卡（产品服务速览窗口，置于首屏公告条下方，不让用户找） -->
    <view class="pc-section">
      <view class="home-ai-card" @tap="goAiChat">
        <view class="home-ai-card__icon">
          <PcIcon name="robot" :size="22" color="#11796F" />
        </view>
        <view class="home-ai-card__body">
          <text class="home-ai-card__title">AI 智能助手</text>
          <text class="home-ai-card__hint">营业时间 / 服务价格 / 宠物日常，随时问</text>
        </view>
        <PcIcon name="arrow-right" :size="16" color="#B2B2B2" />
      </view>
    </view>

    <!-- 常用服务宫格（demo grid-4：4列、48px 圆角图标方块） -->
    <view class="pc-section">
      <view class="home-section-title">常用服务</view>
      <view class="home-grid-4">
        <view
          v-for="item in serviceShortcuts"
          :key="item.label"
          class="home-grid-4__item"
          @tap="goServices(item.categoryName)"
        >
          <view class="home-shortcut-icon" :style="{ backgroundColor: item.background }">
            <PcIcon :name="item.icon" :size="24" :color="item.color" />
          </view>
          <text class="home-shortcut-label">{{ item.label }}</text>
          <text class="home-shortcut-hint">{{ item.hint }}</text>
        </view>
      </view>
    </view>

    <!-- 精选好物横向滚动（demo：section-title + more 链接） -->
    <view class="pc-section">
      <view class="home-section-header">
        <text class="home-section-title">精选好物</text>
        <view class="home-section-more" @tap="goProducts">
          <text class="home-section-more-text">逛逛商店</text>
          <PcIcon name="arrow-right" :size="12" color="#11796F" />
        </view>
      </view>
      <PcStatePanel
        :status="productsStatus"
        empty-icon="🛍️"
        empty-text="暂无精选商品"
        empty-hint="店长正在为你挑选好物"
        @retry="loadFeaturedProducts"
      >
        <scroll-view class="home-products-scroll" scroll-x>
          <view class="home-products">
            <PcProductCard
              v-for="item in featuredProducts"
              :key="item.id"
              class="home-product-card"
              :product-id="item.id"
              :name="item.name"
              :price="item.price"
              :cover-url="item.coverUrl"
              :sales-count="item.salesCount"
              @press="goProductDetail(item.id)"
            />
          </view>
        </scroll-view>
      </PcStatePanel>
    </view>

    <!-- 门店活动（demo：section-title + more 链接） -->
    <view class="pc-section">
      <view class="home-section-header">
        <text class="home-section-title">门店活动</text>
        <view class="home-section-more" @tap="goActivities">
          <text class="home-section-more-text">查看全部</text>
          <PcIcon name="arrow-right" :size="12" color="#11796F" />
        </view>
      </view>
      <PcStatePanel
        :status="activitiesStatus"
        empty-icon="🎉"
        empty-text="暂无进行中的活动"
        empty-hint="新活动上线时会在这里通知你"
        @retry="loadRecentActivities"
      >
        <view class="home-activity-list">
          <view
            v-for="act in recentActivities"
            :key="act.id"
            class="home-activity-card"
            @tap="goActivityDetail(act.id)"
          >
            <view class="home-activity-card__img">
              <image v-if="act.coverUrl" :src="assetFullUrl(act.coverUrl)" mode="aspectFill" class="home-activity-card__cover" lazy-load />
              <PcIcon v-else name="sparkle" :size="24" color="#11796F" />
            </view>
            <view class="home-activity-card__body">
              <text class="home-activity-card__title">{{ act.title }}</text>
              <text class="home-activity-card__time">{{ formatActivityTime(act) }}</text>
              <view class="home-activity-card__tags">
                <view v-if="activityProductCount(act) > 0" class="home-pill-tag home-pill-tag--soft">
                  <text>{{ activityProductCount(act) }} 件商品</text>
                </view>
                <view v-if="activityServiceCount(act) > 0" class="home-pill-tag home-pill-tag--warn">
                  <text>{{ activityServiceCount(act) }} 项服务</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </PcStatePanel>
    </view>

    <!-- 社区动态（demo：纯标题） -->
    <view class="pc-section">
      <text class="home-section-title">社区动态</text>
      <PcStatePanel
        :status="postsStatus"
        empty-icon="💬"
        empty-text="社区还很安静"
        empty-hint="来分享第一篇萌宠日常吧"
        @retry="loadRecentPosts"
      >
        <template #empty-action>
          <view class="home-empty-cta" @tap="goCommunityTab">
            <text class="home-empty-cta__text">去逛社区</text>
          </view>
        </template>
        <view>
          <view
            v-for="post in recentPosts"
            :key="post.id"
            class="home-post-mini"
            @tap="goPostDetail(post.id)"
          >
            <text class="home-post-mini__title">{{ post.title }}</text>
            <view class="home-post-mini__meta">
              <view class="home-post-mini__stat">
                <PcIcon name="heart" :size="13" color="#999999" />
                <text>{{ post.likeCount }}</text>
              </view>
              <view class="home-post-mini__stat">
                <PcIcon name="chat" :size="13" color="#999999" />
                <text>{{ post.commentCount }}</text>
              </view>
            </view>
          </view>
        </view>
      </PcStatePanel>
    </view>

    <PcBottomNav current-path="pages/home/index" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import PcStatePanel from '@/components/PcStatePanel.vue'
import PcProductCard from '@/components/PcProductCard.vue'
import PcBottomNav from '@/components/PcBottomNav.vue'
import PcIcon from '@/components/PcIcon.vue'
import { getPosts } from '@/api/community'
import { getActivities } from '@/api/activity'
import { getAnnouncements } from '@/api/notification'
import { getProducts } from '@/api/product'
import { getStoreDetail } from '@/api/store'
import type { PostItem } from '@/types/community'
import type { ActivityItem } from '@/types/activity'
import type { AnnouncementItem } from '@/types/notification'
import type { ProductItem } from '@/types/product'
import { openServiceCategory } from '@/utils/service-navigation'
import { hasUnreadAnnouncements } from '@/utils/announcement-read'
import { assetFullUrl } from '@/utils/asset-url'

const latestAnnouncement = ref<AnnouncementItem | null>(null)
const announcements = ref<AnnouncementItem[]>([])
const hasUnreadAnnouncement = ref(false)
const storeStatus = ref<string>('OPEN')

const serviceShortcuts = [
  { label: '洗护', categoryName: '洗护', hint: '清爽洁净', icon: 'bath', color: '#11796F', background: '#DFF2ED' },
  { label: '美容', categoryName: '美容', hint: '精致造型', icon: 'scissors', color: '#D78A0C', background: '#FFF0D1' },
  { label: '上门照护', categoryName: '上门照护', hint: '省心到家', icon: 'door', color: '#4777C8', background: '#E8F0FE' },
  { label: '安心寄养', categoryName: '寄养', hint: '贴心陪伴', icon: 'paw', color: '#C85B79', background: '#FCE4EC' },
] as const

const recentPosts = ref<PostItem[]>([])
const postsStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')

const recentActivities = ref<ActivityItem[]>([])
const activitiesStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const featuredProducts = ref<ProductItem[]>([])
const productsStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')

async function loadRecentPosts() {
  postsStatus.value = 'loading'

  try {
    const res = await getPosts({ size: 5 })

    if (!res.success || !res.data) {
      postsStatus.value = 'error'
      return
    }

    recentPosts.value = res.data.items
    postsStatus.value = recentPosts.value.length > 0 ? 'success' : 'empty'
  } catch {
    postsStatus.value = 'error'
  }
}

async function loadRecentActivities() {
  activitiesStatus.value = 'loading'

  try {
    const res = await getActivities({ size: 3 })

    if (!res.success || !res.data) {
      activitiesStatus.value = 'error'
      return
    }

    recentActivities.value = res.data.items
    activitiesStatus.value = recentActivities.value.length > 0 ? 'success' : 'empty'
  } catch {
    activitiesStatus.value = 'error'
  }
}

async function loadFeaturedProducts() {
  productsStatus.value = 'loading'
  try {
    const res = await getProducts({ size: 6 })
    if (!res.success || !res.data) {
      productsStatus.value = 'error'
      return
    }
    featuredProducts.value = res.data.items.slice(0, 6)
    productsStatus.value = featuredProducts.value.length > 0 ? 'success' : 'empty'
  } catch {
    productsStatus.value = 'error'
  }
}

function goServices(categoryName: string) {
  openServiceCategory(categoryName)
}

function goProducts() {
  uni.switchTab({ url: '/pages/products/index' })
}

function goCommunityTab() {
  uni.switchTab({ url: '/pages/community/index' })
}

function goProductDetail(id: string) {
  uni.navigateTo({ url: `/pages/products/detail?id=${id}` })
}

function goPostDetail(id: string) {
  uni.navigateTo({ url: `/pages/community/detail?id=${id}` })
}

function goActivities() {
  uni.navigateTo({ url: '/pages/activity/index' })
}

function goAiChat() {
  uni.navigateTo({ url: '/pages/ai/chat' })
}

function goActivityDetail(id: string) {
  uni.navigateTo({ url: `/pages/activity/detail?id=${id}` })
}

function activityProductCount(activity: ActivityItem): number {
  return activity.products?.length ?? activity.productNames?.length ?? 0
}

function activityServiceCount(activity: ActivityItem): number {
  return activity.services?.length ?? activity.serviceNames?.length ?? 0
}

function formatActivityTime(activity: ActivityItem): string {
  const start = formatShortDate(activity.startTime)
  const end = formatShortDate(activity.endTime)
  if (!start && !end) return '长期有效'
  if (!start) return `截至 ${end}`
  if (!end) return `${start} 起`
  return `${start} - ${end}`
}

function formatShortDate(value: string | null): string {
  if (!value) return ''
  return value.replace('T', ' ').slice(5, 16)
}

async function loadAnnouncement() {
  const res = await getAnnouncements(100)
  if (res.success && res.data && res.data.length > 0) {
    announcements.value = res.data
    latestAnnouncement.value = res.data[0]
    hasUnreadAnnouncement.value = hasUnreadAnnouncements(res.data.map(item => item.id))
  }
}

async function loadStoreStatus() {
  const res = await getStoreDetail('1001')
  if (res.success && res.data) {
    storeStatus.value = res.data.status ?? 'OPEN'
  }
}

function loadHomeData() {
  loadRecentPosts()
  loadRecentActivities()
  loadFeaturedProducts()
  loadAnnouncement()
  loadStoreStatus()
}

function goAnnouncementDetail(id: string) {
  uni.navigateTo({ url: `/pages/announcement/detail?id=${id}` })
}

onLoad(loadHomeData)
onShow(loadAnnouncement)
</script>

<style scoped>
.home-page {
  /* hero、store-bar、notice-bar 通栏贴边；其余 section 由下方规则补左右留白。 */
  padding: 0 0;
}

/* 各内容 section 左右留白（hero/notice-bar 通栏不命中） */
.home-page .pc-section {
  padding-left: 32rpx;
  padding-right: 32rpx;
  margin-bottom: 48rpx;
}

/* 第一个 section（常用服务）距 notice-bar 32rpx */
.home-page .pc-section:first-of-type {
  margin-top: 32rpx;
}

/* 横滚商品区：scroll-view 突破容器宽度，负边距对齐 */
.home-page .home-products-scroll {
  width: calc(100% + 64rpx);
  margin-left: -32rpx;
  margin-right: -32rpx;
}

/* 底部留白：H5 端 fixed PcBottomNav 高 128rpx + 安全余量；小程序端用原生 tabBar（系统托起），
   仅留 32rpx 防止内容贴底边缘。 */
/* #ifdef H5 */
.home-page {
  padding-bottom: 192rpx;
}
/* #endif */
/* #ifdef MP-WEIXIN */
.home-page {
  padding-bottom: 32rpx;
}
/* #endif */

/* ─── 品牌门店卡（V2：Hero 收敛并入门店信息，渐变仅此处与钱包余额卡）─── */
.home-hero {
  position: relative;
  overflow: hidden;
  margin: 24rpx 32rpx 0;
  padding: 28rpx 28rpx 24rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #158470, #11796F 46%, #0C4D48);
}

/* 高光装饰（mp-weixin 不支持 ::before，用绝对定位 view） */
.home-hero__glow {
  position: absolute;
  top: -60rpx;
  right: -48rpx;
  width: 220rpx;
  height: 220rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.07);
}

.home-hero__row {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.home-hero__name {
  font-size: 30rpx;
  font-weight: 600;
  color: #FFFFFF;
}

.home-hero__status {
  display: flex;
  align-items: center;
  gap: 8rpx;
  height: 40rpx;
  padding: 0 16rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.16);
  flex-shrink: 0;
}

.home-hero__status text {
  font-size: 20rpx;
  color: #FFFFFF;
}

.home-hero__status--open .home-hero__dot {
  background: #7CE7A2;
}

.home-hero__status--closed .home-hero__dot {
  background: #FFD591;
}

.home-hero__dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: currentColor;
}

.home-hero__addr {
  position: relative;
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.72);
}

.home-hero__slogan {
  position: relative;
  display: block;
  margin-top: 18rpx;
  font-size: 24rpx;
  letter-spacing: 1rpx;
  color: rgba(255, 255, 255, 0.85);
}

/* ─── 公告条（demo notice-bar：扁平白底通栏）─── */
.home-notice-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 32rpx;
  background: #FFFFFF;
  border-bottom: 1rpx solid #E5E5E5;
}

.home-notice-bar__text {
  flex: 1;
  font-size: 26rpx;
  color: #333333;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.home-notice-bar__dot {
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #FA5151;
  flex-shrink: 0;
}

/* ─── 章节标题（demo section-title：16px/600，左对齐）─── */
.home-section-title {
  display: block;
  font-size: 32rpx;
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 24rpx;
}

.home-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.home-section-header .home-section-title {
  margin-bottom: 0;
}

/* more 链接（demo section-title__more：12px/primary，带箭头） */
.home-section-more {
  display: inline-flex;
  align-items: center;
  gap: 4rpx;
  flex-shrink: 0;
}

.home-section-more-text {
  font-size: 24rpx;
  color: #11796F;
  font-weight: 400;
}

.home-section-more:active {
  opacity: 0.6;
}

/* ─── 常用服务宫格（demo grid-4：4列网格、48px 圆角图标方块）─── */
.home-grid-4 {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 24rpx;
}

.home-grid-4__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

.home-shortcut-icon {
  width: 96rpx;
  height: 96rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.home-grid-4__item:active .home-shortcut-icon {
  transform: scale(0.95);
}

.home-shortcut-label {
  font-size: 24rpx;
  color: #333333;
}

.home-shortcut-hint {
  font-size: 20rpx;
  color: #999999;
}

/* ─── 商品横向滚动（首页精选好物：缩到原 3/4）─── */
.home-products {
  display: flex;
  gap: 18rpx;   /* 24rpx × 3/4 */
  padding: 4rpx 40rpx 24rpx 0;
}

.home-product-card {
  width: 285rpx;   /* 380rpx × 3/4 */
  flex-shrink: 0;
}

/* 首页横滚卡片等比缩小 PcProductCard 内部尺寸 */
.home-product-card :deep(.pc-product-card__image-wrap) {
  height: 207rpx;  /* 276rpx × 3/4 */
}

.home-product-card :deep(.pc-product-card__info) {
  padding: 15rpx 15rpx 18rpx;  /* 20rpx × 3/4 */
  gap: 5rpx;
}

.home-product-card :deep(.pc-product-card__name) {
  font-size: 24rpx;  /* 28rpx × 3/4 ≈ 21，取 24 保证可读 */
}

.home-product-card :deep(.pc-product-card__price) {
  margin-top: 6rpx;
  font-size: 28rpx;  /* 32rpx × 3/4 = 24，取 28 保证可读 */
}

/* ─── 门店活动卡（demo activity-card：扁平、小缩略图）─── */
.home-activity-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.home-activity-card {
  display: flex;
  gap: 24rpx;
  padding: 24rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
}

.home-activity-card__img {
  width: 160rpx;
  height: 120rpx;
  flex-shrink: 0;
  border-radius: 8rpx;
  overflow: hidden;
  background: linear-gradient(135deg, #DFF2ED, #E7F6F1);
  display: flex;
  align-items: center;
  justify-content: center;
}

.home-activity-card__cover {
  width: 100%;
  height: 100%;
}

.home-activity-card__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.home-activity-card__title {
  font-size: 28rpx;
  font-weight: 500;
  color: #1A1A1A;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-activity-card__time {
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #999999;
}

.home-activity-card__tags {
  display: flex;
  gap: 8rpx;
  margin-top: 12rpx;
}

/* ─── pill-tag（demo pill-tag：胶囊小标签）─── */
.home-pill-tag {
  display: inline-flex;
  align-items: center;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
}

.home-pill-tag--soft {
  background: #DFF2ED;
  color: #11796F;
}

.home-pill-tag--warn {
  background: #FFF3E5;
  color: #FA9D3B;
}

.home-pill-tag--danger {
  background: #FFE8E8;
  color: #FA5151;
}

/* ─── 社区动态卡（demo post-mini：扁平、纯标题+meta）─── */
.home-post-mini {
  padding: 24rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
}

.home-post-mini + .home-post-mini {
  margin-top: 16rpx;
}

.home-post-mini__title {
  font-size: 28rpx;
  color: #1A1A1A;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.home-post-mini__meta {
  display: flex;
  gap: 32rpx;
  margin-top: 12rpx;
  font-size: 22rpx;
  color: #999999;
}

.home-post-mini__stat {
  display: flex;
  align-items: center;
  gap: 6rpx;
}

/* ─── 空状态引导按钮（V2：圆角/投影与全局按钮统一）─── */
.home-empty-cta {
  margin-top: 8rpx;
  padding: 16rpx 40rpx;
  border-radius: 16rpx;
  background: var(--pc-user-primary);
}

.home-empty-cta:active {
  opacity: 0.85;
}

.home-empty-cta__text {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--pc-user-surface);
}

/* ─── AI 助手入口卡（品牌浅底强化，置于首屏）─── */
.home-ai-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  background: linear-gradient(135deg, #F0F9F6, #E2F3ED);
  border: 1rpx solid #D5EBE3;
  border-radius: 24rpx;
}

.home-ai-card:active {
  opacity: 0.85;
}

.home-ai-card__icon {
  width: 76rpx;
  height: 76rpx;
  border-radius: 20rpx;
  background: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.home-ai-card__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.home-ai-card__title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1A1A1A;
}

.home-ai-card__hint {
  font-size: 22rpx;
  color: #999999;
}
</style>
