<template>
  <view class="pc-page profile-page">
    <!-- 未登录态（简化为 demo 风格） -->
    <view v-if="!isLoggedIn" class="profile-login">
      <view class="profile-login__avatar">
        <PcIcon name="user" :size="32" color="#11796F" />
      </view>
      <text class="profile-login__title">登录 PetCare 账号</text>
      <text class="profile-login__hint">登录后可以预约服务、管理订单、保存宠物档案并参与社区互动。</text>
      <view class="profile-login__btn">
        <PcPrimaryButton text="手机号登录" @press="goLogin" />
      </view>
      <view class="profile-login__links">
        <view class="profile-login__link" @tap="goRegister"><text>新用户注册</text></view>
        <view class="profile-login__link" @tap="goForgotPassword"><text>忘记密码</text></view>
      </view>
    </view>

    <!-- 已登录：用户卡（demo profile-user） -->
    <view v-else class="profile-user" @tap="goEditProfile">
      <view class="profile-avatar">
        <image v-if="avatarUrl" class="profile-avatar__img" :src="avatarUrl" mode="aspectFill" />
        <text v-else class="profile-avatar__text">{{ displayName.charAt(0) }}</text>
      </view>
      <view class="profile-user__info">
        <text class="profile-user__name">{{ displayName }}</text>
        <text class="profile-user__phone">{{ displayPhone }}</text>
      </view>
      <PcIcon name="arrow-right" :size="16" color="#B2B2B2" />
    </view>

    <!-- 已登录：菜单分组（demo menu-group × 3） -->
    <template v-if="isLoggedIn">
      <!-- 我的订单 -->
      <view class="menu-group">
        <text class="menu-group__title">我的订单</text>
        <view class="menu-card">
          <view class="menu-item" @tap="goPage('/pages/booking/list')">
            <view class="menu-item__icon"><PcIcon name="calendar" :size="22" color="#11796F" /></view>
            <text class="menu-item__text">我的预约</text>
            <PcIcon name="arrow-right" :size="14" color="#B2B2B2" />
          </view>
          <view class="menu-item" @tap="goPage('/pages/order/list')">
            <view class="menu-item__icon"><PcIcon name="receipt" :size="22" color="#11796F" /></view>
            <text class="menu-item__text">我的订单</text>
            <PcIcon name="arrow-right" :size="14" color="#B2B2B2" />
          </view>
          <view class="menu-item" @tap="goPage('/pages/wallet/index')">
            <view class="menu-item__icon"><PcIcon name="wallet" :size="22" color="#11796F" /></view>
            <text class="menu-item__text">我的钱包</text>
            <PcIcon name="arrow-right" :size="14" color="#B2B2B2" />
          </view>
        </view>
      </view>

      <!-- 我的档案 -->
      <view class="menu-group">
        <text class="menu-group__title">我的档案</text>
        <view class="menu-card">
          <view class="menu-item" @tap="goPage('/pages/pets/index')">
            <view class="menu-item__icon"><PcIcon name="paw" :size="22" color="#11796F" /></view>
            <text class="menu-item__text">我的宠物</text>
            <PcIcon name="arrow-right" :size="14" color="#B2B2B2" />
          </view>
          <view class="menu-item" @tap="goPage('/pages/addresses/index')">
            <view class="menu-item__icon"><PcIcon name="pin" :size="22" color="#11796F" /></view>
            <text class="menu-item__text">我的地址</text>
            <PcIcon name="arrow-right" :size="14" color="#B2B2B2" />
          </view>
          <view class="menu-item" @tap="goPage('/pages/my-community/index')">
            <view class="menu-item__icon"><PcIcon name="chat" :size="22" color="#11796F" /></view>
            <text class="menu-item__text">我的社区</text>
            <PcIcon name="arrow-right" :size="14" color="#B2B2B2" />
          </view>
        </view>
      </view>

      <!-- 消息与安全 -->
      <view class="menu-group">
        <text class="menu-group__title">消息与安全</text>
        <view class="menu-card">
          <view class="menu-item" @tap="goPage('/pages/notifications/index')">
            <view class="menu-item__icon"><PcIcon name="bell" :size="22" color="#11796F" /></view>
            <text class="menu-item__text">消息通知</text>
            <view v-if="unreadCount > 0" class="menu-item__badge">
              <text>{{ unreadCount > 99 ? '99+' : unreadCount }}</text>
            </view>
            <PcIcon name="arrow-right" :size="14" color="#B2B2B2" />
          </view>
          <view class="menu-item" @tap="goPage('/pages/security-questions/index')">
            <view class="menu-item__icon"><PcIcon name="shield" :size="22" color="#11796F" /></view>
            <text class="menu-item__text">密保管理</text>
            <PcIcon name="arrow-right" :size="14" color="#B2B2B2" />
          </view>
          <view class="menu-item" @tap="goPage('/pages/change-password/index')">
            <view class="menu-item__icon"><PcIcon name="lock" :size="22" color="#11796F" /></view>
            <text class="menu-item__text">修改密码</text>
            <PcIcon name="arrow-right" :size="14" color="#B2B2B2" />
          </view>
        </view>
      </view>

      <!-- 退出登录（demo logout-btn：独立白底卡片） -->
      <view class="logout-btn" @tap="handleLogout">
        <text class="logout-btn__text">退出登录</text>
      </view>
    </template>

    <PcBottomNav current-path="pages/profile/index" />
  </view>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PcIcon from '@/components/PcIcon.vue'
