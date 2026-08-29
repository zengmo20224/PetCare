<template>
  <view class="pc-page activity-detail">
    <PcStatePanel
      :status="pageStatus"
      empty-text="活动不存在"
      @retry="loadDetail"
    >
      <template v-if="activity">
        <view v-if="activity.coverUrl" class="activity-detail__cover">
          <image class="activity-detail__cover-img" :src="assetFullUrl(activity.coverUrl)" mode="widthFix" />
        </view>
        <view class="activity-detail__card">
          <text class="activity-detail__title">{{ activity.title }}</text>
          <text class="activity-detail__time">{{ formatActivityTime(activity) }}</text>
          <text v-if="activity.description" class="activity-detail__desc">{{ activity.description }}</text>
          <view v-if="productCards.length > 0" class="activity-detail__section">
            <text class="activity-detail__section-title">参与商品</text>
            <view class="activity-detail__cards">
              <view
                v-for="item in productCards"
                :key="item.id"
                class="activity-detail__related-card"
                @tap="goProductDetail(item.id)"
              >
                <image v-if="item.coverUrl" class="activity-detail__related-img" :src="assetFullUrl(item.coverUrl)" mode="aspectFill" />
                <view v-else class="activity-detail__related-img activity-detail__related-img--placeholder">
                  <text>商品</text>
                </view>
                <view class="activity-detail__related-body">
                  <text class="activity-detail__related-title">{{ item.name }}</text>
                  <text class="activity-detail__related-price">{{ formatYuan(item.price) }}</text>
                </view>
              </view>
            </view>
          </view>
          <view v-else-if="productNames.length > 0" class="activity-detail__section">
            <text class="activity-detail__section-title">参与商品</text>
            <view class="activity-detail__chips">
              <text v-for="name in productNames" :key="name" class="activity-detail__chip">{{ name }}</text>
            </view>
          </view>
          <view v-if="serviceCards.length > 0" class="activity-detail__section">
            <text class="activity-detail__section-title">参与服务</text>
            <view class="activity-detail__cards">
              <view
                v-for="item in serviceCards"
                :key="item.id"
                class="activity-detail__related-card"
                @tap="goServiceDetail(item.id)"
              >
                <image v-if="item.coverUrl" class="activity-detail__related-img" :src="assetFullUrl(item.coverUrl)" mode="aspectFill" />
                <view v-else class="activity-detail__related-img activity-detail__related-img--placeholder">
                  <text>服务</text>
                </view>
                <view class="activity-detail__related-body">
                  <text class="activity-detail__related-title">{{ item.name }}</text>
                  <text class="activity-detail__related-meta">
                    {{ serviceModeLabel(item.serviceMode) }} · {{ formatDuration(item.durationMinutes) }} · {{ formatYuan(item.price) }}
                  </text>
                </view>
              </view>
            </view>
          </view>
          <view v-else-if="serviceNames.length > 0" class="activity-detail__section">
            <text class="activity-detail__section-title">参与服务</text>
            <view class="activity-detail__chips">
              <text v-for="name in serviceNames" :key="name" class="activity-detail__chip">{{ name }}</text>
            </view>
          </view>
        </view>
      </template>
    </PcStatePanel>
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PcStatePanel from '@/components/PcStatePanel.vue'
import { getActivityDetail } from '@/api/activity'
import type { ActivityItem } from '@/types/activity'
import { formatDuration, formatYuan } from '@/utils/format'
import { normalizeRouteParam } from '@/utils/route-query'
import { assetFullUrl } from '@/utils/asset-url'

const activity = ref<ActivityItem | null>(null)
const pageStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const currentActivityId = ref('')
const productCards = computed(() => activity.value?.products ?? [])
const serviceCards = computed(() => activity.value?.services ?? [])
const productNames = computed(() => activity.value?.productNames ?? [])
const serviceNames = computed(() => activity.value?.serviceNames ?? [])

