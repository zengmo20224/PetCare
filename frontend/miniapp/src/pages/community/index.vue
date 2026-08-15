<template>
  <view class="pc-page community-page">
    <!-- Hero 温情宣传栏（demo 风格：90px 通栏纯文字） -->
    <PcHeroStrip
      title="把每一份日常，都分享成温暖的陪伴"
      highlight="日常"
      desc="记录成长 · 交换经验 · 让爱宠心意被看见。"
    />

    <!-- 搜索栏（demo search-bar） -->
    <view class="community-search">
      <view class="community-search__input">
        <PcIcon name="search" :size="16" color="#B2B2B2" />
        <input
          class="community-search__field"
          type="text"
          v-model="keyword"
          placeholder="搜索帖子标题或内容"
          placeholder-class="community-search__ph"
          @confirm="handleSearch"
        />
      </view>
      <view class="community-search__btn" @tap="handleSearch">
        <text class="community-search__btn-text">搜索</text>
      </view>
    </view>

    <!-- Tag Tabs (horizontal scroll) -->
    <scroll-view class="community-tags" scroll-x>
      <view class="community-tags__track">
        <view
          class="community-tag"
          :class="{ 'community-tag--active': activeTag === '' }"
          @tap="switchTag('')"
        >
          <text>全部</text>
        </view>
        <view
          v-for="tag in popularTags"
          :key="tag.name"
          class="community-tag"
          :class="{ 'community-tag--active': activeTag === tag.name }"
          @tap="switchTag(tag.name)"
        >
          <text>#{{ tag.name }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- Single-column feed (酷安 style) -->
    <PcStatePanel
      :status="listStatus"
      :empty-icon="emptyIcon"
      :empty-text="emptyText"
      :empty-hint="emptyHint"
      @retry="loadPosts"
    >
      <template v-if="!hasFilter" #empty-action>
        <view class="community-empty-cta" @tap="goCreatePost">
          <text class="community-empty-cta__text">发第一篇帖子</text>
        </view>
      </template>
      <view class="feed-list">
        <view
          v-for="post in posts"
          :key="post.id"
          class="feed-card"
          @tap="goDetail(post.id)"
        >
          <!-- Author row -->
          <view class="feed-card__author">
            <view class="feed-card__avatar">
              <image v-if="post.authorAvatar" class="feed-card__avatar-img" :src="assetFullUrl(post.authorAvatar)" mode="aspectFill" />
              <text v-else class="feed-card__avatar-initial">{{ (post.authorName || '?').charAt(0) }}</text>
            </view>
            <text class="feed-card__author-name">{{ post.authorName || '匿名用户' }}</text>
            <text class="feed-card__time">{{ formatTime(post.publishTime || post.createTime) }}</text>
          </view>

          <!-- Title -->
          <text class="feed-card__title">{{ post.title }}</text>

          <!-- Content -->
          <text v-if="post.content" class="feed-card__content">{{ post.content }}</text>

          <!-- Tags -->
          <view v-if="post.tags && post.tags.length > 0" class="feed-card__tags">
            <text
              v-for="tag in post.tags"
              :key="tag"
              class="feed-card__tag"
            >#{{ tag }}</text>
          </view>

          <!-- Adaptive image grid (1-6 images) -->
          <view
            v-if="post.imageUrls && post.imageUrls.length > 0"
            class="feed-card__images"
            :class="imagesClass(post.imageUrls.length)"
          >
            <view
              v-for="(img, idx) in displayImages(post.imageUrls)"
              :key="idx"
              class="feed-card__image-wrap"
              :class="{ 'feed-card__image-wrap--more': idx === 5 && post.imageUrls.length > 6 }"
            >
              <image class="feed-card__image" :src="assetFullUrl(img)" mode="aspectFill" lazy-load />
              <text v-if="idx === 5 && post.imageUrls.length > 6" class="feed-card__image-more">+{{ post.imageUrls.length - 6 }}</text>
            </view>
          </view>

          <!-- 分割线（demo feed-divider） -->
          <view class="feed-card__divider" />

          <!-- Footer actions（demo：PcIcon heart/chat/bookmark） -->
          <view class="feed-card__footer">
            <view class="feed-card__action">
              <PcIcon name="heart" :size="16" color="#999999" />
              <text class="feed-card__action-count">{{ post.likeCount }}</text>
            </view>
            <view class="feed-card__action">
              <PcIcon name="chat" :size="16" color="#999999" />
              <text class="feed-card__action-count">{{ post.commentCount }}</text>
            </view>
            <view class="feed-card__action">
              <PcIcon name="bookmark" :size="16" color="#999999" />
              <text class="feed-card__action-count">{{ post.favoriteCount }}</text>
            </view>
          </view>
        </view>
      </view>
    </PcStatePanel>
    <!-- 发帖 FAB（demo 风格：右下角浮动按钮） -->
    <PcFab icon="edit" @press="goCreatePost" />
    <PcBottomNav current-path="pages/community/index" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import PcHeroStrip from '@/components/PcHeroStrip.vue'
