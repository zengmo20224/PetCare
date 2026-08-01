<template>
  <view class="pc-page profile-page">
    <PcPageHeader title="我的" />

    <!-- Not logged in -->
    <view v-if="!isLoggedIn" class="profile-login-card">
      <view class="profile-login-card__avatar">
        <text>我的</text>
      </view>
      <text class="profile-login-card__title">登录 PetCare 账号</text>
      <text class="profile-login-card__hint">登录后可以预约服务、管理订单、保存宠物档案并参与社区互动。</text>
      <view class="profile-login-card__primary">
        <PcPrimaryButton text="手机号登录" @tap="goLogin" />
      </view>
      <view class="profile-login-card__links">
        <view class="profile-login-card__secondary" @tap="goRegister">
          <text>新用户注册</text>
        </view>
        <view class="profile-login-card__ghost" @tap="goForgotPassword">
          <text>忘记密码</text>
        </view>
      </view>
    </view>

    <!-- Logged in -->
    <view v-else class="profile-content" @tap="goEditProfile">
      <view class="profile-avatar">
        <image v-if="avatarUrl" class="profile-avatar__img" :src="avatarUrl" mode="aspectFill" />
        <text v-else class="profile-avatar__text">{{ displayName.charAt(0) }}</text>
      </view>
      <view class="profile-info">
        <text class="profile-info__name">{{ displayName }}</text>
        <text class="profile-info__phone">{{ displayPhone }}</text>
      </view>
      <text class="profile-content__arrow">›</text>
    </view>

    <!-- Menu Items -->
    <view v-if="isLoggedIn" class="profile-menu">
      <view class="profile-menu-item" @tap="goPage('/pages/booking/list')">
        <text class="profile-menu-item__label">我的预约</text>
        <text class="profile-menu-item__arrow">›</text>
      </view>
      <view class="profile-menu-item" @tap="goPage('/pages/order/list')">
        <text class="profile-menu-item__label">我的订单</text>
        <text class="profile-menu-item__arrow">›</text>
      </view>
      <view class="profile-menu-item" @tap="goPage('/pages/wallet/index')">
        <text class="profile-menu-item__label">我的钱包</text>
        <text class="profile-menu-item__arrow">›</text>
      </view>
      <view class="profile-menu-item" @tap="goPage('/pages/pets/index')">
        <text class="profile-menu-item__label">我的宠物</text>
        <text class="profile-menu-item__arrow">›</text>
      </view>
      <view class="profile-menu-item" @tap="goPage('/pages/my-community/index')">
        <text class="profile-menu-item__label">我的社区</text>
        <text class="profile-menu-item__arrow">›</text>
      </view>
      <view class="profile-menu-item" @tap="goPage('/pages/notifications/index')">
        <view class="profile-menu-item__left">
          <text class="profile-menu-item__label">消息通知</text>
          <view v-if="unreadCount > 0" class="profile-menu-item__badge">
            <text class="profile-menu-item__badge-text">{{ unreadCount > 99 ? '99+' : unreadCount }}</text>
          </view>
        </view>
        <text class="profile-menu-item__arrow">›</text>
      </view>
      <view class="profile-menu-item" @tap="goPage('/pages/addresses/index')">
        <text class="profile-menu-item__label">我的地址</text>
        <text class="profile-menu-item__arrow">›</text>
      </view>
      <view class="profile-menu-item" @tap="goPage('/pages/security-questions/index')">
        <text class="profile-menu-item__label">密保管理</text>
        <text class="profile-menu-item__arrow">›</text>
      </view>
      <view class="profile-menu-item" @tap="goPage('/pages/change-password/index')">
        <text class="profile-menu-item__label">修改密码</text>
        <text class="profile-menu-item__arrow">›</text>
      </view>
      <view class="profile-menu-item profile-menu-item--logout" @tap="handleLogout">
        <text class="profile-menu-item__label profile-menu-item__label--danger">退出登录</text>
      </view>
    </view>
    <PcBottomNav current-path="pages/profile/index" />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcBottomNav from '@/components/PcBottomNav.vue'
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'
import { useUserStore } from '@/store/user'
import { getUnreadCount } from '@/api/notification'

const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)
const unreadCount = ref(0)