import PcBottomNav from '@/components/PcBottomNav.vue'
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'
import { useUserStore } from '@/store/user'
import { getUnreadCount } from '@/api/notification'
import { assetFullUrl } from '@/utils/asset-url'

const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)
const unreadCount = ref(0)

const displayName = computed(() => userStore.profile?.nickname || '用户')
const displayPhone = computed(() => userStore.profile?.phone || '')
const avatarUrl = computed(() => {
  const url = userStore.profile?.avatarUrl
  if (!url) return null
  return assetFullUrl(url)
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
  /* demo：无全局 padding，各模块自带 margin */
  padding: 0 0;
}

/* 底部留白 */
/* #ifdef H5 */
.profile-page {
  padding-bottom: 192rpx;
}
/* #endif */
/* #ifdef MP-WEIXIN */
.profile-page {
  padding-bottom: 32rpx;
}
/* #endif */

/* ─── 用户卡（demo profile-user）─── */
.profile-user {
  display: flex;
  align-items: center;
  gap: 28rpx;
  margin: 24rpx 32rpx 24rpx;
  padding: 32rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
}

.profile-avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: #DFF2ED;
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
  font-size: 48rpx;
  color: #11796F;
  font-weight: 600;
}

.profile-user__info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.profile-user__name {
  font-size: 32rpx;
  font-weight: 600;
  color: #1A1A1A;
}

.profile-user__phone {
  font-size: 24rpx;
  color: #999999;
}

/* ─── 菜单分组（demo menu-group）─── */
.menu-group {
  margin: 0 32rpx 24rpx;
}

.menu-group__title {
  display: block;
  font-size: 24rpx;
  color: #999999;
  padding: 16rpx 8rpx;
}

.menu-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  overflow: hidden;
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 28rpx;
}

.menu-item + .menu-item {
  border-top: 1rpx solid #E5E5E5;
}

.menu-item__icon {
  width: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 24rpx;
}

.menu-item__text {
  flex: 1;
  font-size: 30rpx;
  color: #333333;
}

/* 消息通知红角标 */
.menu-item__badge {
  background: #FA5151;
  color: #FFFFFF;
  font-size: 20rpx;
  min-width: 32rpx;
  height: 32rpx;
  border-radius: 999rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 8rpx;
  margin-right: 8rpx;
  font-weight: 600;
}

/* ─── 退出登录（demo logout-btn：独立白底卡片）─── */
.logout-btn {
  margin: 48rpx 32rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
  padding: 28rpx;
  text-align: center;
}

.logout-btn:active {
  opacity: 0.85;
}

.logout-btn__text {
  color: #FA5151;
  font-size: 30rpx;
}

/* ─── 未登录态（简化为 demo 风格）─── */
.profile-login {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;
  margin: 24rpx 32rpx;
  padding: 48rpx 32rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
}

.profile-login__avatar {
  width: 112rpx;
  height: 112rpx;
  border-radius: 50%;
  background: #DFF2ED;
  display: flex;
  align-items: center;
  justify-content: center;
}

.profile-login__title {
  font-size: 36rpx;
  font-weight: 600;
  color: #1A1A1A;
}

.profile-login__hint {
  max-width: 520rpx;
  font-size: 24rpx;
  line-height: 1.6;
  color: #999999;
  text-align: center;
}

.profile-login__btn {
  width: 100%;
  margin-top: 8rpx;
}

.profile-login__links {
  display: flex;
  gap: 40rpx;
  margin-top: 8rpx;
}

.profile-login__link text {
  font-size: 26rpx;
  color: #11796F;
}

.profile-login__link:active {
  opacity: 0.6;
}
</style>