import PcIcon from '@/components/PcIcon.vue'
import PcFab from '@/components/PcFab.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import PcBottomNav from '@/components/PcBottomNav.vue'
import { getPosts, getPopularTags } from '@/api/community'
import type { PostItem, TagItem } from '@/types/community'
import { normalizeRouteParam } from '@/utils/route-query'
import { consumeCommunityTagIntent } from '@/utils/community-navigation'
import { assetFullUrl } from '@/utils/asset-url'

const listStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const posts = ref<PostItem[]>([])
const popularTags = ref<TagItem[]>([])
const activeTag = ref('')
const keyword = ref('')

/** Whether the user is filtering by tag or keyword — affects empty-state copy. */
const hasFilter = computed(() => !!activeTag.value || !!keyword.value.trim())

const emptyIcon = computed(() => hasFilter.value ? '🔍' : '🐾')
const emptyText = computed(() => hasFilter.value ? '没有找到相关内容' : '还没有人发帖')
const emptyHint = computed(() =>
  hasFilter.value
    ? '换个关键词或标签试试'
    : '来分享第一篇萌宠日常吧'
)

/** Cap displayed images at 6; the 6th slot shows a "+N" overlay when there are more. */
function displayImages(urls: string[]): string[] {
  return urls.slice(0, 6)
}

/** Pick grid layout class by count, 酷安-style adaptive rules. */
function imagesClass(count: number): string {
  if (count === 1) return 'feed-card__images--single'
  if (count === 2) return 'feed-card__images--double'
  if (count === 4) return 'feed-card__images--four'
  return 'feed-card__images--grid3' // 3, 5, 6 all use 3-column grid
}

function formatTime(time: string): string {
  if (!time) return ''
  const d = new Date(time)
  if (isNaN(d.getTime())) return ''
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

async function loadPopularTags() {
  try {
    const res = await getPopularTags(15)
    if (res.success && res.data) {
      popularTags.value = res.data
    }
  } catch {
    popularTags.value = []
  }
}

async function loadPosts() {
  listStatus.value = 'loading'

  const params: { size: number; keyword?: string; tag?: string } = { size: 30 }
  if (activeTag.value) params.tag = activeTag.value
  if (keyword.value.trim()) params.keyword = keyword.value.trim()

  try {
    const res = await getPosts(params)
    if (!res.success || !res.data) {
      listStatus.value = 'error'
      return
    }

    posts.value = res.data.items
    listStatus.value = posts.value.length > 0 ? 'success' : 'empty'
  } catch {
    listStatus.value = 'error'
  }
}

function switchTag(tagName: string) {
  if (activeTag.value === tagName) return
  activeTag.value = tagName
  loadPosts()
}

function handleSearch() {
  loadPosts()
}

function goDetail(id: string) {
  uni.navigateTo({ url: `/pages/community/detail?id=${id}` })
}

function goCreatePost() {
  uni.navigateTo({ url: '/pages/community-post/create' })
}

function readInitialTag(routeTag?: unknown): string | null {
  const queryTag = normalizeRouteParam(routeTag)
  return queryTag || consumeCommunityTagIntent()
}

onLoad((query) => {
  const tag = readInitialTag(query?.tag)
  if (tag) {
    activeTag.value = tag
  }
  loadPopularTags()
  loadPosts()
})

onShow(() => {
  const tag = consumeCommunityTagIntent()
  if (!tag) return
  activeTag.value = tag
  loadPosts()
})
</script>

<style scoped>
.community-page {
  /* hero/搜索/标签 通栏贴边；feed 由下方规则补留白 */
  padding: 0 0;
}

/* 底部留白：H5 端 fixed PcBottomNav；小程序端用原生 tabBar */
/* #ifdef H5 */
.community-page {
  padding-bottom: 192rpx;
}
/* #endif */
/* #ifdef MP-WEIXIN */
.community-page {
  padding-bottom: 32rpx;
}
/* #endif */

/* ─── 搜索栏（demo search-bar）─── */
.community-search {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 32rpx;
}

.community-search__input {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: #FFFFFF;
  border-radius: 12rpx;
  padding: 14rpx 24rpx;
}

.community-search__field {
  flex: 1;
  font-size: 26rpx;
  color: #333333;
}

.community-search__ph {
  color: #B2B2B2;
  font-size: 26rpx;
}

.community-search__btn {
  background: #11796F;
  border-radius: 8rpx;
  padding: 14rpx 32rpx;
  flex-shrink: 0;
}

.community-search__btn:active {
  opacity: 0.85;
}

.community-search__btn-text {
  color: #FFFFFF;
  font-size: 26rpx;
}

/* ─── 标签 pills（demo tag-pills：胶囊 active 浅青底）─── */
.community-tags {
  white-space: nowrap;
  width: 100%;
  padding: 0 32rpx 24rpx;
  box-sizing: border-box;
}

.community-tags__track {
  display: inline-flex;
  gap: 16rpx;
}

.community-tag {
  display: inline-flex;
  align-items: center;
  padding: 8rpx 24rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  flex-shrink: 0;
}

.community-tag text {
  color: #666666;
  font-size: 24rpx;
  font-weight: 400;
}

/* demo active：浅青底 #DFF2ED + 青绿字 #11796F（不是实心底） */
.community-tag--active {
  background: #DFF2ED;
}

.community-tag--active text {
  color: #11796F;
}

/* ─── 帖子信息流（demo feed：左右 32rpx 留白）─── */
.feed-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  padding: 0 32rpx;
}

