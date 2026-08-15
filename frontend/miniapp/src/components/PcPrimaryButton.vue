<template>
  <wd-button
    class="pc-primary-button"
    type="primary"
    size="large"
    block
    :disabled="disabled"
    :loading="loading"
    @click="handleClick"
  >
    {{ text }}
    <slot />
  </wd-button>
</template>

<script setup lang="ts">
/**
 * 全局主操作按钮，外层 API（text/loading/disabled/@press）。
 * 内部基于 wot-design-uni 的 wd-button 实现。
 * 主色由 tokens.css 中 --wot-color-theme: #11796F 全局控制。
 *
 * 事件协议：对外只发非原生 press，避免 mp-weixin 父子同名 bindtap 冒泡
 * 导致“一次点击进入两次”。wd-button 内部仍用 click（物理点击），但绝不外泄 tap。
 */
defineProps<{
  text?: string
  disabled?: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'press'): void
}>()

// 桥接：wd-button 的 click → 对外只 emit press
function handleClick() {
  emit('press')
}
</script>

<style scoped>
/* 品牌主色通过设计令牌引用（tokens.css 的 --pc-user-primary），跨 H5/小程序统一 */
.pc-primary-button {
  background: var(--pc-user-primary);
  height: 96rpx;
  min-height: 96rpx;
  border-radius: 32rpx;
  font-size: 32rpx;
  font-weight: 700;
}

/* wot 内部按钮高度对齐 */
:deep(.wd-button) {
  height: 96rpx;
  border-radius: 32rpx;
}
</style>
