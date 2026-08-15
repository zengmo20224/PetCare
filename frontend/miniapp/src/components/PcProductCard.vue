<template>
  <view class="pc-product-card" @tap="$emit('press')">
    <view class="pc-product-card__image-wrap">
      <image v-if="displayCover" class="pc-product-card__image" :src="displayCover" mode="aspectFill" lazy-load />
      <!-- 无图时纯灰底占位（demo 风格：无文字） -->
      <!-- badge prop 保留以兼容调用方，但网易严选风不再渲染角标 -->
    </view>
    <view class="pc-product-card__info">
      <text class="pc-product-card__name">{{ name }}</text>
      <text v-if="sub" class="pc-product-card__sub">{{ sub }}</text>
      <text class="pc-product-card__price">{{ priceText }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { formatYuan } from '@/utils/format'
import { getProductVisual } from '@/utils/product-visual'
import { assetFullUrl } from '@/utils/asset-url'

const props = defineProps<{
  productId?: string
  name: string
  price: number
  coverUrl?: string | null
  salesCount?: number | null
  /** 网易严选风规格描述行（如 "中大型犬 · 2kg 袋装"） */
  sub?: string
  /** @deprecated 网易严选风不再渲染 badge，保留 prop 仅向后兼容 */
  badge?: string
}>()

defineEmits<{
  (e: 'press'): void
}>()

const priceText = computed(() => formatYuan(props.price))
const displayCover = computed(() => props.coverUrl ? assetFullUrl(props.coverUrl) : getProductVisual(props.productId))
</script>

<style scoped>
/* 网易严选风：统一浅灰底、留白克制、字重降低、去装饰角标 */
.pc-product-card {
  background: var(--pc-user-surface);
  border-radius: 16rpx;
  overflow: hidden;
}

.pc-product-card:active {
  transform: scale(0.985);
}

/* 图片区：统一 #F5F5F5 灰底，替代原彩色渐变 placeholder */
.pc-product-card__image-wrap {
  position: relative;
  width: 100%;
  height: 276rpx;
  background: var(--pc-demo-img-bg);
  overflow: hidden;
}

.pc-product-card__image {
  width: 100%;
  height: 100%;
}

/* 信息区：紧凑留白，网易严选式层次 */
.pc-product-card__info {
  padding: 20rpx 20rpx 24rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.pc-product-card__name {
  font-size: 28rpx;
  font-weight: 500;
  color: var(--pc-demo-ink-1);
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.8em;
}

/* 规格描述行（sub）：克制灰色小字 */
.pc-product-card__sub {
  font-size: 20rpx;
  color: var(--pc-demo-ink-4);
  line-height: 1.4;
}

.pc-product-card__price {
  margin-top: 8rpx;
  font-size: 32rpx;
  font-weight: 500;
  color: var(--pc-demo-price);
}
</style>