async function loadDetail(routeId?: unknown) {
  const id = normalizeRouteParam(routeId ?? currentActivityId.value)

  if (!id) {
    activity.value = null
    pageStatus.value = 'empty'
    return
  }

  currentActivityId.value = id
  pageStatus.value = 'loading'
  const res = await getActivityDetail(id)

  if (!res.success || !res.data) {
    activity.value = null
    pageStatus.value = 'error'
    return
  }

  activity.value = res.data
  pageStatus.value = 'success'
}

onLoad((query) => {
  loadDetail(query?.id)
})

function goProductDetail(id: string) {
  uni.navigateTo({ url: `/pages/products/detail?id=${id}` })
}

function goServiceDetail(id: string) {
  uni.navigateTo({ url: `/pages/services/detail?id=${id}` })
}

function formatActivityTime(item: ActivityItem): string {
  const start = formatShortDate(item.startTime)
  const end = formatShortDate(item.endTime)
  if (!start && !end) return '长期有效'
  if (!start) return `截至 ${end}`
  if (!end) return `${start} 起`
  return `${start} - ${end}`
}

function formatShortDate(value: string | null): string {
  if (!value) return ''
  return value.replace('T', ' ').slice(5, 16)
}

function serviceModeLabel(mode: string): string {
  const map: Record<string, string> = { STORE: '到店', HOME: '上门', BOTH: '到店/上门' }
  return map[mode] ?? mode
}
</script>

<style scoped>
.activity-detail {
  padding: 40rpx;
  padding: 40rpx;
}

.activity-detail__cover {
  width: 100%;
  margin-bottom: 24rpx;
  border-radius: 24rpx;
  overflow: hidden;
  background: #F5F6F6;
}

/* widthFix：封面按原始比例完整展示，不裁切 */
.activity-detail__cover-img {
  width: 100%;
  display: block;
}

.activity-detail__cover-img {
  width: 100%;
  height: 100%;
}

.activity-detail__card {
  background: var(--pc-user-surface);
  border: 1px solid var(--pc-user-line);
  border-radius: 32rpx;
  border-radius: 32rpx;
  padding: 40rpx;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

.activity-detail__title {
  display: block;
  font-size: 48rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
  color: var(--pc-user-ink);
  margin-bottom: 16rpx;
}

.activity-detail__time {
  display: block;
  margin-bottom: 24rpx;
  font-size: 24rpx;
  color: var(--pc-user-primary);
}

.activity-detail__desc {
  display: block;
  font-size: 28rpx;
  color: var(--pc-user-muted);
  color: var(--pc-user-muted);
  line-height: 1.8;
  margin-bottom: 32rpx;
}

.activity-detail__section {
  margin-bottom: 32rpx;
}

.activity-detail__section-title {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: var(--pc-user-ink);
  color: var(--pc-user-ink);
  margin-bottom: 16rpx;
}

.activity-detail__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.activity-detail__chip {
  font-size: 22rpx;
  color: var(--pc-user-primary);
  color: var(--pc-user-primary);
  background: var(--pc-user-soft);
  background: var(--pc-user-soft);
  padding: 8rpx 24rpx;
  border-radius: 24rpx;
}

.activity-detail__cards {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.activity-detail__related-card {
  display: flex;
  gap: 20rpx;
  padding: 20rpx;
  border: 1px solid var(--pc-user-line);
  border-radius: 28rpx;
  background: #F9FCFB;
}

.activity-detail__related-card:active {
  transform: scale(0.99);
}

.activity-detail__related-img {
  width: 144rpx;
  height: 116rpx;
  flex-shrink: 0;
  border-radius: 24rpx;
  overflow: hidden;
  background: var(--pc-user-soft);
}

.activity-detail__related-img--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.activity-detail__related-img--placeholder text {
  font-size: 28rpx;
  font-weight: 800;
  color: var(--pc-user-primary);
  opacity: 0.38;
}

.activity-detail__related-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8rpx;
}

.activity-detail__related-title {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.activity-detail__related-price {
  font-size: 26rpx;
  font-weight: 800;
  color: var(--pc-user-danger);
}

.activity-detail__related-meta {
  font-size: 24rpx;
  color: var(--pc-user-muted);
}
</style>
