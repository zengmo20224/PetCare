<template>
  <view class="pc-page order-cart">
    <PcPageHeader title="购物车" />

    <PcStatePanel v-if="!isLoggedIn" status="unauthorized" />

    <PcStatePanel
      v-else
      :status="listStatus"
      empty-text="购物车是空的"
      @retry="loadCart"
    >
      <view class="cart-list">
        <view v-for="item in cartItems" :key="item.id" class="cart-item">
          <!-- Checkbox -->
          <view
            class="cart-check"
            :class="{ 'cart-check--on': item.checked }"
            @tap="toggleCheck(item)"
          >
            <text v-if="item.checked" class="cart-check__mark">✓</text>
          </view>

          <!-- Product image -->
          <view class="cart-item__img-wrap">
            <image v-if="item.productCoverUrl" class="cart-item__img" :src="assetFullUrl(item.productCoverUrl)" mode="aspectFill" />
          </view>

          <!-- Info -->
          <view class="cart-item__body">
            <text class="cart-item__name">{{ item.productName }}</text>
            <text class="cart-item__price">¥{{ item.productPrice }}</text>
            <view class="cart-item__bottom">
              <view class="cart-qty">
                <view class="cart-qty__btn" @tap="changeQty(item, -1)"><text>-</text></view>
                <text class="cart-qty__val">{{ item.quantity }}</text>
                <view class="cart-qty__btn" @tap="changeQty(item, 1)"><text>+</text></view>
              </view>
              <text class="cart-item__remove" @tap.stop="removeItem(item.id)">删除</text>
            </view>
          </view>
        </view>
      </view>

      <!-- Fixed bottom bar -->
      <view v-if="cartItems.length > 0" class="cart-bar">
        <view
          class="cart-check"
          :class="{ 'cart-check--on': allChecked }"
          @tap="toggleAll"
        >
          <text v-if="allChecked" class="cart-check__mark">✓</text>
        </view>
        <text class="cart-bar__all">全选</text>
        <view class="cart-bar__total">
          <text class="cart-bar__total-label">合计</text>
          <text class="cart-bar__total-amount">¥{{ checkedTotal }}</text>
        </view>
        <view
          class="cart-bar__btn"
          :class="{ 'cart-bar__btn--disabled': checkedCount === 0 }"
          @tap="goCheckout"
        >
          <text>去结算{{ checkedCount > 0 ? `(${checkedCount})` : '' }}</text>
        </view>
      </view>

      <view v-if="cartItems.length > 0" class="cart-bar-spacer" />
    </PcStatePanel>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import { getCartItems, updateCartItem, deleteCartItem, checkCartItems } from '@/api/cart'
import { useUserStore } from '@/store/user'
import type { CartItem } from '@/types/product'
import { assetFullUrl } from '@/utils/asset-url'

const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)

const listStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const cartItems = ref<CartItem[]>([])

const checkedItems = computed(() => cartItems.value.filter(i => i.checked))
const checkedCount = computed(() => checkedItems.value.length)
const checkedTotal = computed(() =>
  checkedItems.value.reduce((sum, item) => sum + item.subtotal, 0).toFixed(2)
)
const allChecked = computed(() =>
  cartItems.value.length > 0 && cartItems.value.every(i => i.checked)
)

async function loadCart() {
  listStatus.value = 'loading'
  const res = await getCartItems()
  if (!res.success || !res.data) {
    listStatus.value = 'error'
    return
  }
  cartItems.value = res.data
  listStatus.value = cartItems.value.length > 0 ? 'success' : 'empty'
}

async function toggleCheck(item: CartItem) {
  const res = await checkCartItems([item.id], !item.checked)
  if (res.success) {
    await loadCart()
  }
}

async function toggleAll() {
  const ids = cartItems.value.map(i => i.id)
  const res = await checkCartItems(ids, !allChecked.value)
  if (res.success) {
    await loadCart()
  }
}

async function changeQty(item: CartItem, delta: number) {
  const newQty = item.quantity + delta
  if (newQty < 1) return
  if (newQty > item.productStock) {
    uni.showToast({ title: `库存仅剩 ${item.productStock} 件`, icon: 'none' })
    return
  }
  const res = await updateCartItem(item.id, newQty)
  if (res.success) {
    await loadCart()
  }
}

async function removeItem(id: string) {
  uni.showModal({
    title: '提示',
    content: '确定从购物车删除该商品？',
    success: async (res) => {
      if (!res.confirm) return
      const r = await deleteCartItem(id)
      if (r.success) {
        await loadCart()
      }
    },
  })
}

function goCheckout() {
  if (checkedCount.value === 0) {
    uni.showToast({ title: '请先选择商品', icon: 'none' })
    return
  }
  uni.navigateTo({ url: '/pages/order/confirm' })
}

onMounted(() => {
  if (isLoggedIn.value) loadCart()
})

// Refresh when returning from confirm page (order may have cleared items)
onShow(() => {
  if (isLoggedIn.value) loadCart()
})
</script>

<style scoped>
.order-cart {
  padding: 20px;
}

.cart-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cart-item {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 16px;
  padding: 12px;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

/* Checkbox */
.cart-check {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 1.5px solid #E2E9E6;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cart-check--on {
  background: #11796F;
  border-color: #11796F;
}

.cart-check__mark {
  font-size: 13px;
  color: #fff;
  line-height: 1;
}

/* Product image */
.cart-item__img-wrap {
  width: 72px;
  height: 72px;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
  flex-shrink: 0;
}

.cart-item__img {
  width: 100%;
  height: 100%;
}

/* Info */
.cart-item__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.cart-item__name {
  font-size: 14px;
  font-weight: 600;
  color: #19322E;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.cart-item__price {
  font-size: 14px;
  color: #F5A623;
  font-weight: 600;
}

.cart-item__bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 4px;
}

.cart-qty {
  display: flex;
  align-items: center;
  gap: 10px;
}

.cart-qty__btn {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: #DFF2ED;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cart-qty__btn text {
  font-size: 16px;
  color: #11796F;
  line-height: 1;
}

.cart-qty__val {
  font-size: 14px;
  color: #19322E;
  font-weight: 600;
  min-width: 22px;
  text-align: center;
}

.cart-item__remove {
  font-size: 11px;
  color: #e05050;
}

/* Bottom bar */
.cart-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  background: #fff;
  border-top: 1px solid #E2E9E6;
  box-shadow: 0 -2px 8px rgba(25, 50, 46, 0.06);
  z-index: 100;
}

.cart-bar__all {
  font-size: 14px;
  color: #19322E;
  flex-shrink: 0;
}

.cart-bar__total {
  flex: 1;
  display: flex;
  align-items: baseline;
  gap: 4px;
  justify-content: flex-end;
}

.cart-bar__total-label {
  font-size: 14px;
  color: #71817D;
}

.cart-bar__total-amount {
  font-size: 18px;
  font-weight: 700;
  color: #F5A623;
}

.cart-bar__btn {
  padding: 0 24px;
  height: 40px;
  border-radius: 20px;
  background: #11796F;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.cart-bar__btn text {
  color: #fff;
  font-size: 14px;
  font-weight: 600;
}

.cart-bar__btn--disabled {
  background: #E2E9E6;
}

.cart-bar__btn--disabled text {
  color: #71817D;
}

.cart-bar-spacer {
  height: 70px;
}
</style>
