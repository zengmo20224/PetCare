<template>
  <view class="pc-blocked-feature">
    <text class="pc-blocked-feature__icon">&#x1F512;</text>
    <text class="pc-blocked-feature__title">{{ title }}</text>
    <text v-if="reason" class="pc-blocked-feature__reason">{{ reason }}</text>
  </view>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  title: string
  reason?: string
}>(), {
  reason: '',
})
</script>

<style scoped>
.pc-blocked-feature {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 48rpx 32rpx;
  background: var(--pc-user-surface);
  border-radius: 32rpx;
  gap: 16rpx;
}

.pc-blocked-feature__icon {
  font-size: 56rpx;
}

.pc-blocked-feature__title {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--pc-user-ink);
}

.pc-blocked-feature__reason {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}
</style>
