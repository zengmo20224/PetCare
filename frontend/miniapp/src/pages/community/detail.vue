<template>
  <view class="pc-page community-detail">
    <PcStatePanel
      :status="pageStatus"
      empty-text="帖子不存在"
      @retry="loadDetail"
    >
      <template v-if="post">
        <!-- Post Content -->
        <view class="community-detail__card">
          <!-- Author -->
          <view class="community-detail__author">
            <view class="community-detail__author-avatar">
              <image v-if="post.authorAvatar" class="community-detail__author-img" :src="assetFullUrl(post.authorAvatar)" mode="aspectFill" />
              <text v-else class="community-detail__author-initial">{{ (post.authorName || '?').charAt(0) }}</text>
            </view>
            <text class="community-detail__author-name">{{ post.authorName || '匿名用户' }}</text>
          </view>

          <text class="community-detail__title">{{ post.title }}</text>
          <text class="community-detail__content" decode>{{ post.content }}</text>
          <view v-if="post.imageUrls && post.imageUrls.length > 0" class="community-detail__images">
            <image
              v-for="(url, index) in post.imageUrls"
              :key="index"
              class="community-detail__image"
              :src="assetFullUrl(url)"
              mode="aspectFill"
              @tap="previewImage(index)"
            />
          </view>

          <!-- Tags -->
          <view v-if="post.tags && post.tags.length > 0" class="community-detail__tags">
            <text
              v-for="tag in post.tags"
              :key="tag"
              class="community-detail__tag"
              @tap="goTag(tag)"
            >#{{ tag }}</text>
          </view>

          <view class="community-detail__meta">
            <text class="community-detail__stat">{{ post.likeCount }} 赞</text>
            <text class="community-detail__stat">{{ post.commentCount }} 评论</text>
            <text class="community-detail__stat">{{ post.viewCount }} 浏览</text>
          </view>

          <!-- Action Buttons（V2：统一线性图标，激活态用色区分，不再用 emoji 拟物） -->
          <view class="community-detail__actions">
            <view class="community-detail__action-btn" @tap="handleLike">
              <PcIcon name="heart" :size="16" :color="hasLiked ? '#FA5151' : '#666666'" />
              <text>赞</text>
            </view>
            <view class="community-detail__action-btn" @tap="handleFavorite">
              <PcIcon name="bookmark" :size="16" :color="hasFavorited ? '#11796F' : '#666666'" />
              <text>收藏</text>
            </view>
          </view>
        </view>

        <!-- Comments Section -->
        <view class="community-detail__comments">
          <text class="community-detail__comments-title">评论 ({{ post.commentCount }})</text>

          <!-- Comment Input -->
          <view v-if="isLoggedIn" class="community-detail__comment-input">
            <input
              class="community-detail__input"
              type="text"
              v-model="commentInput"
              :placeholder="replyPlaceholder"
              @confirm="handleComment"
            />
            <view v-if="replyTo" class="community-detail__cancel-reply" @tap="cancelReply">
              <text class="community-detail__cancel-reply-text">取消</text>
            </view>
            <view class="community-detail__send-btn" @tap="handleComment">
              <text class="community-detail__send-btn-text">{{ commenting ? '...' : '发送' }}</text>
            </view>
          </view>

          <PcStatePanel :status="commentsStatus" empty-text="暂无评论，快来抢沙发~">
            <view class="community-detail__comment-list">
              <!-- Flat comment list (Douyin-style): author @ replyTo + content -->
              <view v-for="comment in comments" :key="comment.id" class="comment-item">
                <view class="comment-item__avatar">
                  <image v-if="comment.authorAvatar" class="comment-item__avatar-img" :src="assetFullUrl(comment.authorAvatar)" mode="aspectFill" />
                  <text v-else class="comment-item__avatar-initial">{{ (comment.authorName || '?').charAt(0) }}</text>
                </view>
                <view class="comment-item__body">
                  <view class="comment-item__header">
                    <text class="comment-item__author">{{ comment.authorName || '匿名用户' }}</text>
                    <text v-if="comment.replyToName" class="comment-item__reply-to">回复 @{{ comment.replyToName }}</text>
                  </view>
                  <text class="comment-item__content">{{ comment.content }}</text>
                  <view class="comment-item__footer">
                    <text class="comment-item__time">{{ formatTime(comment.createTime) }}</text>
                    <view class="comment-item__actions">
                      <view class="comment-item__action" @tap="handleLikeComment(comment)">
                        <PcIcon name="heart" :size="13" :color="likedCommentIds.has(comment.id) ? '#FA5151' : '#999999'" />
                        <text>{{ comment.likeCount }}</text>
                      </view>
                      <view v-if="isLoggedIn" class="comment-item__action" @tap="startReply(comment)">
                        <text>回复</text>
                      </view>
                      <view v-if="isLoggedIn && comment.authorUserId === currentUserId" class="comment-item__action comment-item__action--danger" @tap="handleDeleteComment(comment.id)">
                        <text>删除</text>
                      </view>
                    </view>
                  </view>
                </view>
              </view>
            </view>
          </PcStatePanel>
        </view>
      </template>
    </PcStatePanel>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PcStatePanel from '@/components/PcStatePanel.vue'
