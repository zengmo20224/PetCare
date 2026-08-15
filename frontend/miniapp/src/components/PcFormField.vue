<template>
  <view class="pc-form-field">
    <text class="pc-form-field__label">{{ label }}</text>
    <slot />
  </view>
</template>

<script setup lang="ts">
defineProps<{
  label: string
}>()
</script>

<style scoped>
.pc-form-field {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 32rpx;
}

.pc-form-field__label {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--pc-user-ink);
}
</style>
