<template>
  <view class="pc-page products-page">
    <!-- 搜索栏（demo search-bar；V2 改版：营销通栏移除，进页即搜索） -->
    <view class="products-search">
      <view class="products-search__input">
        <PcIcon name="search" :size="16" color="#B2B2B2" />
        <input
          v-model="searchKeyword"
          class="products-search__field"
          type="text"
          confirm-type="search"
          placeholder="搜索商品名称"
          placeholder-class="products-search__ph"
          @confirm="handleSearch"
        />
      </view>
      <view class="products-search__btn" @tap="handleSearch">
        <text>搜索</text>
      </view>
    </view>

    <!-- 分类行 + 购物车入口（V2 改版：购物车由悬浮 FAB 改为分类行右侧胶囊，不再遮挡商品卡） -->
    <view class="products-toolbar">
      <scroll-view class="products-tabs" scroll-x>
        <view class="products-tabs__track">
          <view
            class="products-tab"
            :class="{ 'products-tab--active': activeCategoryId === '' }"
            @tap="switchCategory('')"
          >
            <text>全部</text>
          </view>
          <view
            v-for="cat in categories"
            :key="cat.id"
            class="products-tab"
            :class="{ 'products-tab--active': activeCategoryId === cat.id }"
            @tap="switchCategory(cat.id)"
          >
            <text>{{ cat.name }}</text>
          </view>
        </view>
      </scroll-view>
      <view class="products-cart-pill" @tap="goCart">
        <PcIcon name="cart" :size="16" color="#333333" />
        <text class="products-cart-pill__text">购物车</text>
        <view v-if="cartCount > 0" class="products-cart-pill__badge">
          <text>{{ cartCount > 99 ? '99+' : cartCount }}</text>
        </view>
      </view>
    </view>

    <PcStatePanel
      :status="listStatus"
      empty-icon="🛍️"
      :empty-text="emptyText"
      :empty-hint="emptyHint"
      @retry="loadProducts"
    >
      <view class="products-grid">
        <PcProductCard
          v-for="item in products"
          :key="item.id"
          :product-id="item.id"
          :name="item.name"
          :price="item.price"
          :cover-url="item.coverUrl"
          @press="goDetail(item.id)"
        />
      </view>
    </PcStatePanel>
    <PcBottomNav current-path="pages/products/index" />
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import PcIcon from '@/components/PcIcon.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import PcProductCard from '@/components/PcProductCard.vue'
import PcBottomNav from '@/components/PcBottomNav.vue'
import { getProducts, getProductCategories } from '@/api/product'
import { getCartItems } from '@/api/cart'
import { useUserStore } from '@/store/user'
import type { ProductItem, ProductCategory } from '@/types/product'

const userStore = useUserStore()
const listStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const products = ref<ProductItem[]>([])
const categories = ref<ProductCategory[]>([])
const activeCategoryId = ref('')
const searchKeyword = ref('')
const cartCount = ref(0)

/** Whether the user is filtering by category or keyword — affects empty-state copy. */
const hasFilter = computed(() => !!activeCategoryId.value || !!searchKeyword.value.trim())
const emptyText = computed(() => hasFilter.value ? '没有找到相关商品' : '暂无商品')
const emptyHint = computed(() =>
  hasFilter.value ? '换个分类或关键词试试' : '新商品上架后会在这里展示'
)

async function loadCategories() {
  try {
    const res = await getProductCategories()
    if (res.success && res.data) {
      categories.value = res.data
    }
  } catch {
    categories.value = []
  }
}

async function loadProducts() {
  listStatus.value = 'loading'

  const params: { size: number; categoryId?: string; keyword?: string } = { size: 50 }
  if (activeCategoryId.value) params.categoryId = activeCategoryId.value
  const keyword = searchKeyword.value.trim()
  if (keyword) params.keyword = keyword

  try {
    const res = await getProducts(params)
    if (!res.success || !res.data) {
      listStatus.value = 'error'
      return
    }

    products.value = res.data.items
    listStatus.value = products.value.length > 0 ? 'success' : 'empty'
  } catch {
    listStatus.value = 'error'
  }
}