import PcIcon from '@/components/PcIcon.vue'
import {
  getPostDetail, getPostCommentsFlat,
  createComment, deleteComment, likeComment, unlikeComment,
  likePost, unlikePost, favoritePost, unfavoritePost,
} from '@/api/community'
import { useUserStore } from '@/store/user'
import type { PostDetail, CommentFlatItem } from '@/types/community'
import { normalizeRouteParam } from '@/utils/route-query'
import { openCommunityTag } from '@/utils/community-navigation'
import { assetFullUrl } from '@/utils/asset-url'

const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)
const currentUserId = computed(() => userStore.profile?.id)

const post = ref<PostDetail | null>(null)
const pageStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const comments = ref<CommentFlatItem[]>([])
const commentsStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')

const hasLiked = ref(false)
const hasFavorited = ref(false)
const commentInput = ref('')
const commenting = ref(false)
const replyTo = ref<CommentFlatItem | null>(null)
const likedCommentIds = ref<Set<string>>(new Set())
const currentPostId = ref('')

/** Placeholder reflects whom the user is replying to, by name (not floor number). */
const replyPlaceholder = computed(() => {
  if (!replyTo.value) return '写下你的评论...'
  const name = replyTo.value.authorName || '匿名用户'
  return `回复 @${name}...`
})

/** Open the native full-screen image viewer with swipe + pinch-zoom. */
function previewImage(index: number) {
  if (!post.value?.imageUrls || post.value.imageUrls.length === 0) return
  const urls = post.value.imageUrls.map(u => assetFullUrl(u))
  uni.previewImage({
    current: urls[index],
    urls,
  })
}

async function loadDetail(routeId?: unknown) {
  const id = normalizeRouteParam(routeId ?? currentPostId.value)

  if (!id) {
    post.value = null
    comments.value = []
    pageStatus.value = 'empty'
    commentsStatus.value = 'empty'
    return
  }

  currentPostId.value = id
  pageStatus.value = 'loading'
  commentsStatus.value = 'loading'

  const [postRes, commentRes] = await Promise.all([
    getPostDetail(id),
    getPostCommentsFlat(id),
  ])

  if (!postRes.success || !postRes.data) {
    post.value = null
    comments.value = []
    pageStatus.value = 'error'
    commentsStatus.value = 'empty'
    return
  }

  post.value = postRes.data
  pageStatus.value = 'success'

  if (commentRes.success && commentRes.data) {
    comments.value = commentRes.data
    commentsStatus.value = comments.value.length > 0 ? 'success' : 'empty'
  } else {
    commentsStatus.value = 'error'
  }
}

