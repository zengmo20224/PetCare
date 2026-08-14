<template>
  <view class="pc-page post-create">
    <PcPageHeader title="发布帖子" />

    <PcStatePanel v-if="!isLoggedIn" status="unauthorized" />

    <view v-else class="post-create__form">
      <PcFormField label="标题">
        <input class="pc-input" type="text" v-model="title" placeholder="帖子标题（1-120字）" />
      </PcFormField>

      <view class="post-create__content">
        <view class="post-create__ai-bar">
          <text class="post-create__ai-hint">没有思路？让 AI 帮你起个草稿（可修改后再发布）</text>
          <view
            class="post-create__ai-btn"
            :class="{ 'post-create__ai-btn--loading': aiGenerating }"
            @tap="handleAiGenerate"
          >
            <text class="post-create__ai-btn-text">{{ aiGenerating ? '生成中...' : 'AI 帮我写' }}</text>
          </view>
        </view>
        <textarea
          class="post-create__textarea"
          placeholder="分享你和宠物的故事..."
          v-model="content"
          maxlength="5000"
        />
      </view>

      <!-- Image Picker -->
      <PcFormField label="图片（最多9张）">
        <view class="post-create__images">
          <view v-for="(img, index) in images" :key="index" class="post-create__image-item">
            <image class="post-create__image-preview" :src="assetFullUrl(img)" mode="aspectFill" />
            <view class="post-create__image-remove" @tap="removeImage(index)">
              <text class="post-create__image-remove-icon">×</text>
            </view>
          </view>
          <view v-if="images.length < 9" class="post-create__image-add" @tap="chooseImages">
            <text class="post-create__image-add-icon">+</text>
            <text class="post-create__image-add-text">{{ images.length }}/9</text>
          </view>
        </view>
      </PcFormField>

      <!-- Tags -->
      <PcFormField label="标签">
        <TagInput v-model="tags" :max="3" />
      </PcFormField>

      <view class="post-create__action">
        <PcPrimaryButton text="发布" :loading="submitting" @press="handleSubmit" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'
import PcFormField from '@/components/PcFormField.vue'
import TagInput from '@/components/TagInput.vue'
import { createPost } from '@/api/community'
import { generatePostDraft } from '@/api/ai'
import { uploadFile } from '@/api/user'
import { useUserStore } from '@/store/user'
import { assetFullUrl } from '@/utils/asset-url'

const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)

const title = ref('')
const content = ref('')
const tags = ref<string[]>([])
const images = ref<string[]>([])
const submitting = ref(false)
const aiGenerating = ref(false)

/** M8.3：AI 发帖助手——输入事件描述生成草稿，仅填入输入框（用户可改，不自动发布）。 */
function handleAiGenerate() {
  if (aiGenerating.value) return
  uni.showModal({
    title: 'AI 帮我写',
    editable: true,
    placeholderText: '简单描述要分享的事，如：豆包今天第一次洗澡特别乖',
    success: (res) => {
      if (!res.confirm) return
      const event = (res.content || '').trim()
      if (!event) {
        uni.showToast({ title: '请先描述一下要分享的事', icon: 'none' })
        return
      }
      doGenerate(event)
    },
  })
}

async function doGenerate(event: string) {
  aiGenerating.value = true
  const res = await generatePostDraft({
    event,
    originalText: content.value.trim() || undefined,
    tone: '轻松真实',
  })
  aiGenerating.value = false
  if (res.success && res.data?.suggestedText) {
    content.value = res.data.suggestedText
    uni.showToast({ title: '草稿已生成，可修改后发布', icon: 'none' })
  } else {
    uni.showToast({ title: 'AI 生成失败，请稍后再试', icon: 'none' })
  }
}

function chooseImages() {
  const remaining = 9 - images.value.length
  if (remaining <= 0) return

  uni.chooseImage({
    count: remaining,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      for (const filePath of res.tempFilePaths) {
        if (images.value.length >= 9) break
        uni.showLoading({ title: '上传中...' })
        const uploadRes = await uploadFile(filePath)
        uni.hideLoading()
        if (uploadRes.success && uploadRes.data) {
          images.value.push(uploadRes.data.url)
        } else {
          uni.showToast({ title: '图片上传失败', icon: 'none' })
        }
      }
    },
  })
}

function removeImage(index: number) {
  images.value = images.value.filter((_, i) => i !== index)
}

async function handleSubmit() {
  if (!title.value.trim()) {
    uni.showToast({ title: '请输入标题', icon: 'none' }); return
  }
  if (!content.value.trim()) {
    uni.showToast({ title: '请输入内容', icon: 'none' }); return
  }

  submitting.value = true
  const res = await createPost({
    title: title.value,
    content: content.value,
    tags: tags.value,
    imageUrls: images.value.length > 0 ? images.value : undefined,
  })
  submitting.value = false

  if (res.success) {
    uni.showToast({ title: '发布成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  }
}
</script>

<style scoped>
.post-create {
  padding: 40rpx;
}

.post-create__form {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.post-create__content {
  background: var(--pc-user-surface);
  border-radius: 32rpx;
  padding: 32rpx;
}

/* AI 助手条（M8.3） */
.post-create__ai-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 20rpx;
}

.post-create__ai-hint {
  font-size: 22rpx;
  color: var(--pc-user-muted);
  flex: 1;
}

.post-create__ai-btn {
  flex-shrink: 0;
  padding: 10rpx 28rpx;
  border-radius: 999rpx;
  background: rgba(64, 128, 255, 0.12);
}

.post-create__ai-btn--loading {
  opacity: 0.5;
}

.post-create__ai-btn-text {
  font-size: 24rpx;
  color: #4080ff;
}

.post-create__textarea {
  width: 100%;
  min-height: 400rpx;
  font-size: 28rpx;
  color: var(--pc-user-ink);
  line-height: 1.6;
  border: none;
  outline: none;
  resize: none;
}

/* Image Picker */
.post-create__images {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.post-create__image-item {
  position: relative;
  width: 160rpx;
  height: 160rpx;
  border-radius: 16rpx;
  overflow: hidden;
}

.post-create__image-preview {
  width: 100%;
  height: 100%;
}

.post-create__image-remove {
  position: absolute;
  top: 0;
  right: 0;
  width: 44rpx;
  height: 44rpx;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 0 0 0 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.post-create__image-remove-icon {
  color: var(--pc-user-surface);
  font-size: 28rpx;
  line-height: 1;
}

.post-create__image-add {
  width: 160rpx;
  height: 160rpx;
  border: 2px dashed var(--pc-user-line);
  border-radius: 16rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.post-create__image-add-icon {
  font-size: 56rpx;
  color: var(--pc-user-muted);
}

.post-create__image-add-text {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.post-create__action {
  margin-top: 16rpx;
}

.pc-input {
  height: 88rpx;
  border: 1px solid var(--pc-user-line);
  border-radius: 24rpx;
  padding: 0 28rpx;
  font-size: 28rpx;
  color: var(--pc-user-ink);
  background: var(--pc-user-surface);
}
</style>
