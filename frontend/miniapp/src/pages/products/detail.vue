<template>
  <view class="pc-page product-detail">
    <PcPageHeader title="" />

    <PcStatePanel
      :status="pageStatus"
      empty-text="商品不存在"
      @retry="loadDetail"
    >
      <template v-if="product">
        <!-- Image swiper -->
        <view v-if="galleryImages.length > 0" class="detail-gallery">
          <swiper
            class="detail-gallery__swiper"
            :indicator-dots="galleryImages.length > 1"
            indicator-color="rgba(255,255,255,0.4)"
            indicator-active-color="#ffffff"
            :autoplay="false"
            :circular="galleryImages.length > 1"
            @change="onSwiperChange"
          >
            <swiper-item v-for="(url, index) in galleryImages" :key="index">
              <image
                class="detail-gallery__image"
                :src="url"
                mode="aspectFill"
                @tap="previewImage(index)"
              />
            </swiper-item>
          </swiper>
          <view v-if="galleryImages.length > 1" class="detail-gallery__count">
            <text>{{ swiperCurrent + 1 }}/{{ galleryImages.length }}</text>
          </view>
        </view>

        <!-- Price section -->
        <view class="detail-price-block">
          <text class="detail-price">{{ priceText }}</text>
          <text v-if="product.salesCount != null" class="detail-price__sales">已售 {{ product.salesCount }} 件</text>
        </view>

        <!-- Title -->
        <text class="detail-title">{{ product.name }}</text>

        <!-- Sub info -->
        <view class="detail-sub">
          <view v-if="product.categoryName" class="detail-sub__item">
            <text class="detail-sub__label">分类</text>
            <text class="detail-sub__value">{{ product.categoryName }}</text>
          </view>
          <view class="detail-sub__item">
            <text class="detail-sub__label">库存</text>
            <text class="detail-sub__value">{{ product.stock }} 件</text>
          </view>
          <view v-if="product.pickupOnly === 1" class="detail-sub__tag">
            <text>仅限自提</text>
          </view>
        </view>

        <!-- Description (text + image mixed layout) -->
        <view v-if="product.description || detailImages.length > 0" class="detail-desc">
          <text class="detail-desc__title">商品详情</text>
          <text v-if="product.description" class="detail-desc__content" decode>{{ product.description }}</text>
          <view v-if="detailImages.length > 0" class="detail-desc__images">
            <view
              v-for="(url, index) in detailImages"
              :key="index"
              class="detail-desc__image-wrap"
              @tap="previewDetailImage(index)"
            >
              <image class="detail-desc__image" :src="url" mode="widthFix" />
            </view>
          </view>
        </view>

        <!-- Spacer for fixed bottom bar -->
        <view class="detail-bottom-spacer" />

        <!-- Fixed bottom action bar -->
        <view class="detail-action-bar">
          <!-- Quantity stepper -->
          <view class="detail-qty">
            <view class="detail-qty__btn" @tap="changeQty(-1)"><text>-</text></view>
            <text class="detail-qty__val">{{ quantity }}</text>
            <view class="detail-qty__btn" @tap="changeQty(1)"><text>+</text></view>
          </view>
          <view class="detail-action-bar__btns">
            <view class="detail-action-bar__btn detail-action-bar__btn--cart" @tap="addToCart">
              <text>{{ addingToCart ? '...' : '加入购物车' }}</text>
            </view>
            <view class="detail-action-bar__btn detail-action-bar__btn--buy" @tap="buyNow">
              <text>{{ buyingNow ? '...' : '立即购买' }}</text>
            </view>
          </view>
        </view>
      </template>
    </PcStatePanel>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import { getProductDetail } from '@/api/product'
import { addCartItem, getCartItems, checkCartItems } from '@/api/cart'
import { useUserStore } from '@/store/user'
import type { ProductDetail } from '@/types/product'
import { formatYuan } from '@/utils/format'
import { getProductVisual } from '@/utils/product-visual'
import { normalizeRouteParam } from '@/utils/route-query'
import { assetFullUrl } from '@/utils/asset-url'

const PRODUCT_DETAIL_CAROUSEL_LIMIT = 5
const userStore = useUserStore()

const product = ref<ProductDetail | null>(null)
const pageStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const quantity = ref(1)
const swiperCurrent = ref(0)
const addingToCart = ref(false)
const buyingNow = ref(false)
const currentProductId = ref('')

const priceText = computed(() => formatYuan(product.value?.price ?? 0))

