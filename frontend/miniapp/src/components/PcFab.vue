<template>
  <view class="pc-fab" @tap="$emit('press')">
    <!-- 图标：优先用 slot，否则用 icon prop（PcIcon name） -->
    <slot name="icon">
      <PcIcon v-if="icon" :name="icon" :size="22" color="#FFFFFF" />
    </slot>
    <!-- 数字角标 -->
    <view v-if="badge" class="pc-fab__badge">
      <text class="pc-fab__badge-text">{{ badgeText }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import PcIcon from '@/components/PcIcon.vue'

const props = defineProps<{
  /** PcIcon 图标名（如 'robot'、'edit'、'cart'） */
  icon?: string
  /** 角标数字，>99 显示 99+ */
  badge?: number | string
}>()

defineEmits<{
  (e: 'press'): void
}>()

const badgeText = computed(() => {
  const n = Number(props.badge)
  if (Number.isNaN(n)) return String(props.badge)
  return n > 99 ? '99+' : String(n)
})
</script>

<style scoped>
/* demo .fab：右下角固定圆形浮动按钮
   48px ≈ 96rpx；bottom 76px ≈ 152rpx（含 tabBar 高度） */
.pc-fab {
  position: fixed;
  right: 32rpx;
  /* 避开原生 tabBar（高 140rpx）+ 安全间距 24rpx ≈ 164rpx；
     小程序守卫禁止 env()，tabBar 自身已处理 safe-area */
  bottom: 164rpx;
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: var(--pc-user-primary, #11796F);
  color: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04), 0 8rpx 24rpx rgba(17, 121, 111, 0.3);
  z-index: 150;
}

.pc-fab:active {
  transform: scale(0.92);
  opacity: 0.9;
}

.pc-fab__badge {
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: #FA5151;
  border: 3rpx solid #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
}

.pc-fab__badge-text {
  font-size: 20rpx;
  font-weight: 600;
  color: #FFFFFF;
  line-height: 1;
}
</style>
