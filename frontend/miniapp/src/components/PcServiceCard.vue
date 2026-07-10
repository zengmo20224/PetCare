<template>
  <view class="pc-service-card" @tap="$emit('tap')">
    <view class="pc-service-card__image-wrap">
      <image v-if="imageUrl" class="pc-service-card__image" :src="imageUrl" mode="aspectFill" lazy-load />
      <view v-else class="pc-service-card__placeholder">
        <text class="pc-service-card__placeholder-text">{{ placeholderText }}</text>
      </view>
      <view class="pc-service-card__mode">
        <text class="pc-service-card__mode-text">{{ modeLabel }}</text>
      </view>
    </view>
    <view class="pc-service-card__info">
      <text class="pc-service-card__name">{{ name }}</text>
      <view class="pc-service-card__meta">
        <text class="pc-service-card__duration">{{ durationText }}</text>
        <text class="pc-service-card__price">{{ priceText }}</text>
      </view>
      <view class="pc-service-card__action">
        <text class="pc-service-card__action-text">{{ priceFrom ? '选体型预约' : '立即预约' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatDuration, formatYuan } from '@/utils/format'

const props = defineProps<{
  name: string
  mode: string
  durationMinutes?: number
  price?: number
  imageUrl?: string
  priceFrom?: boolean
}>()

defineEmits<{
  (e: 'tap'): void
}>()

const modeLabel = computed(() => {
  const map: Record<string, string> = { STORE: '到店', HOME: '上门', BOTH: '到店/上门' }
  return map[props.mode] ?? props.mode
})

const durationText = computed(() => formatDuration(props.durationMinutes))
const priceText = computed(() => {
  if (props.price == null) return ''
  return props.priceFrom ? `${formatYuan(props.price)}起` : formatYuan(props.price)
})

const placeholderText = computed(() => {
  const map: Record<string, string> = { STORE: '到店', HOME: '上门', BOTH: '服务' }
  return map[props.mode] ?? '服务'
})
</script>

<style scoped>
.pc-service-card {
  background: #fff;
  border: 1px solid rgba(226, 233, 230, 0.75);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 8px 22px rgba(25, 50, 46, 0.08);
}

.pc-service-card:active {
  transform: scale(0.985);
}

.pc-service-card__image-wrap {
  position: relative;
  width: 100%;
  height: 130px;
  background: #fafafa;
  overflow: hidden;
}

.pc-service-card__image {
  width: 100%;
  height: 100%;
}

.pc-service-card__placeholder {
  width: 100%;
  height: 100%;
  background:
    radial-gradient(circle at 78% 20%, rgba(245, 166, 35, 0.28) 0 34px, transparent 35px),
    linear-gradient(135deg, #FFFFFF, #DFF2ED);
  display: flex;
  align-items: center;
  justify-content: center;
}

.pc-service-card__placeholder-text {
  font-size: 28px;
  font-weight: 800;
  color: #11796F;
  opacity: 0.28;
}

/* Mode badge on top of the image */
.pc-service-card__mode {
  position: absolute;
  left: 10px;
  top: 10px;
  padding: 3px 9px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(226, 233, 230, 0.7);
}

.pc-service-card__mode-text {
  color: #0C4D48;
  font-size: 10px;
  font-weight: 700;
}

.pc-service-card__info {
  padding: 12px 13px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pc-service-card__name {
  font-size: 15px;
  font-weight: 600;
  color: #19322E;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.8em;
}

.pc-service-card__meta {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}

.pc-service-card__duration {
  font-size: 11px;
  color: #71817D;
  flex-shrink: 0;
}

.pc-service-card__price {
  font-size: 16px;
  color: #E97951;
  font-weight: 800;
}

.pc-service-card__action {
  height: 32px;
  border-radius: 16px;
  background: #11796F;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 14px rgba(17, 121, 111, 0.2);
}

.pc-service-card__action-text {
  color: #fff;
  font-size: 12px;
  font-weight: 700;
}
</style>
