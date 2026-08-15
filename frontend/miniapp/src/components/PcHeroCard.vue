<template>
  <view class="pc-hero-card">
    <view class="pc-hero-card__eyebrow">
      <text class="pc-hero-card__eyebrow-text">专业护理 · 安心陪伴</text>
    </view>
    <view class="pc-hero-card__content">
      <text class="pc-hero-card__title">{{ title }}</text>
      <text v-if="subtitle" class="pc-hero-card__subtitle">{{ subtitle }}</text>
      <slot name="action" />
    </view>
  </view>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  subtitle?: string
}>()
</script>

<style scoped>
.pc-hero-card {
  position: relative;
  overflow: hidden;
  min-height: 348rpx;
  background:
    radial-gradient(circle at 92% 12%, rgba(255, 218, 138, 0.82) 0 104rpx, transparent 106rpx),
    radial-gradient(circle at 84% 62%, rgba(255, 255, 255, 0.16) 0 164rpx, transparent 166rpx),
    linear-gradient(135deg, #16877C, #0B3D39);
  background:
    radial-gradient(circle at 92% 12%, rgba(245, 166, 35, 0.42) 0 104rpx, transparent 106rpx),
    radial-gradient(circle at 84% 62%, rgba(255, 255, 255, 0.12) 0 164rpx, transparent 166rpx),
    linear-gradient(135deg, #16877c, var(--pc-user-dark));
  border-radius: 52rpx;
  padding: 44rpx 44rpx 48rpx;
  color: var(--pc-user-surface);
  box-shadow: 0 18px 42px rgba(12, 77, 72, 0.24);
  box-shadow: 0 16px 40px rgba(12, 77, 72, 0.16);
}

.pc-hero-card__eyebrow {
  display: inline-flex;
  padding: 8rpx 20rpx;
  margin-bottom: 36rpx;
  border: 1px solid rgba(255, 255, 255, 0.2);
  border-radius: 1998rpx;
  background: rgba(255, 255, 255, 0.1);
}

.pc-hero-card__eyebrow-text {
  font-size: 20rpx;
  letter-spacing: 2rpx;
  color: rgba(255, 255, 255, 0.88);
}

.pc-hero-card__content {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.pc-hero-card__title {
  max-width: 520rpx;
  font-size: 54rpx;
  font-weight: 800;
  line-height: 1.25;
  color: var(--pc-user-surface);
}

.pc-hero-card__subtitle {
  max-width: 560rpx;
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.85);
}
</style>
