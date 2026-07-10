<template>
  <view class="pc-page home-page">
    <!-- 顶部品牌头：wot 设计语言，徽章用 wd-tag -->
    <view class="home-brand">
      <view class="home-brand__info">
        <text class="home-brand__name">PetCare</text>
        <text class="home-brand__location">萌宠家园 · 上海徐汇店</text>
      </view>
      <wd-tag class="home-brand__status" type="primary" plain round>
        <wd-icon name="check-outline" size="12px" /> 营业中
      </wd-tag>
    </view>

    <!-- Hero Section：保留青绿渐变大卡，CTA 用 wot 按钮 -->
    <PcHeroCard
      title="给它安心的照护时间"
      subtitle="洗护、美容与上门照护，为宠物安排专业服务"
    >
      <template #action>
        <wd-button
          class="home-hero__btn"
          type="primary"
          size="large"
          block
          @click="goBooking"
        >
          马上预约
        </wd-button>
      </template>
    </PcHeroCard>

    <!-- Announcement Banner — wot notice-bar -->
    <wd-notice-bar
      v-if="latestAnnouncement"
      class="home-announcement"
      type="warning"
      scrollable
      prefix="warn-bold"
      :text="`社区公告：${latestAnnouncement.title}`"
      @click="goAnnouncementDetail(latestAnnouncement.id)"
    />

    <!-- Quick Service Shortcuts — wot grid 风格的彩色入口 -->
    <view class="pc-section">
      <view class="home-section-heading">
        <text class="home-section-kicker">QUICK ACCESS</text>
        <text class="home-section-title">常用服务</text>
      </view>
      <view class="home-shortcuts">
        <view
          v-for="item in serviceShortcuts"
          :key="item.label"
          class="home-shortcut-card"
          @tap="goServices(item.categoryName)"
        >
          <view class="home-shortcut-item">
            <view class="home-shortcut-icon" :style="{ backgroundColor: item.background }">
              <PcServiceIcon :name="item.icon" :color="item.color" />
            </view>
            <text class="home-shortcut-label">{{ item.label }}</text>
            <text class="home-shortcut-hint">{{ item.hint }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- Featured Products — 精选好物横向滚动 -->
    <view class="pc-section">
      <view class="home-section-header">
        <view class="home-section-heading">
          <text class="home-section-kicker">PET SELECT</text>
          <text class="home-section-title">精选好物</text>
        </view>
        <wd-button class="home-section-more" type="text" size="small" icon="arrow-right" @click="goProducts">逛逛商店</wd-button>
      </view>
      <PcStatePanel :status="productsStatus" empty-text="暂无精选商品" error-message="">
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
              badge="门店精选"
              @tap="goProductDetail(item.id)"
            />
          </view>
        </scroll-view>
      </PcStatePanel>
    </view>

    <!-- Marketing Activities — wot card 风格 -->
    <view class="pc-section">
      <view class="home-section-header">
        <view class="home-section-heading">
          <text class="home-section-kicker">SPECIAL OFFERS</text>
          <text class="home-section-title">门店活动</text>
        </view>
        <wd-button class="home-section-more" type="text" size="small" icon="arrow-right" @click="goActivities">查看全部</wd-button>
      </view>
      <PcStatePanel :status="activitiesStatus" empty-text="暂无活动" error-message="">
        <view class="home-posts">
          <wd-card
            v-for="act in recentActivities"
            :key="act.id"
            class="home-activity-card"
            @click="goActivityDetail(act.id)"
          >
            <view class="home-activity-item">
              <image v-if="act.coverUrl" class="home-activity-cover" :src="fullImageUrl(act.coverUrl)" mode="aspectFill" lazy-load />
              <view v-else class="home-activity-cover home-activity-cover--placeholder">
                <text>活动</text>
              </view>
              <view class="home-activity-body">
                <text class="home-post-title">{{ act.title }}</text>
                <text class="home-activity-time">{{ formatActivityTime(act) }}</text>
                <view class="home-post-meta">
                  <wd-tag v-if="activityProductCount(act) > 0" type="primary" plain size="small">{{ activityProductCount(act) }} 件商品</wd-tag>
                  <wd-tag v-if="activityServiceCount(act) > 0" type="warning" plain size="small">{{ activityServiceCount(act) }} 项服务</wd-tag>
                </view>
              </view>
            </view>
          </wd-card>
        </view>
      </PcStatePanel>
    </view>

    <!-- Recent Community Posts — wot card 风格 -->
    <view class="pc-section">
      <view class="home-section-heading">
        <text class="home-section-kicker">COMMUNITY</text>
        <text class="home-section-title">社区动态</text>
      </view>
      <PcStatePanel :status="postsStatus" empty-text="暂无动态" error-message="">
        <view class="home-posts">
          <wd-card
            v-for="post in recentPosts"
            :key="post.id"
            class="home-post-card"
            @click="goPostDetail(post.id)"
          >
            <text class="home-post-title">{{ post.title }}</text>
            <view class="home-post-meta">
              <view class="home-post-stat">
                <wd-icon name="heart" size="13px" color="#71817D" />
                <text>{{ post.likeCount }}</text>
              </view>
              <view class="home-post-stat">
                <wd-icon name="chat" size="13px" color="#71817D" />
                <text>{{ post.commentCount }}</text>
              </view>
            </view>
          </wd-card>
        </view>
      </PcStatePanel>
    </view>

    <!-- AI Status -->
    <view class="pc-section">
      <PcBlockedFeature title="智能助手暂未开放" reason="AI 能力正在开发中，敬请期待" />
    </view>
    <PcBottomNav current-path="pages/home/index" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import PcHeroCard from '@/components/PcHeroCard.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import PcBlockedFeature from '@/components/PcBlockedFeature.vue'
import PcProductCard from '@/components/PcProductCard.vue'
import PcBottomNav from '@/components/PcBottomNav.vue'
import PcServiceIcon from '@/components/PcServiceIcon.vue'
import { getPosts } from '@/api/community'
import { getActivities } from '@/api/activity'
import { getAnnouncements } from '@/api/notification'
import { getProducts } from '@/api/product'
import type { PostItem } from '@/types/community'
import type { ActivityItem } from '@/types/activity'
import type { AnnouncementItem } from '@/types/notification'
import type { ProductItem } from '@/types/product'
import { openAllServices, openServiceCategory } from '@/utils/service-navigation'
import { hasUnreadAnnouncements } from '@/utils/announcement-read'

const latestAnnouncement = ref<AnnouncementItem | null>(null)
const announcements = ref<AnnouncementItem[]>([])
const hasUnreadAnnouncement = ref(false)
const API_BASE = import.meta.env.VITE_API_BASE_URL || ''

const serviceShortcuts = [
  { label: '洗护', categoryName: '洗护', hint: '清爽洁净', icon: 'bath', color: '#11796F', background: '#DFF2ED' },
  { label: '美容', categoryName: '美容', hint: '精致造型', icon: 'groom', color: '#D78A0C', background: '#FFF0D1' },
  { label: '上门照护', categoryName: '上门照护', hint: '省心到家', icon: 'home', color: '#4777C8', background: '#E8F0FE' },
  { label: '安心寄养', categoryName: '寄养', hint: '贴心陪伴', icon: 'foster', color: '#C85B79', background: '#FCE4EC' },
] as const

const recentPosts = ref<PostItem[]>([])
const postsStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')

const recentActivities = ref<ActivityItem[]>([])
const activitiesStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const featuredProducts = ref<ProductItem[]>([])
const productsStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')

async function loadRecentPosts() {
  postsStatus.value = 'loading'

  const res = await getPosts({ size: 5 })

  if (!res.success || !res.data) {
    postsStatus.value = 'error'
    return
  }

  recentPosts.value = res.data.items
  postsStatus.value = recentPosts.value.length > 0 ? 'success' : 'empty'
}

async function loadRecentActivities() {
  activitiesStatus.value = 'loading'

  const res = await getActivities({ size: 3 })

  if (!res.success || !res.data) {
    activitiesStatus.value = 'error'
    return
  }

  recentActivities.value = res.data.items
  activitiesStatus.value = recentActivities.value.length > 0 ? 'success' : 'empty'
}

async function loadFeaturedProducts() {
  productsStatus.value = 'loading'
  const res = await getProducts({ size: 6 })
  if (!res.success || !res.data) {
    productsStatus.value = 'error'
    return
  }
  featuredProducts.value = res.data.items.slice(0, 6)
  productsStatus.value = featuredProducts.value.length > 0 ? 'success' : 'empty'
}

function goBooking() {
  openAllServices()
}

function goServices(categoryName: string) {
  openServiceCategory(categoryName)
}

function goProducts() {
  uni.switchTab({ url: '/pages/products/index' })
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

function goActivityDetail(id: string) {
  uni.navigateTo({ url: `/pages/activity/detail?id=${id}` })
}

function activityProductCount(activity: ActivityItem): number {
  return activity.products?.length ?? activity.productNames?.length ?? 0
}

function activityServiceCount(activity: ActivityItem): number {
  return activity.services?.length ?? activity.serviceNames?.length ?? 0
}

function fullImageUrl(url: string | null): string {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return API_BASE + url
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

function loadHomeData() {
  loadRecentPosts()
  loadRecentActivities()
  loadFeaturedProducts()
  loadAnnouncement()
}

function goAnnouncementDetail(id: string) {
  uni.navigateTo({ url: `/pages/announcement/detail?id=${id}` })
}

onLoad(loadHomeData)
onShow(loadAnnouncement)
</script>

<style scoped>
.home-page {
  /* 底部留白避开 fixed PcBottomNav（64px 高 + 安全余量），与其他 tab 页一致 */
  padding: 20px 20px 96px;
}

/* ─── 顶部品牌头 ─── */
.home-brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 2px 2px 18px;
}

.home-brand__info {
  display: flex;
  flex-direction: column;
}

.home-brand__name {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.5px;
  color: #0C4D48;
}

.home-brand__location {
  font-size: 11px;
  color: #71817D;
}

.home-brand__status {
  flex-shrink: 0;
}

/* ─── wot 公告条 ─── */
.home-announcement {
  margin-top: 14px;
  border-radius: 20px;
  overflow: hidden;
}

/* ─── Hero 按钮 ─── */
.home-hero__btn {
  margin-top: 16px;
  align-self: stretch;
}

/* ─── 章节标题 ─── */
.home-section-title {
  display: block;
  font-size: 16px;
  font-weight: 800;
  color: #19322E;
  margin-bottom: 12px;
}

.home-section-heading {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.home-section-kicker {
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 1.4px;
  color: #E97951;
}

.home-section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.home-section-header .home-section-title {
  margin-bottom: 0;
}

.home-section-more {
  font-size: 10px;
  font-weight: 700;
  flex-shrink: 0;
  line-height: 1.4;
  margin-top: 2px;
}

/* ─── 常用服务快捷入口（保留 PetCare 标志性彩色单字方块）─── */
.home-shortcuts {
  display: flex;
  align-items: stretch;
  padding: 12px 8px;
  border: 1px solid rgba(226, 233, 230, 0.8);
  border-radius: 20px;
  background: #FFFFFF;
  box-shadow: 0 8px 24px rgba(25, 50, 46, 0.08);
}

.home-shortcut-card {
  flex: 1;
  min-width: 0;
}

.home-shortcut-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 0;
  width: 100%;
  min-height: 96px;
  padding: 4px 2px;
}

.home-shortcut-icon {
  width: 52px;
  height: 52px;
  margin-bottom: 4px;
  border-radius: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.7);
}

.home-shortcut-item:active .home-shortcut-icon {
  transform: translateY(2px) scale(0.96);
}

.home-shortcut-label {
  max-width: 100%;
  font-size: 11px;
  font-weight: 700;
  color: #19322E;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.home-shortcut-hint {
  max-width: 100%;
  font-size: 9px;
  color: #71817D;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* ─── 商品横向滚动 ─── */
.home-products-scroll {
  width: calc(100% + 20px);
}

.home-products {
  display: flex;
  gap: 12px;
  padding: 2px 20px 12px 0;
}

.home-product-card {
  width: 190px;
  flex-shrink: 0;
}

/* ─── wot card 列表通用 ─── */
.home-posts {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.home-activity-card,
.home-post-card {
  margin: 0;
  border-radius: 16px;
  overflow: hidden;
}

/* 给 wot 卡片内容区足够内边距，避免内容贴边显得狭窄 */
.home-activity-card :deep(.wd-card__content),
.home-post-card :deep(.wd-card__content) {
  padding: 18px 16px;
}

/* ─── 活动卡片内容 ─── */
.home-activity-item {
  display: flex;
  gap: 14px;
  align-items: center;
}

.home-activity-cover {
  width: 108px;
  height: 88px;
  flex-shrink: 0;
  border-radius: 14px;
  overflow: hidden;
  background: #DFF2ED;
}

.home-activity-cover--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.home-activity-cover--placeholder text {
  font-size: 20px;
  font-weight: 800;
  color: #11796F;
  opacity: 0.36;
}

.home-activity-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.home-activity-time {
  font-size: 11px;
  color: #71817D;
}

/* ─── 社区帖子卡片内容 ─── */
.home-post-title {
  font-size: 15px;
  font-weight: 600;
  line-height: 1.5;
  color: #19322E;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.home-post-meta {
  display: flex;
  gap: 18px;
  margin-top: 10px;
}

.home-post-stat {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: #71817D;
}
</style>