async function handleLike() {
  if (!isLoggedIn.value || !post.value) {
    uni.showToast({ title: '请先登录', icon: 'none' }); return
  }
  const id = post.value.id
  if (hasLiked.value) {
    await unlikePost(id)
    hasLiked.value = false
    if (post.value) post.value.likeCount--
  } else {
    const res = await likePost(id)
    if (res.success) {
      hasLiked.value = true
      if (post.value) post.value.likeCount++
    }
  }
}

async function handleFavorite() {
  if (!isLoggedIn.value || !post.value) {
    uni.showToast({ title: '请先登录', icon: 'none' }); return
  }
  const id = post.value.id
  if (hasFavorited.value) {
    await unfavoritePost(id)
    hasFavorited.value = false
    uni.showToast({ title: '已取消收藏', icon: 'none' })
  } else {
    const res = await favoritePost(id)
    if (res.success) {
      hasFavorited.value = true
      uni.showToast({ title: '已收藏', icon: 'success' })
    }
  }
}

function startReply(comment: CommentFlatItem) {
  replyTo.value = comment
  commentInput.value = ''
}

function cancelReply() {
  replyTo.value = null
  commentInput.value = ''
}

async function handleComment() {
  if (!isLoggedIn.value || !post.value) {
    uni.showToast({ title: '请先登录', icon: 'none' }); return
  }
  if (!commentInput.value.trim()) return

  commenting.value = true
  const parentId = replyTo.value ? replyTo.value.id : undefined
  const res = await createComment(post.value.id, commentInput.value, parentId)
  commenting.value = false

  if (res.success) {
    commentInput.value = ''
    replyTo.value = null
    uni.showToast({ title: '评论成功', icon: 'success' })
    await loadDetail()
  }
}

async function handleLikeComment(comment: CommentFlatItem) {
  if (!isLoggedIn.value || !post.value) {
    uni.showToast({ title: '请先登录', icon: 'none' }); return
  }
  if (likedCommentIds.value.has(comment.id)) {
    await unlikeComment(post.value.id, comment.id)
    likedCommentIds.value.delete(comment.id)
    comment.likeCount = Math.max(0, comment.likeCount - 1)
  } else {
    const res = await likeComment(post.value.id, comment.id)
    if (res.success) {
      likedCommentIds.value.add(comment.id)
      comment.likeCount++
    }
  }
}

async function handleDeleteComment(commentId: string) {
  if (!post.value) return
  uni.showModal({
    title: '确认删除',
    content: '确定删除这条评论吗？',
    success: async (res) => {
      if (!res.confirm || !post.value) return
      try {
        const deleteRes = await deleteComment(post.value.id, commentId)
        if (deleteRes.success) {
          uni.showToast({ title: '已删除', icon: 'success' })
          await loadDetail()
        } else {
          uni.showToast({ title: deleteRes.error?.message || '删除失败', icon: 'none' })
        }
      } catch {
        uni.showToast({ title: '删除失败，请稍后重试', icon: 'none' })
      }
    },
  })
}

function formatTime(time: string): string {
  const d = new Date(time)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  if (diff < 604800000) return Math.floor(diff / 86400000) + '天前'
  return `${d.getMonth() + 1}月${d.getDate()}日`
}

onLoad((query) => {
  loadDetail(query?.id)
})

function goTag(tag: string) {
  openCommunityTag(tag)
}
</script>

<style scoped>
.community-detail {
  padding: 40rpx;
}

.community-detail__card {
  background: var(--pc-user-surface);
  border-radius: 32rpx;
  padding: 40rpx;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
  margin-bottom: 32rpx;
}

.community-detail__title {
  display: block;
  font-size: 48rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
  margin-bottom: 24rpx;
}

.community-detail__content {
  display: block;
  width: 100%;
  font-size: 28rpx;
  color: var(--pc-user-ink);
  line-height: 1.8;
  margin-bottom: 24rpx;
  white-space: pre-wrap;
  word-break: break-word;
}

