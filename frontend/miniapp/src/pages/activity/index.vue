<template>
  <view class="pc-page activity-page">
    <PcPageHeader title="优惠活动" />

    <PcStatePanel
      :status="listStatus"
      empty-text="暂无活动"
      @retry="loadActivities"
    >
      <view class="activity-list">
        <view
          v-for="item in activities"
          :key="item.id"
          class="activity-card"
          @tap="goDetail(item.id)"
        >
          <image v-if="item.coverUrl" class="activity-card__cover" :src="assetFullUrl(item.coverUrl)" mode="aspectFill" />
          <view v-else class="activity-card__cover activity-card__cover--placeholder">
            <text>活动</text>
          </view>
          <view class="activity-card__body">
            <text class="activity-card__title">{{ item.title }}</text>
            <text class="activity-card__time">{{ formatActivityTime(item) }}</text>
            <text v-if="item.description" class="activity-card__desc">{{ item.description }}</text>
            <view class="activity-card__tags">
              <text v-if="productCount(item) > 0" class="activity-card__tag">
                {{ productCount(item) }} 件商品
              </text>
              <text v-if="serviceCount(item) > 0" class="activity-card__tag">
                {{ serviceCount(item) }} 项服务
              </text>
            </view>
          </view>
        </view>
      </view>
    </PcStatePanel>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import { getActivities } from '@/api/activity'
import type { ActivityItem } from '@/types/activity'
import { assetFullUrl } from '@/utils/asset-url'

const listStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const activities = ref<ActivityItem[]>([])

async function loadActivities() {
  listStatus.value = 'loading'

  const res = await getActivities({ size: 20 })

  if (!res.success || !res.data) {
    listStatus.value = 'error'
    return
  }

  activities.value = res.data.items
  listStatus.value = activities.value.length > 0 ? 'success' : 'empty'
}

function goDetail(id: string) {
  uni.navigateTo({ url: `/pages/activity/detail?id=${id}` })
}

function productCount(item: ActivityItem): number {
  return item.products?.length ?? item.productNames?.length ?? 0
}

function serviceCount(item: ActivityItem): number {
  return item.services?.length ?? item.serviceNames?.length ?? 0
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

loadActivities()
</script>

<style scoped>
.activity-page {
  padding: 40rpx;
  padding: 40rpx;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}

.activity-card {
  display: flex;
  gap: 24rpx;
  background: var(--pc-user-surface);
  border: 1px solid var(--pc-user-line);
  border-radius: 32rpx;
  border-radius: 32rpx;
  padding: 24rpx;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

.activity-card__cover {
  width: 216rpx;
  height: 176rpx;
  flex-shrink: 0;
  border-radius: 28rpx;
  overflow: hidden;
  background: var(--pc-user-soft);
}

.activity-card__cover--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.activity-card__cover--placeholder text {
  font-size: 40rpx;
  font-weight: 800;
  color: var(--pc-user-primary);
  opacity: 0.34;
}

.activity-card__body {
  flex: 1;
  min-width: 0;
}

.activity-card__title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
  color: var(--pc-user-ink);
  margin-bottom: 12rpx;
}

.activity-card__time {
  display: block;
  font-size: 22rpx;
  color: var(--pc-user-primary);
  margin-bottom: 12rpx;
}

.activity-card__desc {
  display: -webkit-box;
  font-size: 28rpx;
  color: var(--pc-user-muted);
  color: var(--pc-user-muted);
  line-height: 1.6;
  margin-bottom: 16rpx;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.activity-card__tags {
  display: flex;
  gap: 16rpx;
}

.activity-card__tag {
  font-size: 22rpx;
  color: var(--pc-user-primary);
  color: var(--pc-user-primary);
  background: var(--pc-user-soft);
  background: var(--pc-user-soft);
  padding: 4rpx 16rpx;
  border-radius: 16rpx;
}
</style>
