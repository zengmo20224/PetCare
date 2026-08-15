<template>
  <view class="pc-page profile-edit">
    <PcPageHeader title="编辑资料" />

    <view v-if="profile" class="profile-edit__form">
      <!-- Avatar -->
      <view class="profile-edit__avatar-section">
        <view class="profile-edit__avatar" @tap="chooseAvatar">
          <image v-if="avatarUrl" class="profile-edit__avatar-img" :src="assetFullUrl(avatarUrl)" mode="aspectFill" />
          <text v-else class="profile-edit__avatar-placeholder">{{ profile.nickname?.charAt(0) || '?' }}</text>
        </view>
        <text class="profile-edit__avatar-hint">点击更换头像</text>
      </view>

      <!-- Nickname -->
      <PcFormField label="昵称">
        <input class="pc-input" type="text" v-model="nickname" placeholder="输入昵称" maxlength="64" />
      </PcFormField>

      <!-- Phone (read-only) -->
      <PcFormField label="手机号">
        <text class="profile-edit__readonly">{{ profile.phone || '未绑定' }}</text>
      </PcFormField>

      <!-- Real Name -->
      <PcFormField label="真实姓名">
        <input class="pc-input" type="text" v-model="realName" placeholder="选填，用于上门服务身份核验" maxlength="64" />
      </PcFormField>

      <!-- ID Card No -->
      <PcFormField label="身份证号">
        <input class="pc-input" type="text" v-model="idCardNo" placeholder="选填，用于上门服务身份核验" maxlength="18" />
      </PcFormField>

      <!-- ID Card Image -->
      <PcFormField label="身份证照片">
        <view class="profile-edit__id-card">
          <view class="profile-edit__id-card-preview" @tap="chooseIdCardImage">
            <image v-if="idCardImageUrl" class="profile-edit__id-card-img" :src="assetFullUrl(idCardImageUrl)" mode="aspectFill" />
            <text v-else class="profile-edit__id-card-placeholder">+ 上传身份证</text>
          </view>
          <text v-if="idCardImageUrl" class="profile-edit__id-card-change" @tap="chooseIdCardImage">更换照片</text>
        </view>
      </PcFormField>

      <!-- Save Button -->
      <view class="profile-edit__action">
        <PcPrimaryButton text="保存" :loading="saving" @press="handleSave" />
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcFormField from '@/components/PcFormField.vue'
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'
import { useUserStore } from '@/store/user'
import { updateUserProfile, uploadFile } from '@/api/user'
import { assetFullUrl } from '@/utils/asset-url'

const userStore = useUserStore()

const profile = ref(userStore.profile)
const nickname = ref('')
const realName = ref('')
const idCardNo = ref('')
const avatarUrl = ref<string | null>(null)
const idCardImageUrl = ref<string | null>(null)
const saving = ref(false)

onMounted(async () => {
  if (!userStore.profile) {
    await userStore.fetchProfile()
  }
  profile.value = userStore.profile
  if (profile.value) {
    nickname.value = profile.value.nickname || ''
    realName.value = profile.value.realName || ''
    idCardNo.value = profile.value.idCardNo || ''
    avatarUrl.value = profile.value.avatarUrl
    idCardImageUrl.value = profile.value.idCardImageUrl
  }
})

function chooseAvatar() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const filePath = res.tempFilePaths[0]
      uni.showLoading({ title: '上传中...' })
      const uploadRes = await uploadFile(filePath)
      uni.hideLoading()
      if (uploadRes.success && uploadRes.data) {
        avatarUrl.value = uploadRes.data.url
      }
    },
  })
}

function chooseIdCardImage() {
  uni.chooseImage({
    count: 1,
    sizeType: ['compressed'],
    sourceType: ['album', 'camera'],
    success: async (res) => {
      const filePath = res.tempFilePaths[0]
      uni.showLoading({ title: '上传中...' })
      const uploadRes = await uploadFile(filePath)
      uni.hideLoading()
      if (uploadRes.success && uploadRes.data) {
        idCardImageUrl.value = uploadRes.data.url
      }
    },
  })
}

async function handleSave() {
  if (!nickname.value.trim()) {
    uni.showToast({ title: '昵称不能为空', icon: 'none' })
    return
  }

  saving.value = true
  const res = await updateUserProfile({
    nickname: nickname.value.trim(),
    avatarUrl: avatarUrl.value || undefined,
    realName: realName.value.trim() || undefined,
    idCardNo: idCardNo.value.trim() || undefined,
    idCardImageUrl: idCardImageUrl.value || undefined,
  })
  saving.value = false

  if (res.success && res.data) {
    await userStore.fetchProfile()
    uni.showToast({ title: '保存成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 800)
  }
}
</script>

<style scoped>
.profile-edit {
  padding: 40rpx;
}

.profile-edit__form {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
}

.profile-edit__avatar-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16rpx;
  padding: 32rpx 0;
}

.profile-edit__avatar {
  width: 160rpx;
  height: 160rpx;
  border-radius: 50%;
  background: var(--pc-user-primary);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.profile-edit__avatar-img {
  width: 100%;
  height: 100%;
}

.profile-edit__avatar-placeholder {
  font-size: 64rpx;
  color: var(--pc-user-surface);
  font-weight: 700;
}

.profile-edit__avatar-hint {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.profile-edit__readonly {
  font-size: 28rpx;
  color: var(--pc-user-muted);
}

.profile-edit__id-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
}

.profile-edit__id-card-preview {
  width: 320rpx;
  height: 200rpx;
  border: 2px dashed var(--pc-user-line);
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.profile-edit__id-card-img {
  width: 100%;
  height: 100%;
}

.profile-edit__id-card-placeholder {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.profile-edit__id-card-change {
  font-size: 22rpx;
  color: var(--pc-user-primary);
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

.profile-edit__action {
  margin-top: 16rpx;
}
</style>
