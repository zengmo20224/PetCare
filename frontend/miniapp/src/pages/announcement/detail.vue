<template>
  <view class="pc-page announcement-detail">
    <PcPageHeader title="公告详情">
      <template #action>
        <view class="announcement-detail__read-all" @tap="handleMarkAllRead">
          <text>{{ markingAll ? '...' : '一键已读' }}</text>
        </view>
      </template>
    </PcPageHeader>

    <PcStatePanel :status="status" empty-text="暂无公告" @retry="loadDetail">
      <view class="announcement-detail__list" v-if="announcements.length > 0">
        <view
          v-for="item in announcements"
          :key="item.id"
          class="announcement-detail__card"
        >
          <text class="announcement-detail__title">{{ item.title }}</text>
          <text class="announcement-detail__time">{{ formatTime(item.createTime) }}</text>
          <!--
            M3 防存储型 XSS：改用文本插值（{{ }}）渲染公告内容。
            服务端 ContentSanitizer 已对内容做 HTML 转义，前端文本节点再叠加一层防护，
            彻底杜绝 <script>/onerror 等注入执行。
            历史公告若含字面量 < > 会显示为转义后的实体，符合安全预期。
          -->
          <view class="announcement-detail__content">{{ item.content }}</view>
        </view>
      </view>
    </PcStatePanel>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import { getAnnouncements } from '@/api/notification'
import type { AnnouncementItem } from '@/types/notification'
import { markAnnouncementRead, markAllAnnouncementsRead } from '@/utils/announcement-read'
import { normalizeRouteParam } from '@/utils/route-query'

const announcements = ref<AnnouncementItem[]>([])
const status = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const markingAll = ref(false)
const currentAnnouncementId = ref('')

function formatTime(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

/**
 * Fetch the full list of published announcements every time the page is shown,
 * so edits and new posts made in the admin panel are reflected in real time.
 * Deleted announcements are already filtered out by the backend, so the user
 * only ever sees currently-published ones.
 */
async function loadDetail(routeId?: unknown) {
  currentAnnouncementId.value = normalizeRouteParam(routeId)

  status.value = 'loading'
  const res = await getAnnouncements(100)
  if (!res.success || !res.data) {
    announcements.value = []
    status.value = 'error'
    return
  }

  announcements.value = res.data
  if (announcements.value.length > 0) {
    markAnnouncementRead(announcements.value[0].id)
  }
  status.value = announcements.value.length > 0 ? 'success' : 'empty'
}

/** Mark all announcements as read, so the home-page red dot clears. */
async function handleMarkAllRead() {
  if (markingAll.value) return
  markingAll.value = true
  try {
    if (announcements.value.length > 0) {
      markAllAnnouncementsRead(announcements.value.map(item => item.id))
    }
    uni.showToast({ title: '已全部标记为已读', icon: 'none' })
  } finally {
    markingAll.value = false
  }
}

onLoad((query) => {
  loadDetail(query?.id)
})
onShow(() => {
  loadDetail(currentAnnouncementId.value)
})
</script>

<style scoped>
.announcement-detail {
  padding: 40rpx;
}

.announcement-detail__read-all {
  padding: 12rpx 24rpx;
  border-radius: 1998rpx;
  background: var(--pc-user-soft);
}

.announcement-detail__read-all text {
  font-size: 22rpx;
  font-weight: 700;
  color: var(--pc-user-primary);
}

.announcement-detail__list {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.announcement-detail__card {
  background: var(--pc-user-surface);
  border-radius: 32rpx;
  padding: 40rpx 32rpx;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

.announcement-detail__title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
  display: block;
  margin-bottom: 16rpx;
}

.announcement-detail__time {
  font-size: 22rpx;
  color: var(--pc-user-muted);
  display: block;
  margin-bottom: 32rpx;
}

.announcement-detail__content {
  font-size: 28rpx;
  color: var(--pc-user-ink);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
