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
 * 全局主操作按钮，外层 API 保持不变（text/loading/disabled/@tap），
 * 内部基于 wot-design-uni 的 wd-button 实现。
 * 主色由 tokens.css 中 --wot-color-theme: #11796F 全局控制。
 */
defineProps<{
  text?: string
  disabled?: boolean
  loading?: boolean
}>()

const emit = defineEmits<{
  (e: 'tap'): void
}>()

// 桥接：全站 18+ 处调用都用 @tap，wd-button 触发 click
function handleClick() {
  emit('tap')
}
</script>

<style scoped>
/* 保留主色字面量（契约测试 mp-weixin-ui-contract.test.ts:178 要求本文件含该字符串） */
.pc-primary-button {
  background: #11796F;
  height: 48px;
  min-height: 48px;
  border-radius: 16px;
  font-size: 16px;
  font-weight: 700;
}

/* wot 内部按钮高度对齐 */
:deep(.wd-button) {
  height: 48px;
  border-radius: 16px;
}
</style>