function switchCategory(categoryId: string) {
  if (activeCategoryId.value === categoryId) return
  activeCategoryId.value = categoryId
  loadProducts()
}

function handleSearch() {
  loadProducts()
}

function goDetail(id: string) {
  uni.navigateTo({ url: `/pages/products/detail?id=${id}` })
}

function goCart() {
  uni.navigateTo({ url: '/pages/order/cart' })
}

/** Reload cart item count for the floating cart badge. Only for logged-in users. */
async function loadCartCount() {
  if (!userStore.isLoggedIn) {
    cartCount.value = 0
    return
  }
  try {
    const res = await getCartItems()
    if (res.success && res.data) {
      cartCount.value = res.data.reduce((sum, item) => sum + item.quantity, 0)
    }
  } catch {
    cartCount.value = 0
  }
}

onLoad(() => {
  loadCategories()
  loadProducts()
  loadCartCount()
})

// Refresh cart count when returning from cart/detail pages (after add-to-cart)
onShow(() => {
  loadCartCount()
})
</script>

<style scoped>
.products-page {
  /* hero/搜索/pills 通栏贴边；grid 由下方规则补留白 */
  padding: 0 0;
}

/* 底部留白 */
/* #ifdef H5 */
.products-page {
  padding-bottom: 192rpx;
}
/* #endif */
/* #ifdef MP-WEIXIN */
.products-page {
  padding-bottom: 32rpx;
}
/* #endif */

/* ─── 搜索栏（demo search-bar）─── */
.products-search {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 32rpx;
}

.products-search__input {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: #FFFFFF;
  border-radius: 12rpx;
  padding: 14rpx 24rpx;
}

.products-search__field {
  flex: 1;
  font-size: 26rpx;
  color: #333333;
}

.products-search__ph {
  color: #B2B2B2;
  font-size: 26rpx;
}

.products-search__btn {
  background: #11796F;
  border-radius: 8rpx;
  padding: 14rpx 32rpx;
  flex-shrink: 0;
}

.products-search__btn:active {
  opacity: 0.85;
}

.products-search__btn text {
  color: #FFFFFF;
  font-size: 26rpx;
}

/* ─── 分类行 + 购物车胶囊（V2 改版）─── */
.products-toolbar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 0 32rpx 24rpx;
}

.products-tabs {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  box-sizing: border-box;
}

.products-cart-pill {
  display: flex;
  align-items: center;
  gap: 8rpx;
  height: 60rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  border: 1rpx solid #E5E5E5;
  flex-shrink: 0;
}

.products-cart-pill:active {
  opacity: 0.8;
}

.products-cart-pill__text {
  font-size: 24rpx;
  color: #333333;
}

.products-cart-pill__badge {
  min-width: 30rpx;
  height: 30rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: #FA5151;
  display: flex;
  align-items: center;
  justify-content: center;
}

.products-cart-pill__badge text {
  font-size: 18rpx;
  font-weight: 600;
  color: #FFFFFF;
  line-height: 1;
}

/* ─── 分类 pills（demo cat-pills：8rpx 圆角小 pill）─── */
.products-tabs__track {
  display: inline-flex;
  gap: 16rpx;
}

.products-tab {
  display: inline-flex;
  align-items: center;
  padding: 12rpx 28rpx;
  border: 1rpx solid #E5E5E5;
  border-radius: 8rpx;
  background: #FFFFFF;
  flex-shrink: 0;
}

.products-tab text {
  color: #666666;
  font-size: 26rpx;
  font-weight: 400;
}

.products-tab--active {
  background: #11796F;
  border-color: #11796F;
}

.products-tab--active text {
  color: #FFFFFF;
}

/* ─── 商品网格（demo prod-grid：gap 10px）─── */
.products-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20rpx;
  padding: 0 32rpx;
}
</style>
