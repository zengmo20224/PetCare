<template>
  <view class="pc-state-panel">
    <!-- Loading -->
    <view v-if="status === 'loading'" class="pc-state-panel__state">
      <view class="pc-state-panel__spinner" />
      <text class="pc-state-panel__text">{{ loadingText }}</text>
    </view>

    <!-- Empty -->
    <view v-else-if="status === 'empty'" class="pc-state-panel__state">
      <text class="pc-state-panel__empty-icon">{{ emptyIcon }}</text>
      <text class="pc-state-panel__text">{{ emptyText }}</text>
      <text v-if="emptyHint" class="pc-state-panel__hint">{{ emptyHint }}</text>
      <slot name="empty-action" />
    </view>

    <!-- Error -->
    <view v-else-if="status === 'error'" class="pc-state-panel__state">
      <text class="pc-state-panel__error-icon">&#x26A0;</text>
      <text class="pc-state-panel__text">{{ sanitizedMessage }}</text>
      <PcPrimaryButton text="重试" @press="$emit('retry')" />
    </view>

    <!-- Blocked -->
    <view v-else-if="status === 'blocked'" class="pc-state-panel__state">
      <text class="pc-state-panel__blocked-icon">&#x1F512;</text>
      <text class="pc-state-panel__text">功能尚未启用</text>
      <text v-if="reason" class="pc-state-panel__reason">{{ reason }}</text>
    </view>

    <!-- Unauthorized -->
    <view v-else-if="status === 'unauthorized'" class="pc-state-panel__state">
      <text class="pc-state-panel__blocked-icon">&#x1F512;</text>
      <text class="pc-state-panel__text">微信登录尚未启用</text>
    </view>

    <!-- Success: render default slot -->
    <slot v-else-if="status === 'success'" />
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import PcPrimaryButton from './PcPrimaryButton.vue'
import { sanitizeErrorMessage } from '@/utils/error-sanitizer'

const props = withDefaults(defineProps<{
  status: 'loading' | 'empty' | 'error' | 'blocked' | 'unauthorized' | 'success'
  loadingText?: string
  emptyText?: string
  emptyIcon?: string
  emptyHint?: string
  errorMessage?: string
  reason?: string
}>(), {
  loadingText: '加载中...',
  emptyText: '暂无内容',
  emptyIcon: '\u{1F4E6}',
  emptyHint: '',
  errorMessage: '',
  reason: '',
})

defineEmits<{
  (e: 'retry'): void
}>()

const sanitizedMessage = computed(() => sanitizeErrorMessage(props.errorMessage))
</script>

<style scoped>
.pc-state-panel {
  width: 100%;
}

.pc-state-panel__state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 96rpx 48rpx;
  gap: 24rpx;
}

.pc-state-panel__spinner {
  width: 64rpx;
  height: 64rpx;
  border: 3px solid var(--pc-user-line);
  border-top-color: var(--pc-user-primary);
  border-radius: 50%;
  animation: pc-spin 0.8s linear infinite;
}

@keyframes pc-spin {
  to { transform: rotate(360deg); }
}

.pc-state-panel__empty-icon,
.pc-state-panel__error-icon,
.pc-state-panel__blocked-icon {
  font-size: 80rpx;
}

.pc-state-panel__text {
  font-size: 28rpx;
  color: var(--pc-user-muted);
  text-align: center;
}

.pc-state-panel__reason {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.pc-state-panel__hint {
  font-size: 24rpx;
  color: var(--pc-user-muted);
  text-align: center;
  max-width: 520rpx;
  line-height: 1.6;
  margin-top: -8rpx;
}
</style>
