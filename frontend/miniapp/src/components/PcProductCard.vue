<template>
  <view class="pc-product-card" @tap="$emit('tap')">
    <view class="pc-product-card__image-wrap">
      <image v-if="displayCover" class="pc-product-card__image" :src="displayCover" mode="aspectFill" lazy-load />
      <view v-else class="pc-product-card__placeholder">
        <text class="pc-product-card__placeholder-text">好物</text>
      </view>
      <view v-if="badge" class="pc-product-card__badge">
        <text class="pc-product-card__badge-text">{{ badge }}</text>
      </view>
    </view>
    <view class="pc-product-card__info">
      <text class="pc-product-card__name">{{ name }}</text>
      <view class="pc-product-card__bottom">
        <text class="pc-product-card__price">{{ priceText }}</text>
        <text v-if="salesCount != null" class="pc-product-card__sales">已售 {{ salesCount }}</text>
      </view>
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
  badge?: string
}>()

defineEmits<{
  (e: 'tap'): void
}>()

const priceText = computed(() => formatYuan(props.price))
const displayCover = computed(() => props.coverUrl ? assetFullUrl(props.coverUrl) : getProductVisual(props.productId))
</script>

<style scoped>
.pc-product-card {
  background: #fff;
  border: 1px solid rgba(226, 233, 230, 0.75);
  border-radius: 20px;
  overflow: hidden;
  box-shadow: 0 8px 22px rgba(25, 50, 46, 0.08);
  box-shadow: 0 8px 24px rgba(25, 50, 46, 0.08);
}

.pc-product-card:active {
  transform: scale(0.985);
}

.pc-product-card__image-wrap {
  position: relative;
  width: 100%;
  height: 138px;
  background: #fafafa;
  overflow: hidden;
}

.pc-product-card__image {
  width: 100%;
  height: 100%;
}

.pc-product-card__placeholder {
  width: 100%;
  height: 100%;
  background:
    radial-gradient(circle at 78% 20%, rgba(245, 166, 35, 0.28) 0 34px, transparent 35px),
    linear-gradient(135deg, #FFFFFF, #DFF2ED);
  display: flex;
  align-items: center;
  justify-content: center;
}

.pc-product-card__placeholder-text {
  font-size: 28px;
  font-weight: 800;
  color: #11796F;
  opacity: 0.28;
}

.pc-product-card__badge {
  position: absolute;
  left: 10px;
  top: 10px;
  padding: 3px 9px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.88);
  border: 1px solid rgba(226, 233, 230, 0.7);
}

.pc-product-card__badge-text {
  color: #19322E;
  color: #0C4D48;
  font-size: 10px;
  font-weight: 700;
}

.pc-product-card__info {
  padding: 12px 13px 14px;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pc-product-card__name {
  font-size: 16px;
  font-weight: 600;
  color: #19322E;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.8em;
}

.pc-product-card__bottom {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 6px;
}

.pc-product-card__price {
  font-size: 18px;
  color: #E97951;
  color: #E97951;
  font-weight: 800;
}

.pc-product-card__sales {
  font-size: 11px;
  color: #71817D;
  flex-shrink: 0;
}
</style>
