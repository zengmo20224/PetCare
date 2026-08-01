<template>
  <view class="pc-page activity-detail">
    <PcStatePanel
      :status="pageStatus"
      empty-text="活动不存在"
      @retry="loadDetail"
    >
      <template v-if="activity">
        <view v-if="activity.coverUrl" class="activity-detail__cover">
          <image class="activity-detail__cover-img" :src="assetFullUrl(activity.coverUrl)" mode="aspectFill" />
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
  padding: 20px;
  padding: 20px;
}

.activity-detail__cover {
  width: 100%;
  height: 190px;
  margin-bottom: 14px;
  border-radius: 20px;
  overflow: hidden;
  background: #DFF2ED;
}

.activity-detail__cover-img {
  width: 100%;
  height: 100%;
}

.activity-detail__card {
  background: #fff;
  border: 1px solid #E2E9E6;
  border-radius: 16px;
  border-radius: 16px;
  padding: 20px;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

.activity-detail__title {
  display: block;
  font-size: 24px;
  font-weight: 700;
  color: #19322E;
  color: #19322E;
  margin-bottom: 8px;
}

.activity-detail__time {
  display: block;
  margin-bottom: 12px;
  font-size: 12px;
  color: #11796F;
}

.activity-detail__desc {
  display: block;
  font-size: 14px;
  color: #71817D;
  color: #71817D;
  line-height: 1.8;
  margin-bottom: 16px;
}

.activity-detail__section {
  margin-bottom: 16px;
}

.activity-detail__section-title {
  display: block;
  font-size: 14px;
  font-weight: 600;
  color: #19322E;
  color: #19322E;
  margin-bottom: 8px;
}

.activity-detail__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.activity-detail__chip {
  font-size: 11px;
  color: #11796F;
  color: #11796F;
  background: #DFF2ED;
  background: #DFF2ED;
  padding: 4px 12px;
  border-radius: 12px;
}

.activity-detail__cards {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.activity-detail__related-card {
  display: flex;
  gap: 10px;
  padding: 10px;
  border: 1px solid #E2E9E6;
  border-radius: 14px;
  background: #F9FCFB;
}

.activity-detail__related-card:active {
  transform: scale(0.99);
}

.activity-detail__related-img {
  width: 72px;
  height: 58px;
  flex-shrink: 0;
  border-radius: 12px;
  overflow: hidden;
  background: #DFF2ED;
}

.activity-detail__related-img--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.activity-detail__related-img--placeholder text {
  font-size: 14px;
  font-weight: 800;
  color: #11796F;
  opacity: 0.38;
}

.activity-detail__related-body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 4px;
}

.activity-detail__related-title {
  font-size: 14px;
  font-weight: 700;
  color: #19322E;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.activity-detail__related-price {
  font-size: 13px;
  font-weight: 800;
  color: #E97951;
}

.activity-detail__related-meta {
  font-size: 12px;
  color: #71817D;
}
</style>
