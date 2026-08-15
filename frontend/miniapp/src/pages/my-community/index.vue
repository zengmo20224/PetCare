<template>
  <view class="pc-page my-community-page">
    <PcPageHeader title="我的社区" />

    <PcStatePanel v-if="!isLoggedIn" status="unauthorized" />

    <template v-else>
      <!-- Tab Bar -->
      <view class="mc-tabs">
        <view
          class="mc-tab"
          :class="{ 'mc-tab--active': activeTab === 'posts' }"
          @tap="switchTab('posts')"
        >
          <text class="mc-tab__text">我的帖子</text>
        </view>
        <view
          class="mc-tab"
          :class="{ 'mc-tab--active': activeTab === 'liked' }"
          @tap="switchTab('liked')"
        >
          <text class="mc-tab__text">我的点赞</text>
        </view>
        <view
          class="mc-tab"
          :class="{ 'mc-tab--active': activeTab === 'favorited' }"
          @tap="switchTab('favorited')"
        >
          <text class="mc-tab__text">我的收藏</text>
        </view>
      </view>

      <!-- Post List -->
      <PcStatePanel
        :status="listStatus"
        :empty-text="emptyText"
        @retry="loadData"
      >
        <view class="mc-list">
          <view
            v-for="post in posts"
            :key="post.id"
            class="mc-card"
            @tap="goDetail(post.id)"
          >
            <view class="mc-card__cover">
              <image v-if="post.imageUrls && post.imageUrls.length > 0" class="mc-card__cover-img" :src="assetFullUrl(post.imageUrls[0])" mode="aspectFill" />
            </view>

            <view class="mc-card__body">
              <text class="mc-card__title">{{ post.title }}</text>
              <text class="mc-card__content">{{ post.content }}</text>

              <view class="mc-card__tags" v-if="post.tags && post.tags.length > 0">
                <text
                  v-for="tag in post.tags"
                  :key="tag"
                  class="mc-card__tag"
                >#{{ tag }}</text>
              </view>

              <view class="mc-card__meta">
                <view class="mc-card__stat">
                  <text>{{ post.likeCount }}</text>
                </view>
                <view class="mc-card__stat">
                  <text>{{ post.commentCount }}</text>
                </view>
                <view class="mc-card__stat">
                  <text>{{ post.favoriteCount }}</text>
                </view>
                <text class="mc-card__date">{{ formatDate(post.publishTime || post.createTime) }}</text>
                <view
                  v-if="activeTab === 'posts'"
                  class="mc-card__delete"
                  @tap.stop="handleDelete(post.id)"
                >
                  <text class="mc-card__delete-text">删除</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </PcStatePanel>
    </template>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import { getMyPosts, getMyLikedPosts, getMyFavoritedPosts, deletePost } from '@/api/community'
import { useUserStore } from '@/store/user'
import type { PostItem } from '@/types/community'
import { assetFullUrl } from '@/utils/asset-url'

const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)

const activeTab = ref<'posts' | 'liked' | 'favorited'>('posts')
const listStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const posts = ref<PostItem[]>([])

const emptyText = computed(() => {
  if (activeTab.value === 'posts') return '还没有发布过帖子'
  if (activeTab.value === 'liked') return '还没有点赞过帖子'
  return '还没有收藏过帖子'
})

function formatDate(iso: string): string {
  if (!iso) return ''
  const d = new Date(iso)
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${month}-${day}`
}

async function loadData() {
  listStatus.value = 'loading'

  let res
  const params = { size: 30 }
  if (activeTab.value === 'posts') {
    res = await getMyPosts(params)
  } else if (activeTab.value === 'liked') {
    res = await getMyLikedPosts(params)
  } else {
    res = await getMyFavoritedPosts(params)
  }

  if (!res.success || !res.data) {
    listStatus.value = 'error'
    return
  }

  posts.value = res.data.items
  listStatus.value = posts.value.length > 0 ? 'success' : 'empty'
}

function switchTab(tab: 'posts' | 'liked' | 'favorited') {
  if (activeTab.value === tab) return
  activeTab.value = tab
  loadData()
}

function goDetail(id: string) {
  uni.navigateTo({ url: `/pages/community/detail?id=${id}` })
}

function handleDelete(postId: string) {
  uni.showModal({
    title: '确认删除',
    content: '删除后不可恢复，确定删除这篇帖子吗？',
    success: async (res) => {
      if (!res.confirm) return
      const deleteRes = await deletePost(postId)
      if (deleteRes.success) {
        uni.showToast({ title: '已删除', icon: 'success' })
        loadData()
      }
    },
  })
}

loadData()
</script>

<style scoped>
.my-community-page {
  padding: 40rpx;
}

.mc-tabs {
  display: flex;
  gap: 2rpx;
  background: var(--pc-user-line);
  border-radius: 24rpx;
  overflow: hidden;
  margin-bottom: 32rpx;
}

.mc-tab {
  flex: 1;
  padding: 24rpx 0;
  background: var(--pc-user-surface);
  display: flex;
  align-items: center;
  justify-content: center;
}

.mc-tab--active {
  background: var(--pc-user-primary);
}

.mc-tab__text {
  font-size: 28rpx;
  color: var(--pc-user-muted);
  font-weight: 500;
}

.mc-tab--active .mc-tab__text {
  color: var(--pc-user-surface);
  font-weight: 700;
}

.mc-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.mc-card {
  background: var(--pc-user-surface);
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
  display: flex;
  flex-direction: row;
  min-height: 200rpx;
}

.mc-card__cover {
  width: 200rpx;
  height: 200rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--pc-user-surface);
  border-radius: 16rpx;
  overflow: hidden;
}

.mc-card__cover-img {
  width: 100%;
  height: 100%;
}

.mc-card__body {
  flex: 1;
  padding: 20rpx 24rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  min-width: 0;
}

.mc-card__title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--pc-user-ink);
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 8rpx;
}

.mc-card__content {
  font-size: 22rpx;
  color: var(--pc-user-muted);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 12rpx;
}

.mc-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-bottom: 12rpx;
}

.mc-card__tag {
  font-size: 22rpx;
  color: var(--pc-user-primary);
  background: rgba(43, 122, 120, 0.08);
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
}

.mc-card__meta {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.mc-card__stat text {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.mc-card__date {
  font-size: 22rpx;
  color: var(--pc-user-muted);
  margin-left: auto;
}

.mc-card__delete {
  padding: 4rpx 16rpx;
  background: rgba(224, 80, 80, 0.1);
  border-radius: 8rpx;
}

.mc-card__delete-text {
  font-size: 22rpx;
  color: #e05050;
}
</style>
