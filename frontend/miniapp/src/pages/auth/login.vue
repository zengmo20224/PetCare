<template>
  <view class="pc-page auth-page">
    <PcPageHeader title="登录" />

    <view class="auth-card">
      <view class="auth-brand">
        <text class="auth-brand__title">欢迎回来 👋</text>
        <text class="auth-brand__subtitle">登录后即可预约服务、管理爱宠</text>
      </view>

      <wd-cell-group border class="auth-form">
        <wd-input
          v-model="phoneInput"
          label="手机号"
          label-width="80px"
          placeholder="请输入手机号"
          type="number"
          :maxlength="11"
          clearable
        />
        <wd-input
          v-model="passwordInput"
          label="密码"
          label-width="80px"
          placeholder="请输入密码"
          show-password
          clearable
        />
      </wd-cell-group>

      <view class="auth-links">
        <wd-button type="text" size="small" @click="goRegister">没有账号？去注册</wd-button>
        <wd-button type="text" size="small" @click="goForgotPassword">忘记密码</wd-button>
      </view>

      <PcPrimaryButton text="登录" :loading="loginLoading" @tap="handleLogin" />

      <!-- WeChat one-tap login. Mini-program only — H5 has no WeChat provider. -->
      <!-- #ifdef MP-WEIXIN -->
      <view class="auth-divider">
        <view class="auth-divider__line"></view>
        <text class="auth-divider__text">或</text>
        <view class="auth-divider__line"></view>
      </view>
      <button
        class="auth-wx-btn"
        :disabled="wxLoginLoading"
        open-type="getUserInfo"
        @tap="handleWxLogin"
      >
        <text class="auth-wx-btn__text">{{ wxLoginLoading ? '登录中...' : '微信一键登录' }}</text>
      </button>
      <!-- #endif -->
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const phoneInput = ref('')
const passwordInput = ref('')
const loginLoading = ref(false)
const wxLoginLoading = ref(false)

async function handleLogin() {
  if (!phoneInput.value || !passwordInput.value) {
    uni.showToast({ title: '请填写手机号和密码', icon: 'none' })
    return
  }

  loginLoading.value = true
  const ok = await userStore.doLogin(phoneInput.value, passwordInput.value)
  loginLoading.value = false

  if (ok) {
    uni.showToast({ title: '登录成功', icon: 'success' })
    await userStore.fetchProfile()
    uni.switchTab({ url: '/pages/profile/index' })
  }
}

/**
 * WeChat one-tap login (mini-program only, gated by #ifdef MP-WEIXIN in template).
 * Flow: uni.login → temporary code → backend exchanges for openid → JWT issued.
 * The code is a single-use credential; mock backend mode derives a stable openid from it.
 */
async function handleWxLogin() {
  if (wxLoginLoading.value) return
  wxLoginLoading.value = true
  try {
    const loginRes = await new Promise<UniApp.LoginRes>((resolve, reject) => {
      uni.login({
        provider: 'weixin',
        success: resolve,
        fail: reject,
      })
    })
    if (!loginRes.code) {
      uni.showToast({ title: '获取微信授权失败', icon: 'none' })
      return
    }
    const ok = await userStore.doWechatLogin(loginRes.code)
    if (ok) {
      uni.showToast({ title: '登录成功', icon: 'success' })
      await userStore.fetchProfile()
      uni.switchTab({ url: '/pages/profile/index' })
    }
  } catch {
    uni.showToast({ title: '微信登录失败，请重试', icon: 'none' })
  } finally {
    wxLoginLoading.value = false
  }
}

function goRegister() {
  uni.navigateTo({ url: '/pages/auth/register' })
}

function goForgotPassword() {
  uni.navigateTo({ url: '/pages/auth/forgot-password' })
}
</script>

<style scoped>
.auth-page {
  padding: 20px;
}

.auth-card {
  display: flex;
  flex-direction: column;
  gap: 20px;
  margin-top: 24px;
  padding: 24px 20px;
  border-radius: 24px;
  background: #FFFFFF;
  box-shadow: 0 12px 32px rgba(25, 50, 46, 0.09);
}

.auth-brand {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.auth-brand__title {
  font-size: 22px;
  font-weight: 800;
  color: #0C4D48;
}

.auth-brand__subtitle {
  font-size: 13px;
  color: #71817D;
}

.auth-form {
  border-radius: 16px;
  overflow: hidden;
}

.auth-links {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.auth-divider {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 4px 0;
}

.auth-divider__line {
  flex: 1;
  height: 1px;
  background: #e2e9e6;
}

.auth-divider__text {
  font-size: 12px;
  color: #71817d;
}

.auth-wx-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 48px;
  background: #07c160;
  border-radius: 24px;
  border: none;
  padding: 0;
  margin: 0;
}

.auth-wx-btn::after {
  border: none;
}

.auth-wx-btn[disabled] {
  background: #9ad8b6;
  color: #ffffff;
}

.auth-wx-btn__text {
  color: #ffffff;
  font-size: 16px;
  font-weight: 600;
}
</style>
