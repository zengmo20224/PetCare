<template>
  <view class="pc-hero-strip">
    <!-- 暖金光晕装饰（mp-weixin 不支持 ::before content，用绝对定位 view 模拟） -->
    <view class="pc-hero-strip__glow" />
    <view class="pc-hero-strip__content">
      <text class="pc-hero-strip__title">
        <text>{{ prefix }}</text>
        <text v-if="highlight" class="pc-hero-strip__highlight">{{ highlight }}</text>
        <text>{{ suffix }}</text>
      </text>
      <text v-if="desc" class="pc-hero-strip__desc">{{ desc }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  /** 完整标题，如 "把每一份牵挂，都妥帖安放在这里" */
  title: string
  /** 需要高亮（#FFD9A0 暖金）的文字片段，必须在 title 中出现 */
  highlight?: string
  /** 副标题 */
  desc?: string
}>()

// 把 title 按 highlight 拆成 前缀 + 高亮 + 后缀 三段
const parts = computed(() => {
  if (!props.highlight || !props.title.includes(props.highlight)) {
    return { prefix: props.title, suffix: '' }
  }
  const idx = props.title.indexOf(props.highlight)
  return {
    prefix: props.title.slice(0, idx),
    suffix: props.title.slice(idx + props.highlight.length)
  }
})

const prefix = computed(() => parts.value.prefix)
const suffix = computed(() => parts.value.suffix)
</script>

<style scoped>
/* demo hero-unified：90px 高、深绿渐变通栏、纯文字温情栏
   rpx 换算：90px ≈ 180rpx（375px 设计稿） */
.pc-hero-strip {
  position: relative;
  overflow: hidden;
  height: 180rpx;
  padding: 40rpx 32rpx;
  background: linear-gradient(135deg, #16877C, #0B3D39);
  border-bottom: 1rpx solid #E5E5E5;
}

/* 暖金径向光晕（对应 demo .hero-home::before） */
.pc-hero-strip__glow {
  position: absolute;
  top: -40rpx;
  right: -30rpx;
  width: 200rpx;
  height: 200rpx;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(255, 217, 160, 0.2) 0%, transparent 70%);
  pointer-events: none;
}

.pc-hero-strip__content {
  position: relative;
  z-index: 1;
  display: flex;
  flex-direction: column;
}

.pc-hero-strip__title {
  font-size: 36rpx;
  font-weight: 700;
  line-height: 1.4;
  color: #FFFFFF;
  text-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.1);
}

.pc-hero-strip__highlight {
  color: #FFD9A0;
}

.pc-hero-strip__desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.5;
  color: rgba(255, 255, 255, 0.9);
}
</style>
