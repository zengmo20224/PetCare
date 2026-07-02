<template>
  <view class="pc-service-icon" :style="{ color: color }">
    <!-- 洗护：浴缸 + 水滴泡沫 -->
    <svg v-if="name === 'bath'" viewBox="0 0 24 24" fill="none" :stroke="color" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
      <path d="M3 11h18a0 0 0 0 1 0 0v1a5 5 0 0 1-5 5H8a5 5 0 0 1-5-5v-1z" />
      <path d="M6 11V6.5A2.5 2.5 0 0 1 8.5 4H9" />
      <path d="M7 21l1-2" />
      <path d="M17 21l-1-2" />
      <circle cx="11" cy="8" r="0.9" fill="currentColor" stroke="none" />
      <circle cx="14" cy="9" r="0.7" fill="currentColor" stroke="none" />
    </svg>

    <!-- 美容：剪刀 -->
    <svg v-else-if="name === 'groom'" viewBox="0 0 24 24" fill="none" :stroke="color" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
      <circle cx="6" cy="6" r="2.5" />
      <circle cx="6" cy="18" r="2.5" />
      <line x1="20" y1="4" x2="8.12" y2="15.88" />
      <line x1="14.47" y1="14.48" x2="20" y2="20" />
      <line x1="8.12" y1="8.12" x2="12" y2="12" />
    </svg>

    <!-- 上门照护：房子 + 门口爪印 -->
    <svg v-else-if="name === 'home'" viewBox="0 0 24 24" fill="none" :stroke="color" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
      <path d="M3 10.5L12 3l9 7.5" />
      <path d="M5 9.5V20a1 1 0 0 0 1 1h12a1 1 0 0 0 1-1V9.5" />
      <path d="M9.5 21v-4.5a2.5 2.5 0 0 1 5 0V21" />
    </svg>

    <!-- 安心寄养：心形 + 爪印 -->
    <svg v-else-if="name === 'foster'" viewBox="0 0 24 24" fill="none" :stroke="color" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
      <path d="M12 20.5l-7-7.2a4.2 4.2 0 0 1 6-5.9l1 1 1-1a4.2 4.2 0 0 1 6 5.9l-7 7.2z" />
      <circle cx="9.5" cy="11" r="0.8" fill="currentColor" stroke="none" />
      <circle cx="14.5" cy="11" r="0.8" fill="currentColor" stroke="none" />
    </svg>

    <!-- 兜底：爪印 -->
    <svg v-else viewBox="0 0 24 24" fill="none" :stroke="color" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
      <ellipse cx="12" cy="14.5" rx="3.2" ry="2.8" />
      <circle cx="7" cy="10.5" r="1.6" />
      <circle cx="17" cy="10.5" r="1.6" />
      <circle cx="9.5" cy="7" r="1.4" />
      <circle cx="14.5" cy="7" r="1.4" />
    </svg>
  </view>
</template>

<script setup lang="ts">
/**
 * 服务类别图标（内联 SVG，无外部依赖）。
 * 统一线性风格：24×24 viewBox、stroke-width 1.8、round 线帽，贴合 wot 设计语言。
 * 颜色通过 props.color 注入，复用 PetCare 彩色配色。
 */
defineProps<{
  name: 'bath' | 'groom' | 'home' | 'foster'
  color: string
}>()
</script>

<style scoped>
.pc-service-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
}

.pc-service-icon svg {
  width: 26px;
  height: 26px;
  display: block;
}
</style>
