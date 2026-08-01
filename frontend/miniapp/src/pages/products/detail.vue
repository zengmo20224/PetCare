<template>
  <view class="pc-page product-detail">
    <PcPageHeader title="">
      <template #action>
        <view class="detail-cart-btn" @tap="goCart">
          <text class="detail-cart-btn__text">购物车</text>
        </view>
      </template>
    </PcPageHeader>

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
          <view v-if="detailImages.length > 0" class="detail-desc__images" :class="imagesLayoutClass(detailImages.length)">
            <view
              v-for="(url, index) in detailImages"
              :key="index"
              class="detail-desc__image-wrap"
              @tap="previewDetailImage(index)"
            >
              <image class="detail-desc__image" :src="url" mode="aspectFill" />
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

/** Adaptive grid layout class by image count (same rules as community detail). */
function imagesLayoutClass(count: number): string {
  if (count === 1) return 'detail-desc__images--single'
  if (count === 2) return 'detail-desc__images--double'
  if (count === 4) return 'detail-desc__images--four'
  return 'detail-desc__images--grid3'
}

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

function goCart() {
  uni.navigateTo({ url: '/pages/order/cart' })
}

onLoad((query) => {
  loadDetail(query?.id)
})
</script>

<style scoped>
.product-detail {
  padding: 0 0 0;
}

.detail-cart-btn {
  padding: 6px 12px;
  border-radius: 999px;
  background: #DFF2ED;
}

.detail-cart-btn__text {
  color: #11796F;
  font-size: 11px;
  font-weight: 700;
}

/* Gallery swiper */
.detail-gallery {
  position: relative;
  width: 100%;
  background: #F3F7F5;
}

.detail-gallery__swiper {
  width: 100%;
  height: 320px;
}

.detail-gallery__image {
  width: 100%;
  height: 100%;
  cursor: pointer;
}

.detail-gallery__count {
  position: absolute;
  right: 12px;
  bottom: 12px;
  background: rgba(0, 0, 0, 0.5);
  border-radius: 12px;
  padding: 2px 10px;
}

.detail-gallery__count text {
  font-size: 12px;
  color: #fff;
}

/* Price block */
.detail-price-block {
  display: flex;
  align-items: baseline;
  gap: 10px;
  padding: 16px 16px 8px;
}

.detail-price {
  font-size: 24px;
  color: #E97951;
  font-weight: 800;
}

.detail-price__sales {
  font-size: 11px;
  color: #71817D;
}

/* Title */
.detail-title {
  display: block;
  width: 100%;
  padding: 0 16px;
  font-size: 24px;
  font-weight: 700;
  color: #19322E;
  line-height: 1.4;
  margin-bottom: 12px;
}

/* Sub info */
.detail-sub {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  padding: 12px 16px;
  background: #fff;
  border: 1px solid #E2E9E6;
  margin: 0 16px 16px;
  border-radius: 20px;
  box-shadow: 0 8px 24px rgba(25, 50, 46, 0.08);
}

.detail-sub__item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.detail-sub__label {
  font-size: 11px;
  color: #71817D;
}

.detail-sub__value {
  font-size: 14px;
  color: #19322E;
}

.detail-sub__tag {
  background: rgba(43, 122, 120, 0.1);
  border-radius: 4px;
  padding: 2px 8px;
}

.detail-sub__tag text {
  font-size: 11px;
  color: #11796F;
  font-weight: 600;
}

/* Description */
.detail-desc {
  padding: 16px;
  margin: 0 16px 16px;
  background: #fff;
  border: 1px solid #E2E9E6;
  border-radius: 20px;
  box-shadow: 0 8px 24px rgba(25, 50, 46, 0.08);
}

.detail-desc__title {
  display: block;
  font-size: 14px;
  font-weight: 700;
  color: #19322E;
  margin-bottom: 10px;
}

.detail-desc__content {
  display: block;
  width: 100%;
  font-size: 14px;
  color: #19322E;
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  margin-bottom: 12px;
}

/* Description image grid (mixed text+image layout) */
.detail-desc__images {
  display: grid;
  gap: 6px;
}

.detail-desc__images--single {
  grid-template-columns: 1fr;
}

.detail-desc__images--double {
  grid-template-columns: 1fr 1fr;
}

.detail-desc__images--four {
  grid-template-columns: 1fr 1fr;
}

.detail-desc__images--grid3 {
  grid-template-columns: 1fr 1fr 1fr;
}

.detail-desc__image-wrap {
  width: 100%;
  height: 104px;
  border-radius: 6px;
  overflow: hidden;
  background: #f5f5f5;
}

.detail-desc__images--single .detail-desc__image-wrap {
  height: 180px;
}

.detail-desc__image {
  width: 100%;
  height: 100%;
  cursor: pointer;
}

/* Spacer so content isn't hidden behind fixed bar */
.detail-bottom-spacer {
  height: 80px;
}

/* Fixed bottom action bar */
.detail-action-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  background: #fff;
  border-top: 1px solid #E2E9E6;
  box-shadow: 0 -2px 8px rgba(25, 50, 46, 0.06);
  z-index: 100;
}

.detail-qty {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.detail-qty__btn {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  background: #DFF2ED;
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-qty__btn text {
  font-size: 18px;
  color: #11796F;
  line-height: 1;
}

.detail-qty__val {
  font-size: 14px;
  font-weight: 600;
  color: #19322E;
  min-width: 24px;
  text-align: center;
}

.detail-action-bar__btns {
  flex: 1;
  display: flex;
  gap: 10px;
}

.detail-action-bar__btn {
  flex: 1;
  height: 42px;
  border-radius: 21px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.detail-action-bar__btn--cart {
  background: rgba(43, 122, 120, 0.12);
}

.detail-action-bar__btn--cart text {
  color: #11796F;
  font-size: 14px;
  font-weight: 600;
}

.detail-action-bar__btn--buy {
  background: #11796F;
}

.detail-action-bar__btn--buy text {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.detail-action-bar__btn:active {
  opacity: 0.85;
}
</style>
