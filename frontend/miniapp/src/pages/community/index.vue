<template>
  <view class="pc-page community-page">
    <PcPageHeader title="社区" />

    <view class="community-intro">
      <text class="community-intro__kicker">SHARE THE MOMENTS</text>
      <text class="community-intro__title">把它的小小日常，分享成彼此温暖的陪伴</text>
      <text class="community-intro__desc">记录成长、交换经验，也让每一份爱宠心意被看见。</text>
    </view>

    <!-- Search Bar -->
    <view class="community-search">
      <input
        class="community-search__input"
        type="text"
        v-model="keyword"
        placeholder="搜索帖子标题或内容"
        @confirm="handleSearch"
      />
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
      empty-text="暂无社区内容"
      @retry="loadPosts"
    >
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

          <!-- Footer actions -->
          <view class="feed-card__footer">
            <view class="feed-card__action">
              <text>❤ {{ post.likeCount }}</text>
            </view>
            <view class="feed-card__action">
              <text>💬 {{ post.commentCount }}</text>
            </view>
            <view class="feed-card__action">
              <text>🔖 {{ post.favoriteCount }}</text>
            </view>
          </view>
        </view>
      </view>
    </PcStatePanel>
    <view class="community-fab" aria-label="发布帖子" @tap="goCreatePost">
      <text class="community-fab__icon">+</text>
    </view>
    <PcBottomNav current-path="pages/community/index" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import PcPageHeader from '@/components/PcPageHeader.vue'
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
  min-height: 100vh;
  padding: 20px 20px 96px;
  background: #FAF8F3;
}

.community-intro {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-bottom: 18px;
  min-height: 118px;
  padding: 21px 20px;
  border: 1px solid rgba(17, 121, 111, 0.16);
  border-radius: 22px;
  background:
    radial-gradient(circle at 92% 18%, rgba(255, 218, 138, 0.82) 0 38px, transparent 39px),
    linear-gradient(135deg, #F5FFFC 0%, #FFFFFF 46%, #E7F6F1 100%);
  background:
    radial-gradient(circle at 92% 18%, rgba(245, 166, 35, 0.3) 0 38px, transparent 39px),
    linear-gradient(135deg, #fff, #DFF2ED);
  box-shadow: 0 12px 30px rgba(25, 50, 46, 0.08);
}

.community-intro__kicker {
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 1.4px;
  color: #E97951;
  color: #E97951;
}

.community-intro__title {
  max-width: 300px;
  font-size: 20px;
  line-height: 1.35;
  font-weight: 800;
  color: #19322E;
  color: #0C4D48;
}

.community-intro__desc {
  max-width: 310px;
  font-size: 11px;
  color: #5F746F;
  color: #71817D;
}

.community-fab {
  position: fixed;
  right: 20px;
  bottom: 96px;
  z-index: 880;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 54px;
  height: 54px;
  background: #11796F;
  background: linear-gradient(145deg, #16877C, #0B3D39);
  background: linear-gradient(145deg, #16877c, #0C4D48);
  border: 3px solid rgba(255, 255, 255, 0.92);
  border-radius: 50%;
  box-shadow: 0 12px 28px rgba(12, 77, 72, 0.3);
}

.community-fab:active {
  transform: scale(0.93);
}

.community-fab__icon {
  color: #fff;
  font-size: 34px;
  font-weight: 600;
  line-height: 1;
}

/* Search Bar */
.community-search {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.community-search__input {
  flex: 1;
  height: 40px;
  border-radius: 20px;
  background: #fff;
  border: 1px solid #E2E9E6;
  border: 1px solid #E2E9E6;
  padding: 0 16px;
  font-size: 14px;
  color: #19322E;
}

.community-search__btn {
  height: 40px;
  padding: 0 20px;
  border-radius: 20px;
  background: #11796F;
  background: #11796F;
  display: flex;
  align-items: center;
}

.community-search__btn-text {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

/* Tag Tabs */
.community-tags {
  margin-bottom: 12px;
  white-space: nowrap;
  width: 100%;
}

.community-tags__track {
  display: inline-flex;
  gap: 8px;
  min-width: 100%;
  padding-right: 20px;
  box-sizing: border-box;
}

.community-tag {
  display: flex;
  align-items: center;
  padding: 6px 16px;
  border-radius: 20px;
  background: #fff;
  border: 1px solid #E2E9E6;
  border: 1px solid #E2E9E6;
  flex-shrink: 0;
}

.community-tag--active {
  background: #11796F;
  background: #11796F;
  border-color: #11796F;
  border-color: #11796F;
}

.community-tag--active text {
  color: #fff;
}

.community-tag text {
  font-size: 14px;
  color: #5F746F;
  color: #71817D;
}

/* Single-column feed (酷安 style) */
.feed-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.feed-card {
  background: #fff;
  border-radius: 12px;
  padding: 14px 16px;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

/* Author row */
.feed-card__author {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
}

.feed-card__avatar {
  width: 32px;
  height: 32px;
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
  font-size: 14px;
  color: #11796F;
  font-weight: 600;
}

.feed-card__author-name {
  font-size: 14px;
  font-weight: 600;
  color: #19322E;
  flex: 1;
}

.feed-card__time {
  font-size: 11px;
  color: #71817D;
}

/* Title */
.feed-card__title {
  display: block;
  width: 100%;
  font-size: 16px;
  font-weight: 700;
  color: #19322E;
  margin-bottom: 6px;
  line-height: 1.4;
}

/* Content */
.feed-card__content {
  display: block;
  width: 100%;
  font-size: 14px;
  color: #19322E;
  line-height: 1.6;
  margin-bottom: 8px;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-wrap;
  word-break: break-word;
}

/* Tags */
.feed-card__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.feed-card__tag {
  font-size: 12px;
  color: #11796F;
  background: rgba(43, 122, 120, 0.08);
  padding: 3px 8px;
  border-radius: 4px;
}

/* Adaptive image grid */
.feed-card__images {
  margin-bottom: 10px;
  gap: 4px;
}

/* 1 image: large single image */
.feed-card__images--single {
  display: flex;
}

.feed-card__images--single .feed-card__image-wrap {
  width: 65%;
}

/* 2 images: side by side */
.feed-card__images--double {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

/* 4 images: 2x2 */
.feed-card__images--four {
  display: grid;
  grid-template-columns: 1fr 1fr;
}

/* 3/5/6 images: 3-column grid */
.feed-card__images--grid3 {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
}

.feed-card__image-wrap {
  position: relative;
  width: 100%;
  height: 104px;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f5f5;
}

.feed-card__images--single .feed-card__image-wrap {
  height: 156px;
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
  color: #fff;
  font-size: 18px;
  font-weight: 700;
}

/* Footer actions */
.feed-card__footer {
  display: flex;
  gap: 20px;
  padding-top: 6px;
  border-top: 1px solid #E2E9E6;
}

.feed-card__action {
  display: flex;
  align-items: center;
}

.feed-card__action text {
  font-size: 11px;
  color: #71817D;
}
</style>
