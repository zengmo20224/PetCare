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
  padding: 20px;
  padding: 20px;
}

.activity-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.activity-card {
  display: flex;
  gap: 12px;
  background: #fff;
  border: 1px solid #E2E9E6;
  border-radius: 16px;
  border-radius: 16px;
  padding: 12px;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

.activity-card__cover {
  width: 108px;
  height: 88px;
  flex-shrink: 0;
  border-radius: 14px;
  overflow: hidden;
  background: #DFF2ED;
}

.activity-card__cover--placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
}

.activity-card__cover--placeholder text {
  font-size: 20px;
  font-weight: 800;
  color: #11796F;
  opacity: 0.34;
}

.activity-card__body {
  flex: 1;
  min-width: 0;
}

.activity-card__title {
  display: block;
  font-size: 16px;
  font-weight: 700;
  color: #19322E;
  color: #19322E;
  margin-bottom: 6px;
}

.activity-card__time {
  display: block;
  font-size: 11px;
  color: #11796F;
  margin-bottom: 6px;
}

.activity-card__desc {
  display: -webkit-box;
  font-size: 14px;
  color: #71817D;
  color: #71817D;
  line-height: 1.6;
  margin-bottom: 8px;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.activity-card__tags {
  display: flex;
  gap: 8px;
}

.activity-card__tag {
  font-size: 11px;
  color: #11796F;
  color: #11796F;
  background: #DFF2ED;
  background: #DFF2ED;
  padding: 2px 8px;
  border-radius: 8px;
}
</style>
