<template>
  <wd-tag
    class="pc-status-tag"
    :type="wotType"
    plain
  >
    {{ label }}
  </wd-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

/**
 * 全局状态标签，外层 API 保持不变（label/type），
 * 内部实现替换为 wot-design-uni 的 wd-tag。
 * 原 type 取自 @/types/status 的语义分类，这里映射到 wd-tag 支持的类型。
 */
const props = defineProps<{
  label: string
  type?: string
}>()

// 项目语义 type -> wot-tag type 映射
const wotType = computed(() => {
  const map: Record<string, 'primary' | 'success' | 'warning' | 'danger' | 'default'> = {
    primary: 'primary',
    success: 'success',
    warning: 'warning',
    danger: 'danger',
    info: 'default',
  }
  return map[props.type ?? 'info'] ?? 'default'
})
</script>

<style scoped>
.pc-status-tag {
  font-size: 11px;
  font-weight: 500;
}
</style>