/**
 * Build gallery images: prefer real imageUrls, fallback to coverUrl.
 * When the product has no images at all, use placeholder demo images so the
 * swiper layout can be previewed (real images will be added by back-office later).
 */
const galleryImages = computed(() => {
  if (!product.value) return []
  const realUrls: string[] = []
  if (product.value.imageUrls && product.value.imageUrls.length > 0) {
    realUrls.push(...product.value.imageUrls)
  } else if (product.value.coverUrl) {
    realUrls.push(product.value.coverUrl)
  }
  if (realUrls.length > 0) {
    return realUrls
      .slice(0, PRODUCT_DETAIL_CAROUSEL_LIMIT)
      .map(u => assetFullUrl(u))
  }
  const fallback = getProductVisual(product.value.id)
  return fallback ? [fallback] : []
})

/**
 * Real product images for the description section (no placeholders).
 * These are shown below the description text in a mixed text+image layout.
 */
const detailImages = computed(() => {
  if (!product.value) return []
  const urls: string[] = []
  if (product.value.detailImageUrls && product.value.detailImageUrls.length > 0) {
    urls.push(...product.value.detailImageUrls)
  }
  return urls.map(u => assetFullUrl(u))
})

function onSwiperChange(e: any) {
  swiperCurrent.value = e.detail.current
}

function previewImage(index: number) {
  if (galleryImages.value.length === 0) return
  uni.previewImage({
    current: galleryImages.value[index],
    urls: galleryImages.value,
  })
}

/** Preview an image from the description section grid. */
function previewDetailImage(index: number) {
  if (detailImages.value.length === 0) return
  uni.previewImage({
    current: detailImages.value[index],
    urls: detailImages.value,
  })
}

/** 介绍图统一直接排列（与预约服务详情一致：单张通栏、widthFix 保持比例）。 */
function changeQty(delta: number) {
  const next = quantity.value + delta
  const max = product.value?.stock ?? 1
  if (next < 1) return
  if (next > max) {
    uni.showToast({ title: `库存仅剩 ${max} 件`, icon: 'none' })
    return
  }
  quantity.value = next
}

async function loadDetail(routeId?: unknown) {
  const id = normalizeRouteParam(routeId ?? currentProductId.value)

  if (!id) {
    product.value = null
    pageStatus.value = 'empty'
    return
  }

  currentProductId.value = id
  pageStatus.value = 'loading'
  const res = await getProductDetail(id)

  if (!res.success || !res.data) {
    product.value = null
    pageStatus.value = 'error'
    return
  }

  product.value = res.data
  quantity.value = 1
  swiperCurrent.value = 0
  pageStatus.value = 'success'
}

async function addToCart() {
  if (!userStore.isLoggedIn) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    return
  }
  if (!product.value) return

  addingToCart.value = true
  const res = await addCartItem(product.value.id, quantity.value)
  addingToCart.value = false

  if (res.success) {
    uni.showToast({ title: '已加入购物车', icon: 'success' })
  }
}

/**
 * Buy now flow — the backend checkout reads cart items with checked=true.
 * So we: (1) add this product to cart, (2) uncheck all other checked items,
 * (3) check only the newly added item, (4) navigate to confirm page.
 * This mirrors how major e-commerce apps handle "buy now" as a transient cart state.
 */
async function buyNow() {
  if (!userStore.isLoggedIn) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    return
  }
  if (!product.value) return

  buyingNow.value = true
  try {
    // 1. Add to cart
    const addRes = await addCartItem(product.value.id, quantity.value)
    if (!addRes.success || !addRes.data) {
      uni.showToast({ title: '操作失败，请稍后重试', icon: 'none' })
      return
    }
    const newItemId = addRes.data.id

    // 2. Load current cart to find other checked items
    const cartRes = await getCartItems()
    if (cartRes.success && cartRes.data) {
      const otherCheckedIds = cartRes.data
        .filter(item => item.checked && item.id !== newItemId)
        .map(item => item.id)
      // 3a. Uncheck those first
      if (otherCheckedIds.length > 0) {
        await checkCartItems(otherCheckedIds, false)
      }
    }

    // 3b. Check only the new item
    await checkCartItems([newItemId], true)

    // 4. Go to confirm page
    uni.navigateTo({ url: '/pages/order/confirm' })
  } finally {
    buyingNow.value = false
  }
}

onLoad((query) => {
  loadDetail(query?.id)
})
</script>

<style scoped>
.product-detail {
  padding: 0 0 0;
}

