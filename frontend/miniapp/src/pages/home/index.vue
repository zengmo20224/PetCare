<template>
  <view class="pc-page home-page">
    <view class="home-brand">
      <view>
        <text class="home-brand__name">PetCare</text>
        <text class="home-brand__location">萌宠家园 · 上海徐汇店</text>
      </view>
      <view class="home-brand__status">
        <view class="home-brand__dot" />
        <text>营业中</text>
      </view>
    </view>

    <!-- Hero Section -->
    <PcHeroCard
      title="给它安心的照护时间"   
      subtitle="洗护、美容与上门照护，为宠物安排专业服务"
    >
      <template #action>
        <wd-button
          class="home-hero__btn"
          type="primary"
          size="large"
          round
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

    <!-- Quick Service Shortcuts -->
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
              <text :style="{ color: item.color }">{{ item.iconText }}</text>
            </view>
            <text class="home-shortcut-label">{{ item.label }}</text>
            <text class="home-shortcut-hint">{{ item.hint }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- Featured Products -->
    <view class="pc-section">
      <view class="home-section-header">
        <view class="home-section-heading">
          <text class="home-section-kicker">PET SELECT</text>
          <text class="home-section-title">精选好物</text>
        </view>
        <wd-button class="home-section-more" type="text" size="small" @click="goProducts">逛逛商店</wd-button>
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

    <!-- Marketing Activities -->
    <view class="pc-section">
      <view class="home-section-header">
        <view class="home-section-heading">
          <text class="home-section-kicker">SPECIAL OFFERS</text>
          <text class="home-section-title">门店活动</text>
        </view>
        <wd-button class="home-section-more" type="text" size="small" @click="goActivities">查看全部</wd-button>
      </view>
      <PcStatePanel :status="activitiesStatus" empty-text="暂无活动" error-message="">
        <view class="home-posts">
          <view
            v-for="act in recentActivities"
            :key="act.id"
            class="home-activity-item"
            @tap="goActivityDetail(act.id)"
          >
            <image v-if="act.coverUrl" class="home-activity-cover" :src="fullImageUrl(act.coverUrl)" mode="aspectFill" />
            <view v-else class="home-activity-cover home-activity-cover--placeholder">
              <text>活动</text>
            </view>
            <view class="home-activity-body">
              <text class="home-post-title">{{ act.title }}</text>
              <text class="home-activity-time">{{ formatActivityTime(act) }}</text>
              <view class="home-post-meta">
                <text v-if="activityProductCount(act) > 0" class="home-post-stat">{{ activityProductCount(act) }} 件商品</text>
                <text v-if="activityServiceCount(act) > 0" class="home-post-stat">{{ activityServiceCount(act) }} 项服务</text>
              </view>
            </view>
          </view>
        </view>
      </PcStatePanel>
    </view>

    <!-- Recent Community Posts -->
    <view class="pc-section">
      <view class="home-section-heading">
        <text class="home-section-kicker">COMMUNITY</text>
        <text class="home-section-title">社区动态</text>
      </view>
      <PcStatePanel :status="postsStatus" empty-text="暂无动态" error-message="">
        <view class="home-posts">
          <view
            v-for="post in recentPosts"
            :key="post.id"
            class="home-post-item"
            @tap="goPostDetail(post.id)"
          >
            <text class="home-post-title">{{ post.title }}</text>
            <view class="home-post-meta">
              <text class="home-post-stat">{{ post.likeCount }} 赞</text>
              <text class="home-post-stat">{{ post.commentCount }} 评论</text>
            </view>
          </view>
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
  { label: '洗护', categoryName: '洗护', hint: '清爽洁净', iconText: '洗', color: '#11796F', background: '#DFF2ED' },
  { label: '美容', categoryName: '美容', hint: '精致造型', iconText: '美', color: '#D78A0C', background: '#FFF0D1' },
  { label: '上门照护', categoryName: '上门照护', hint: '省心到家', iconText: '家', color: '#4777C8', background: '#E8F0FE' },
  { label: '安心寄养', categoryName: '寄养', hint: '贴心陪伴', iconText: '养', color: '#C85B79', background: '#FCE4EC' },
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
  padding: 20px;
  padding: 20px;
}

.home-brand {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 2px 2px 18px;
}

.home-brand > view:first-child {
  display: flex;
  flex-direction: column;
}

.home-brand__name {
  font-size: 20px;
  font-weight: 800;
  letter-spacing: -0.5px;
  color: #0C4D48;
  color: #0C4D48;
}

.home-brand__location {
  font-size: 11px;
  color: #71817D;
  color: #71817D;
}

.home-brand__status {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid #E2E9E6;
  border: 1px solid #E2E9E6;
}

.home-brand__status text {
  font-size: 10px;
  font-weight: 700;
  color: #11796F;
  color: #11796F;
}

.home-brand__dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #11796F;
  background: #11796F;
  box-shadow: 0 0 0 4px rgba(17, 121, 111, 0.12);
}

.home-announcement {
  margin-top: 14px;
  border-radius: 20px;
  overflow: hidden;
}

.home-hero__btn {
  margin-top: 16px;
  align-self: flex-start;
}

.home-section-title {
  display: block;
  font-size: 16px;
  font-weight: 800;
  color: #19322E;
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
}

.home-shortcuts {
  display: flex;
  align-items: stretch;
  padding: 12px 8px;
  border: 1px solid rgba(226, 233, 230, 0.8);
  border-radius: 20px;
  border-radius: 20px;
  background: #FFFFFF;
  box-shadow: 0 8px 24px rgba(25, 50, 46, 0.08);
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
  width: 50px;
  height: 50px;
  margin-bottom: 2px;
  border-radius: 17px;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.7);
}

.home-shortcut-icon text {
  font-size: 18px;
  font-weight: 800;
}

.home-shortcut-item:active .home-shortcut-icon {
  transform: translateY(2px) scale(0.96);
}

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

.home-shortcut-label {
  max-width: 100%;
  font-size: 11px;
  font-weight: 700;
  color: #19322E;
  color: #19322E;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.home-shortcut-hint {
  max-width: 100%;
  font-size: 9px;
  color: #71817D;
  color: #71817D;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.home-posts {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.home-post-item {
  background: #fff;
  border: 1px solid rgba(226, 233, 230, 0.75);
  border-radius: 20px;
  border-radius: 20px;
  padding: 15px 16px;
  box-shadow: 0 8px 24px rgba(25, 50, 46, 0.08);
  box-shadow: 0 8px 24px rgba(25, 50, 46, 0.08);
}

.home-post-title {
  font-size: 14px;
  font-weight: 600;
  color: #19322E;
  color: #19322E;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.home-post-meta {
  display: flex;
  gap: 16px;
  margin-top: 6px;
}

.home-post-stat {
  font-size: 11px;
  color: #71817D;
  color: #71817D;
}

.home-activity-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: #fff;
  border: 1px solid rgba(226, 233, 230, 0.75);
  border-radius: 20px;
  box-shadow: 0 8px 24px rgba(25, 50, 46, 0.08);
}

.home-activity-cover {
  width: 92px;
  height: 74px;
  flex-shrink: 0;
  border-radius: 16px;
  overflow: hidden;
  background: #DFF2ED;
}

.home-activity-cover--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.home-activity-cover--placeholder text {
  font-size: 18px;
  font-weight: 800;
  color: #11796F;
  opacity: 0.36;
}

.home-activity-body {
  flex: 1;
  min-width: 0;
}

.home-activity-time {
  display: block;
  margin-top: 4px;
  font-size: 11px;
  color: #71817D;
}
</style>