const displayName = computed(() => userStore.profile?.nickname || '用户')
const displayPhone = computed(() => userStore.profile?.phone || '')
const avatarUrl = computed(() => {
  const url = userStore.profile?.avatarUrl
  if (!url) return null
  if (url.startsWith('http')) return url
  const base = import.meta.env.VITE_API_BASE_URL || ''
  return base + url
})

function goLogin() {
  uni.navigateTo({ url: '/pages/auth/login' })
}

function goRegister() {
  uni.navigateTo({ url: '/pages/auth/register' })
}

function goForgotPassword() {
  uni.navigateTo({ url: '/pages/auth/forgot-password' })
}

function goEditProfile() {
  uni.navigateTo({ url: '/pages/profile-edit/index' })
}

function handleLogout() {
  userStore.logout()
  uni.showToast({ title: '已退出', icon: 'none' })
}

function goPage(url: string) {
  uni.navigateTo({ url })
}

// Refresh unread count every time the page is shown — including when the user
// returns from the notifications page after marking items as read.
onShow(async () => {
  if (isLoggedIn.value) {
    if (!userStore.profile) {
      userStore.fetchProfile()
    }
    // Reload unread count (may have changed since last visit)
    try {
      const res = await getUnreadCount()
      if (res.success && res.data) {
        unreadCount.value = res.data.count
      }
    } catch {
      unreadCount.value = 0
    }
  }
})
</script>

<style scoped>
.profile-page {
  min-height: 100vh;
  padding: 20px 20px 96px;
  background: #FAF8F3;
}

.profile-login-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 13px;
  margin-top: 18px;
  padding: 30px 20px 24px;
  border: 1px solid #DCEBE7;
  border-radius: 24px;
  background: #FFFFFF;
  box-shadow: 0 12px 32px rgba(25, 50, 46, 0.09);
}

.profile-login-card__avatar {
  width: 68px;
  height: 68px;
  border-radius: 24px;
  background: #DFF2ED;
  display: flex;
  align-items: center;
  justify-content: center;
}

.profile-login-card__avatar text {
  color: #11796F;
  font-size: 16px;
  font-weight: 800;
}

.profile-login-card__title {
  font-size: 21px;
  line-height: 1.3;
  font-weight: 800;
  color: #19322E;
}

.profile-login-card__hint {
  max-width: 280px;
  font-size: 13px;
  line-height: 1.7;
  color: #5F746F;
  text-align: center;
}

.profile-login-card__primary {
  width: 100%;
}

.profile-login-card__links {
  display: flex;
  width: 100%;
  gap: 10px;
}

.profile-login-card__secondary,
.profile-login-card__ghost {
  flex: 1;
  height: 42px;
  border-radius: 21px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.profile-login-card__secondary {
  background: #FFF0D1;
}

.profile-login-card__ghost {
  background: #F3F7F5;
}

.profile-login-card__secondary text,
.profile-login-card__ghost text {
  font-size: 13px;
  font-weight: 700;
}

.profile-login-card__secondary text {
  color: #9A5E00;
}

.profile-login-card__ghost text {
  color: #11796F;
}

.profile-content {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

.profile-avatar {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #11796F;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  flex-shrink: 0;
}

.profile-avatar__img {
  width: 100%;
  height: 100%;
}

.profile-avatar__text {
  font-size: 16px;
  color: #fff;
  font-weight: 600;
}

.profile-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
}

.profile-info__name {
  font-size: 16px;
  font-weight: 700;
  color: #19322E;
}

.profile-info__phone {
  font-size: 11px;
  color: #71817D;
}

.profile-content__arrow {
  font-size: 14px;
  color: #71817D;
}

.profile-menu {
  display: flex;
  flex-direction: column;
  gap: 1px;
  background: #E2E9E6;
  border-radius: 16px;
  overflow: hidden;
}

.profile-menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: #fff;
}

.profile-menu-item--logout {
  justify-content: center;
}

.profile-menu-item__label {
  font-size: 14px;
  color: #19322E;
}

.profile-menu-item__label--danger {
  color: #e05050;
}

.profile-menu-item__arrow {
  font-size: 14px;
  color: #71817D;
}

.profile-menu-item__left {
  display: flex;
  align-items: center;
  gap: 8px;
}

.profile-menu-item__badge {
  background: #e05050;
  border-radius: 10px;
  padding: 2px 8px;
  min-width: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.profile-menu-item__badge-text {
  font-size: 11px;
  color: #fff;
  font-weight: 700;
}
</style>
