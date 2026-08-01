<template>
  <view class="pc-page post-create">
    <PcPageHeader title="发布帖子" />

    <PcStatePanel v-if="!isLoggedIn" status="unauthorized" />

    <view v-else class="post-create__form">
      <PcFormField label="标题">
        <input class="pc-input" type="text" v-model="title" placeholder="帖子标题（1-120字）" />
      </PcFormField>

      <view class="post-create__content">
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
        <PcPrimaryButton text="发布" :loading="submitting" @tap="handleSubmit" />
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
  padding: 20px;
}

.post-create__form {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.post-create__content {
  background: #fff;
  border-radius: 16px;
  padding: 16px;
}

.post-create__textarea {
  width: 100%;
  min-height: 200px;
  font-size: 14px;
  color: #19322E;
  line-height: 1.6;
  border: none;
  outline: none;
  resize: none;
}

/* Image Picker */
.post-create__images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.post-create__image-item {
  position: relative;
  width: 80px;
  height: 80px;
  border-radius: 8px;
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
  width: 22px;
  height: 22px;
  background: rgba(0, 0, 0, 0.6);
  border-radius: 0 0 0 8px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.post-create__image-remove-icon {
  color: #fff;
  font-size: 14px;
  line-height: 1;
}

.post-create__image-add {
  width: 80px;
  height: 80px;
  border: 2px dashed #E2E9E6;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.post-create__image-add-icon {
  font-size: 28px;
  color: #71817D;
}

.post-create__image-add-text {
  font-size: 11px;
  color: #71817D;
}

.post-create__action {
  margin-top: 8px;
}

.pc-input {
  height: 44px;
  border: 1px solid #E2E9E6;
  border-radius: 12px;
  padding: 0 14px;
  font-size: 14px;
  color: #19322E;
  background: #fff;
}
</style>
