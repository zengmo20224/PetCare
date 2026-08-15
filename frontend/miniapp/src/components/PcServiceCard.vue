<template>
  <view class="pc-service-card" @tap="$emit('press')">
    <!-- 图片区：有真实封面用照片（aspectFill 裁切），否则回退 demo 风格 #F5F5F5 灰底 + 墨色线性图标 -->
    <view class="pc-service-card__img">
      <image
        v-if="resolvedImage"
        :src="resolvedImage"
        mode="aspectFill"
        class="pc-service-card__photo"
        lazy-load
      />
      <PcIcon v-else :name="iconName" :size="44" color="#3D3D3D" />
    </view>
    <view class="pc-service-card__body">
      <text class="pc-service-card__name">{{ name }}</text>
      <view class="pc-service-card__attr">
        <view class="pc-service-card__attr-item">
          <PcIcon name="clock" :size="13" color="#999999" />
          <text>{{ durationText }}</text>
        </view>
        <view class="pc-service-card__attr-item">
          <PcIcon :name="modeIcon" :size="13" color="#999999" />
          <text>{{ modeLabel }}</text>
        </view>
      </view>
      <view class="pc-service-card__price">
        <text class="pc-service-card__price-main">{{ priceMain }}</text>
        <text v-if="priceUnit" class="pc-service-card__price-unit">{{ priceUnit }}</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import PcIcon from '@/components/PcIcon.vue'
import { assetFullUrl } from '@/utils/asset-url'
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
  (e: 'press'): void
}>()

/** 后端相对路径（/uploads/...）解析为可访问的绝对地址；空值回退图标 */
const resolvedImage = computed(() => assetFullUrl(props.imageUrl))

/** 按服务名关键字映射 PcIcon 图标（demo 风格：bath/scissors/door/paw/bottle） */
const iconName = computed(() => {
  const n = props.name || ''
  if (n.includes('药浴')) return 'bottle'
  if (n.includes('洗护') || n.includes('洗澡')) return 'bath'
  if (n.includes('美容') || n.includes('造型') || n.includes('修剪')) return 'scissors'
  if (n.includes('上门') || n.includes('到家')) return 'door'
  if (n.includes('寄养') || n.includes('托管')) return 'paw'
  return 'scissors' // 兜底
})

/** 按 serviceMode 映射 attr 地点图标 */
const modeIcon = computed(() => {
  if (props.mode === 'HOME') return 'door'
  return 'shop' // STORE / BOTH 都用 shop
})

const modeLabel = computed(() => {
  const map: Record<string, string> = { STORE: '到店', HOME: '上门', BOTH: '到店/上门' }
  return map[props.mode] ?? props.mode
})

const durationText = computed(() => formatDuration(props.durationMinutes))

const priceMain = computed(() => {
  if (props.price == null) return ''
  return formatYuan(props.price)
})

const priceUnit = computed(() => {
  if (props.priceFrom) return '起'
  // 寄养类按天计价
  if ((props.name || '').includes('寄养') || (props.name || '').includes('托管')) return '/天'
  return ''
})
</script>

<style scoped>
/* demo svc-card：白底、8px 圆角、无阴影无边框 */
.pc-service-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  overflow: hidden;
}

.pc-service-card:active {
  transform: scale(0.98);
}

/* 图片区：统一 #F5F5F5 灰底，居中 PcIcon 墨色图标 */
.pc-service-card__img {
  width: 100%;
  height: 260rpx; /* demo 130px */
  background: #F5F5F5;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 真实封面照片：铺满图片区，aspectFill 裁切 */
.pc-service-card__photo {
  width: 100%;
  height: 100%;
  display: block;
}

.pc-service-card__body {
  padding: 20rpx; /* demo 10px */
}

.pc-service-card__name {
  font-size: 30rpx; /* demo 15px */
  font-weight: 500;
  color: #1A1A1A;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.pc-service-card__attr {
  display: flex;
  gap: 16rpx;
  margin-top: 8rpx;
}

.pc-service-card__attr-item {
  display: inline-flex;
  align-items: center;
  gap: 4rpx;
  font-size: 22rpx; /* demo 11px */
  color: #999999;
}

.pc-service-card__price {
  display: flex;
  align-items: baseline;
  gap: 4rpx;
  margin-top: 12rpx;
}

.pc-service-card__price-main {
  font-size: 32rpx; /* demo 16px */
  font-weight: 500;
  color: #FA5151;
}

.pc-service-card__price-unit {
  font-size: 22rpx;
  font-weight: 400;
  color: #999999;
}
</style>
