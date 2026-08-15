<template>
  <view v-if="visible" class="pc-confirm-sheet" @tap="$emit('cancel')">
    <view class="pc-confirm-sheet__panel" @tap.stop>
      <text class="pc-confirm-sheet__title">{{ title }}</text>
      <text class="pc-confirm-sheet__message">{{ message }}</text>
      <view class="pc-confirm-sheet__actions">
        <view class="pc-confirm-sheet__btn pc-confirm-sheet__btn--cancel" @tap="$emit('cancel')">
          <text>取消</text>
        </view>
        <view class="pc-confirm-sheet__btn pc-confirm-sheet__btn--confirm" @tap="$emit('confirm')">
          <text>{{ confirmText }}</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
withDefaults(defineProps<{
  visible: boolean
  title: string
  message: string
  confirmText?: string
}>(), {
  confirmText: '确定',
})

defineEmits<{
  (e: 'confirm'): void
  (e: 'cancel'): void
}>()
</script>

<style scoped>
.pc-confirm-sheet {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: flex-end;
  z-index: 999;
}

.pc-confirm-sheet__panel {
  width: 100%;
  background: var(--pc-user-surface);
  border-radius: 40rpx 40rpx 0 0;
  padding: 48rpx 40rpx;
  padding-bottom: 68rpx;
}

.pc-confirm-sheet__title {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
  margin-bottom: 16rpx;
}

.pc-confirm-sheet__message {
  font-size: 28rpx;
  color: var(--pc-user-muted);
  margin-bottom: 40rpx;
}

.pc-confirm-sheet__actions {
  display: flex;
  gap: 24rpx;
}

.pc-confirm-sheet__btn {
  flex: 1;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 32rpx;
  font-size: 28rpx;
  font-weight: 600;
}

.pc-confirm-sheet__btn--cancel {
  background: var(--pc-user-cream);
  color: var(--pc-user-muted);
}

.pc-confirm-sheet__btn--confirm {
  background: var(--pc-user-primary);
  color: var(--pc-user-surface);
}
</style>