/* 帖子卡（demo feed-item：8px 圆角、无阴影） */
.feed-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 28rpx;
}

/* 作者行（demo feed-head） */
.feed-card__author {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 16rpx;
}

.feed-card__avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #DFF2ED;
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.feed-card__avatar-img {
  width: 100%;
  height: 100%;
}

.feed-card__avatar-initial {
  font-size: 28rpx;
  color: #11796F;
  font-weight: 600;
}

.feed-card__author-name {
  font-size: 26rpx;
  font-weight: 500;
  color: #333333;
  flex: 1;
}

.feed-card__time {
  font-size: 22rpx;
  color: #999999;
}

/* 标题（demo feed-title：15px/600） */
.feed-card__title {
  display: block;
  width: 100%;
  font-size: 30rpx;
  font-weight: 600;
  color: #1A1A1A;
  margin-bottom: 8rpx;
  line-height: 1.4;
}

/* 内容（demo feed-content：13px/3行省略） */
.feed-card__content {
  display: block;
  width: 100%;
  font-size: 26rpx;
  color: #666666;
  line-height: 1.6;
  margin-bottom: 16rpx;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 标签（demo pill-tag--soft：999rpx 胶囊 浅青底） */
.feed-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 20rpx;
}

.feed-card__tag {
  display: inline-flex;
  align-items: center;
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: #DFF2ED;
  color: #11796F;
  font-size: 22rpx;
}

/* 图片网格（保留自适应规则） */
.feed-card__images {
  margin-bottom: 20rpx;
  gap: 8rpx;
}

.feed-card__images--single {
  display: flex;
}

.feed-card__images--single .feed-card__image-wrap {
  width: 65%;
}

.feed-card__images--double {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.feed-card__images--four {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

.feed-card__images--grid3 {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
}

.feed-card__image-wrap {
  position: relative;
  width: 100%;
  height: 208rpx;
  border-radius: 8rpx;
  overflow: hidden;
  background: #F5F5F5;
}

.feed-card__images--single .feed-card__image-wrap {
  height: 312rpx;
}

.feed-card__image {
  width: 100%;
  height: 100%;
}

.feed-card__image-more {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.5);
  color: #FFFFFF;
  font-size: 36rpx;
  font-weight: 700;
}

/* 分割线（demo feed-divider：独立 1px 线） */
.feed-card__divider {
  height: 1rpx;
  background: #E5E5E5;
  margin: 20rpx 0;
}

/* 操作行（demo feed-actions：三等分间距 + PcIcon 图标） */
.feed-card__footer {
  display: flex;
  justify-content: space-around;
  padding-top: 0;
}

.feed-card__action {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.feed-card__action-count {
  font-size: 24rpx;
  color: #999999;
}

/* ─── 空状态引导按钮 ─── */
.community-empty-cta {
  margin-top: 8rpx;
  padding: 16rpx 40rpx;
  border-radius: 999rpx;
  background: #11796F;
  box-shadow: 0 8px 20px rgba(17, 121, 111, 0.18);
}

.community-empty-cta:active {
  opacity: 0.85;
}

.community-empty-cta__text {
  font-size: 26rpx;
  font-weight: 700;
  color: #FFFFFF;
}
</style>
