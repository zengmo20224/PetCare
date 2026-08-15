<template>
  <view class="pc-page notif-page">
    <PcPageHeader title="消息通知">
      <template #action>
        <view v-if="unreadCount > 0" class="notif-mark-all" @tap="handleMarkAllRead">
          <text class="notif-mark-all__text">全部已读</text>
        </view>
      </template>
    </PcPageHeader>

    <PcStatePanel v-if="!isLoggedIn" status="unauthorized" />

    <PcStatePanel
      v-else
      :status="listStatus"
      empty-icon="🔔"
      empty-text="暂无消息通知"
      @retry="loadData"
    >
      <view class="notif-list">
        <view
          v-for="notif in notifications"
          :key="notif.id"
          class="notif-item"
          :class="{ 'notif-item--unread': !notif.isRead }"
          @tap="goPost(notif)"
        >
          <!-- Avatar -->
          <view class="notif-item__avatar">
            <image v-if="notif.actorAvatar" class="notif-item__avatar-img" :src="assetFullUrl(notif.actorAvatar)" mode="aspectFill" />
            <text v-else class="notif-item__avatar-text">{{ (notif.actorName || '?').charAt(0) }}</text>
          </view>

          <view class="notif-item__body">
            <view class="notif-item__header">
              <text class="notif-item__actor">{{ notif.actorName || '匿名用户' }}</text>
              <text class="notif-item__type">{{ typeLabel(notif.type) }}</text>
            </view>
            <text v-if="notif.content" class="notif-item__content">{{ notif.content }}</text>
            <text class="notif-item__time">{{ formatTime(notif.createTime) }}</text>
          </view>

          <!-- Unread dot -->
          <view v-if="!notif.isRead" class="notif-item__dot" />
        </view>
      </view>
    </PcStatePanel>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import { getNotifications, getUnreadCount, markNotificationRead, markAllNotificationsRead } from '@/api/notification'
import { useUserStore } from '@/store/user'
import type { NotificationItem } from '@/types/notification'
import { assetFullUrl } from '@/utils/asset-url'

const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)

const listStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const notifications = ref<NotificationItem[]>([])
const unreadCount = ref(0)

function typeLabel(type: string): string {
  if (type === 'LIKE') return '赞了你的帖子'
  if (type === 'COMMENT') return '评论了你的帖子'
  if (type === 'FAVORITE') return '收藏了你的帖子'
  return ''
}

function formatTime(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const minutes = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)

  if (minutes < 1) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

async function loadData() {
  listStatus.value = 'loading'

  const res = await getNotifications({ size: 50 })
  if (!res.success || !res.data) {
    listStatus.value = 'error'
    return
  }

  notifications.value = res.data.items
  listStatus.value = notifications.value.length > 0 ? 'success' : 'empty'

  // Refresh unread count
  const countRes = await getUnreadCount()
  if (countRes.success && countRes.data) {
    unreadCount.value = countRes.data.count
  }
}

async function goPost(notif: NotificationItem) {
  // Mark as read
  if (!notif.isRead) {
    await markNotificationRead(notif.id)
    notif.isRead = true
    unreadCount.value = Math.max(0, unreadCount.value - 1)
  }
  if (notif.postId) {
    uni.navigateTo({ url: `/pages/community/detail?id=${notif.postId}` })
  }
}

async function handleMarkAllRead() {
  const res = await markAllNotificationsRead()
  if (res.success) {
    notifications.value.forEach(n => { n.isRead = true })
    unreadCount.value = 0
    uni.showToast({ title: '已全部标记为已读', icon: 'none' })
  }
}

onShow(() => {
  if (isLoggedIn.value) {
    loadData()
  }
})
</script>

<style scoped>
.notif-page {
  padding: 40rpx;
}

.notif-mark-all {
  padding: 12rpx 28rpx;
  background: var(--pc-user-soft);
  border-radius: 28rpx;
}

.notif-mark-all__text {
  font-size: 22rpx;
  color: var(--pc-user-primary);
  font-weight: 600;
}

.notif-list {
  display: flex;
  flex-direction: column;
  gap: 2rpx;
  background: var(--pc-user-line);
  border-radius: 24rpx;
  overflow: hidden;
}

.notif-item {
  display: flex;
  align-items: flex-start;
  gap: 24rpx;
  background: var(--pc-user-surface);
  padding: 28rpx 32rpx;
  position: relative;
}

.notif-item--unread {
  background: rgba(43, 122, 120, 0.03);
}

.notif-item__avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: var(--pc-user-soft);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.notif-item__avatar-img {
  width: 100%;
  height: 100%;
}

.notif-item__avatar-text {
  font-size: 32rpx;
  color: var(--pc-user-primary);
  font-weight: 600;
}

.notif-item__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
  min-width: 0;
}

.notif-item__header {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.notif-item__actor {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--pc-user-ink);
}

.notif-item__type {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.notif-item__content {
  font-size: 22rpx;
  color: var(--pc-user-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.notif-item__time {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.notif-item__dot {
  position: absolute;
  top: 28rpx;
  right: 32rpx;
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: #e05050;
}
</style>
