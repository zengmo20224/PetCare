<template>
  <view class="pc-page cp-page">
    <PcPageHeader title="修改密码" />

    <view class="cp-card">
      <view class="cp-brand">
        <text class="cp-brand__title">修改登录密码</text>
        <text class="cp-brand__subtitle">请输入原密码验证身份后设置新密码</text>
      </view>

      <wd-cell-group border class="cp-form">
        <wd-input
          v-model="form.oldPassword"
          label="原密码"
          label-width="80px"
          placeholder="请输入原密码"
          show-password
          clearable
        />
        <wd-input
          v-model="form.newPassword"
          label="新密码"
          label-width="80px"
          placeholder="8-32位，含数字和字母"
          show-password
          clearable
        />
        <wd-input
          v-model="form.confirmPassword"
          label="确认密码"
          label-width="80px"
          placeholder="再次输入新密码"
          show-password
          clearable
        />
      </wd-cell-group>

      <view class="cp-rules">
        <text class="cp-rules__title">密码要求</text>
        <text class="cp-rules__item">· 长度 8-32 位</text>
        <text class="cp-rules__item">· 必须同时包含数字和字母</text>
      </view>

      <PcPrimaryButton text="确认修改" :loading="saving" @tap="handleSave" />

      <view class="cp-forgot">
        <wd-button type="text" size="small" @click="goForgotPassword">忘记原密码？用密保问题修改</wd-button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'
import { changePassword } from '@/api/user'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const saving = ref(false)

const form = ref({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

async function handleSave() {
  if (!form.value.oldPassword) {
    uni.showToast({ title: '请输入原密码', icon: 'none' })
    return
  }
  if (!form.value.newPassword) {
    uni.showToast({ title: '请输入新密码', icon: 'none' })
    return
  }
  if (form.value.newPassword.length < 8 || form.value.newPassword.length > 32) {
    uni.showToast({ title: '新密码长度 8-32 位', icon: 'none' })
    return
  }
  if (!/(?=.*[A-Za-z])(?=.*\d)/.test(form.value.newPassword)) {
    uni.showToast({ title: '新密码必须包含数字和字母', icon: 'none' })
    return
  }
  if (form.value.newPassword !== form.value.confirmPassword) {
    uni.showToast({ title: '两次输入的新密码不一致', icon: 'none' })
    return
  }

  saving.value = true
  const res = await changePassword({
    oldPassword: form.value.oldPassword,
    newPassword: form.value.newPassword,
  })
  saving.value = false

  if (res.success) {
    uni.showToast({ title: '密码修改成功，请重新登录', icon: 'success' })
    // 改密后 token 仍有效，但为安全起见退出重新登录
    setTimeout(async () => {
      await userStore.logout()
      uni.redirectTo({ url: '/pages/auth/login' })
    }, 1000)
  }
}

function goForgotPassword() {
  uni.navigateTo({ url: '/pages/auth/forgot-password' })
}
</script>

<style scoped>
.cp-page {
  padding: 20px;
}

.cp-card {
  display: flex;
  flex-direction: column;
  gap: 18px;
  margin-top: 24px;
  padding: 24px 20px;
  border-radius: 24px;
  background: #FFFFFF;
  box-shadow: 0 12px 32px rgba(25, 50, 46, 0.09);
}

.cp-brand {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.cp-brand__title {
  font-size: 18px;
  font-weight: 800;
  color: #0C4D48;
}

.cp-brand__subtitle {
  font-size: 12px;
  color: #71817D;
}

.cp-form {
  border-radius: 16px;
  overflow: hidden;
}

.cp-rules {
  display: flex;
  flex-direction: column;
  gap: 4px;
  background: #FAF8F3;
  border-radius: 12px;
  padding: 12px 14px;
}

.cp-rules__title {
  font-size: 12px;
  font-weight: 700;
  color: #19322E;
}

.cp-rules__item {
  font-size: 11px;
  color: #71817D;
  line-height: 1.6;
}

.cp-forgot {
  display: flex;
  justify-content: center;
}
</style>