/* Gallery swiper */
.detail-gallery {
  position: relative;
  width: 100%;
  background: #F3F7F5;
}

.detail-gallery__swiper {
  width: 100%;
  height: 640rpx;
}

.detail-gallery__image {
  width: 100%;
  height: 100%;
  cursor: pointer;
}

.detail-gallery__count {
  position: absolute;
  right: 24rpx;
  bottom: 24rpx;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 24rpx;
  padding: 4rpx 20rpx;
}

.detail-gallery__count text {
  font-size: 24rpx;
  color: var(--pc-user-surface);
}

/* Price block */
.detail-price-block {
  display: flex;
  align-items: baseline;
  gap: 20rpx;
  padding: 32rpx 32rpx 16rpx;
}

.detail-price {
  font-size: 48rpx;
  color: var(--pc-user-danger);
  font-weight: 800;
}

.detail-price__sales {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

/* Title */
.detail-title {
  display: block;
  width: 100%;
  padding: 0 32rpx;
  font-size: 48rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
  line-height: 1.4;
  margin-bottom: 24rpx;
}

/* Sub info */
.detail-sub {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 32rpx;
  padding: 24rpx 32rpx;
  background: var(--pc-user-surface);
  border: 1px solid var(--pc-user-line);
  margin: 0 32rpx 32rpx;
  border-radius: 40rpx;
  box-shadow: 0 8px 24px rgba(25, 50, 46, 0.08);
}

.detail-sub__item {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.detail-sub__label {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.detail-sub__value {
  font-size: 28rpx;
  color: var(--pc-user-ink);
}

.detail-sub__tag {
  background: rgba(43, 122, 120, 0.1);
  border-radius: 8rpx;
  padding: 4rpx 16rpx;
}

.detail-sub__tag text {
  font-size: 22rpx;
  color: var(--pc-user-primary);
  font-weight: 600;
}

/* Description */
.detail-desc {
  padding: 32rpx;
  margin: 0 32rpx 32rpx;
  background: var(--pc-user-surface);
  border: 1px solid var(--pc-user-line);
  border-radius: 40rpx;
  box-shadow: 0 8px 24px rgba(25, 50, 46, 0.08);
}

.detail-desc__title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
  margin-bottom: 20rpx;
}

.detail-desc__content {
  display: block;
  width: 100%;
  font-size: 28rpx;
  color: var(--pc-user-ink);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  margin-bottom: 24rpx;
}

/* 介绍图：与预约服务详情同款排版——单张通栏、widthFix 自适应高度 */
.detail-desc__images {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16rpx;
}

.detail-desc__image-wrap {
  width: 100%;
  border-radius: 16rpx;
  overflow: hidden;
  background: #f5f5f5;
}

.detail-desc__image {
  width: 100%;
  display: block;
  cursor: pointer;
}

/* Spacer so content isn't hidden behind fixed bar */
.detail-bottom-spacer {
  height: 160rpx;
}

/* Fixed bottom action bar */
.detail-action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 20rpx 32rpx;
  background: var(--pc-user-surface);
  border-top: 1px solid var(--pc-user-line);
  box-shadow: 0 -2px 8px rgba(25, 50, 46, 0.06);
  z-index: 100;
}

.detail-qty {
  display: flex;
  align-items: center;
  gap: 20rpx;
  flex-shrink: 0;
}

.detail-qty__btn {
  width: 60rpx;
  height: 60rpx;
  border-radius: 50%;
  background: var(--pc-user-soft);
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-qty__btn text {
  font-size: 36rpx;
  color: var(--pc-user-primary);
  line-height: 1;
}

.detail-qty__val {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--pc-user-ink);
  min-width: 48rpx;
  text-align: center;
}

.detail-action-bar__btns {
  flex: 1;
  display: flex;
  gap: 20rpx;
}

.detail-action-bar__btn {
  flex: 1;
  height: 84rpx;
  border-radius: 42rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-action-bar__btn--cart {
  background: rgba(43, 122, 120, 0.12);
}

.detail-action-bar__btn--cart text {
  color: var(--pc-user-primary);
  font-size: 28rpx;
  font-weight: 600;
}

.detail-action-bar__btn--buy {
  background: var(--pc-user-primary);
}

.detail-action-bar__btn--buy text {
  color: var(--pc-user-surface);
  font-size: 28rpx;
  font-weight: 600;
}

.detail-action-bar__btn:active {
  opacity: 0.85;
}
</style>