.community-detail__images {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.community-detail__image {
  width: 200rpx;
  height: 200rpx;
  border-radius: 16rpx;
  cursor: pointer;
}

.community-detail__image:active {
  opacity: 0.85;
}

.community-detail__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-bottom: 24rpx;
}

.community-detail__tag {
  font-size: 22rpx;
  color: var(--pc-user-primary);
  background: rgba(43, 122, 120, 0.08);
  padding: 8rpx 20rpx;
  border-radius: 12rpx;
}

.community-detail__meta {
  display: flex;
  gap: 32rpx;
}

.community-detail__stat {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.community-detail__actions {
  display: flex;
  gap: 24rpx;
  margin-top: 24rpx;
  padding-top: 24rpx;
  border-top: 1px solid var(--pc-user-line);
}

.community-detail__action-btn {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 32rpx;
  border-radius: 20rpx;
  background: var(--pc-user-soft);
  font-size: 28rpx;
  color: var(--pc-user-primary);
}

/* Comments */
.community-detail__comments {
  background: var(--pc-user-surface);
  border-radius: 32rpx;
  padding: 40rpx;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

/* Post author */
.community-detail__author {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 24rpx;
}

.community-detail__author-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: var(--pc-user-soft);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.community-detail__author-img {
  width: 100%;
  height: 100%;
}

.community-detail__author-initial {
  font-size: 32rpx;
  color: var(--pc-user-primary);
  font-weight: 600;
}

.community-detail__author-name {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--pc-user-ink);
}

.community-detail__comments-title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
  margin-bottom: 24rpx;
}

.community-detail__comment-input {
  display: flex;
  gap: 16rpx;
  margin-bottom: 32rpx;
  align-items: center;
}

.community-detail__input {
  flex: 1;
  height: 80rpx;
  border: 1px solid var(--pc-user-line);
  border-radius: 40rpx;
  padding: 0 32rpx;
  font-size: 28rpx;
  color: var(--pc-user-ink);
  background: var(--pc-user-cream);
}

.community-detail__cancel-reply {
  padding: 8rpx 16rpx;
}

.community-detail__cancel-reply-text {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.community-detail__send-btn {
  height: 80rpx;
  padding: 0 40rpx;
  border-radius: 40rpx;
  background: var(--pc-user-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.community-detail__send-btn-text {
  color: var(--pc-user-surface);
  font-size: 28rpx;
  font-weight: 600;
}

.community-detail__comment-list {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

/* Flat comment item (Douyin-style) */
.comment-item {
  display: flex;
  gap: 20rpx;
}

.comment-item__avatar {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: var(--pc-user-soft);
  overflow: hidden;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.comment-item__avatar-img {
  width: 100%;
  height: 100%;
}

.comment-item__avatar-initial {
  font-size: 26rpx;
  color: var(--pc-user-primary);
  font-weight: 600;
}

.comment-item__body {
  flex: 1;
  min-width: 0;
}

.comment-item__header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 8rpx;
  flex-wrap: wrap;
}

.comment-item__author {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--pc-user-dark);
}

.comment-item__reply-to {
  font-size: 24rpx;
  color: var(--pc-user-primary);
  background: rgba(17, 121, 111, 0.08);
  padding: 2rpx 16rpx;
  border-radius: 1998rpx;
}

.comment-item__content {
  display: block;
  width: 100%;
  font-size: 28rpx;
  color: var(--pc-user-ink);
  line-height: 1.6;
  margin-bottom: 12rpx;
  white-space: pre-wrap;
  word-break: break-word;
}

.comment-item__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.comment-item__time {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.comment-item__actions {
  display: flex;
  gap: 28rpx;
}

.comment-item__action {
  display: flex;
  align-items: center;
  gap: 6rpx;
}

.comment-item__action text {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.comment-item__action--danger text {
  color: #e65555;
}

</style>
