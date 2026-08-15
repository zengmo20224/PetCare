<template>
  <view class="pc-page booking-success">
    <view class="booking-success__content">
      <text class="booking-success__icon">&#x2705;</text>
      <text class="booking-success__title">预约提交成功</text>
      <text class="booking-success__hint">我们将尽快确认您的预约</text>
      <PcPrimaryButton text="查看我的预约" @press="goMyBookings" />
    </view>
  </view>
</template>

<script setup lang="ts">
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'

function goMyBookings() {
  uni.navigateTo({ url: '/pages/booking/list' })
}
</script>

<style scoped>
.booking-success {
  padding: 40rpx;
}

.booking-success__content {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding-top: 120rpx;
  gap: 24rpx;
}

.booking-success__icon {
  font-size: 96rpx;
}

.booking-success__title {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
}

.booking-success__hint {
  font-size: 28rpx;
  color: var(--pc-user-muted);
  margin-bottom: 48rpx;
}
</style>
