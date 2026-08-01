<template>
  <view class="pc-page order-confirm">
    <PcPageHeader title="确认订单" />

    <PcStatePanel :status="pageStatus" empty-text="没有可结算的商品">
      <template v-if="checkedItems.length > 0">
        <!-- Items Summary -->
        <view class="confirm-section">
          <text class="confirm-label">商品清单</text>
          <view class="confirm-items">
            <view v-for="item in checkedItems" :key="item.id" class="confirm-item">
              <view class="confirm-item__img-wrap">
                <image v-if="item.productCoverUrl" class="confirm-item__img" :src="fullUrl(item.productCoverUrl)" mode="aspectFill" />
              </view>
              <view class="confirm-item__info">
                <text class="confirm-item__name">{{ item.productName }}</text>
                <view class="confirm-item__row">
                  <text class="confirm-item__price">¥{{ item.productPrice }}</text>
                  <text class="confirm-item__qty">×{{ item.quantity }}</text>
                </view>
              </view>
              <text class="confirm-item__subtotal">¥{{ item.subtotal }}</text>
            </view>
          </view>
        </view>

        <!-- Delivery method switch -->
        <view class="confirm-section">
          <text class="confirm-label">配送方式</text>
          <view class="confirm-delivery">
            <view
              class="confirm-delivery__opt"
              :class="{ 'confirm-delivery__opt--on': deliveryMethod === 'PICKUP' }"
              @tap="deliveryMethod = 'PICKUP'"
            >
              <text>到店自提</text>
            </view>
            <view
              class="confirm-delivery__opt"
              :class="{ 'confirm-delivery__opt--on': deliveryMethod === 'EXPRESS' }"
              @tap="deliveryMethod = 'EXPRESS'"
            >
              <text>快递配送</text>
            </view>
          </view>
        </view>

        <!-- Payment method switch (CR-20260718-003) -->
        <view class="confirm-section">
          <text class="confirm-label">支付方式</text>
          <view class="confirm-delivery">
            <view
              class="confirm-delivery__opt"
              :class="{ 'confirm-delivery__opt--on': paymentMethod === 'OFFLINE_STORE' }"
              @tap="paymentMethod = 'OFFLINE_STORE'"
            >
              <text>到店支付</text>
            </view>
            <view
              class="confirm-delivery__opt"
              :class="{ 'confirm-delivery__opt--on': paymentMethod === 'WALLET' }"
              @tap="paymentMethod = 'WALLET'"
            >
              <text>钱包余额</text>
            </view>
          </view>
          <view v-if="paymentMethod === 'WALLET'" class="confirm-wallet">
            <text class="confirm-wallet__label">钱包余额</text>
            <text
              class="confirm-wallet__balance"
              :class="{ 'confirm-wallet__balance--low': walletInsufficient }"
            >
              ¥{{ walletBalanceText }}
            </text>
          </view>
        </view>

        <!-- Pickup: store selection -->
        <view v-if="deliveryMethod === 'PICKUP'" class="confirm-section">
          <text class="confirm-label">自提门店</text>
          <view class="confirm-pick" @tap="showStorePicker">
            <text v-if="selectedStore" class="confirm-pick__text">{{ selectedStore.name }}</text>
            <text v-else class="confirm-pick__placeholder">请选择自提门店 ›</text>
            <text v-if="selectedStore" class="confirm-pick__addr">{{ selectedStore.address }}</text>
          </view>
        </view>

        <!-- Express: address selection -->
        <view v-if="deliveryMethod === 'EXPRESS'" class="confirm-section">
          <text class="confirm-label">收货地址</text>
          <view class="confirm-pick" @tap="goSelectAddress">
            <text v-if="selectedAddress" class="confirm-pick__text">
              {{ selectedAddress.contactName }} {{ selectedAddress.contactPhone }}
            </text>
            <text v-else class="confirm-pick__placeholder">请选择收货地址 ›</text>
            <text v-if="selectedAddress" class="confirm-pick__addr">{{ fullAddress(selectedAddress) }}</text>
          </view>
        </view>

        <!-- Contact Info (plain inputs — PcFormField is a label container only) -->
        <view class="confirm-section">
          <text class="confirm-label">联系人信息</text>
          <view class="confirm-field">
            <text class="confirm-field__label">姓名</text>
            <input class="confirm-field__input" type="text" v-model="contactName" placeholder="取货联系人" />
          </view>
          <view class="confirm-field">
            <text class="confirm-field__label">电话</text>
            <input class="confirm-field__input" type="text" v-model="contactPhone" placeholder="联系电话" />
          </view>
        </view>

        <!-- Remark (single label, no duplication) -->
        <view class="confirm-section">
          <text class="confirm-label">备注</text>
          <view class="confirm-field">
            <input class="confirm-field__input confirm-field__input--full" type="text" v-model="remark" placeholder="选填" />
          </view>
        </view>

        <!-- Total -->
        <view class="confirm-total">
          <text class="confirm-total__label">合计 ({{ checkedItems.length }}件)</text>
          <text class="confirm-total__amount">¥{{ totalAmount }}</text>
        </view>

        <view class="confirm-action">
          <view class="confirm-submit-btn" :class="{ 'confirm-submit-btn--loading': submitting }" @tap="handleSubmit">
            <text>{{ submitting ? '提交中...' : '提交订单' }}</text>
          </view>
        </view>
      </template>
    </PcStatePanel>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import { getCartItems } from '@/api/cart'
import { createOrder } from '@/api/order'
import { getStores } from '@/api/store'
import { getMyAddresses, type AddressItem } from '@/api/user'
import { getMyWallet } from '@/api/wallet'
import { useUserStore } from '@/store/user'
import type { CartItem } from '@/types/product'
import type { StoreItem } from '@/types/store'

const API_BASE = import.meta.env.VITE_API_BASE_URL || ''
const userStore = useUserStore()

const pageStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const cartItems = ref<CartItem[]>([])
const contactName = ref('')
const contactPhone = ref('')
const remark = ref('')
const submitting = ref(false)

// Delivery
const deliveryMethod = ref<'PICKUP' | 'EXPRESS'>('PICKUP')
const stores = ref<StoreItem[]>([])
const selectedStore = ref<StoreItem | null>(null)
const selectedAddress = ref<AddressItem | null>(null)

// Payment (CR-20260718-003): OFFLINE_STORE (default) or WALLET.
const paymentMethod = ref<'OFFLINE_STORE' | 'WALLET'>('OFFLINE_STORE')
const walletBalance = ref(0)

const checkedItems = computed(() => cartItems.value.filter(i => i.checked))
const totalAmount = computed(() =>
  checkedItems.value.reduce((sum, item) => sum + item.subtotal, 0).toFixed(2)
)
const walletBalanceText = computed(() => walletBalance.value.toFixed(2))
const walletInsufficient = computed(
  () => paymentMethod.value === 'WALLET' && walletBalance.value < Number(totalAmount.value)
)

function fullUrl(url: string | null): string {
  if (!url) return ''
  if (url.startsWith('http')) return url
  return API_BASE + url
}

function fullAddress(addr: AddressItem): string {
  return `${addr.province ?? ''}${addr.city ?? ''}${addr.district ?? ''} ${addr.detailAddress ?? ''}`
}

async function loadCart() {
  pageStatus.value = 'loading'
  const res = await getCartItems()
  if (!res.success || !res.data) {
    pageStatus.value = 'error'
    return
  }
  cartItems.value = res.data
  // Pre-fill contact info from user profile if available
  if (!contactName.value && userStore.profile?.nickname) {
    contactName.value = userStore.profile.nickname
  }
  if (!contactPhone.value && userStore.profile?.phone) {
    contactPhone.value = userStore.profile.phone
  }
  pageStatus.value = checkedItems.value.length > 0 ? 'success' : 'empty'
}

async function loadStores() {
  const res = await getStores()
  if (res.success && res.data) {
    stores.value = res.data
    // Auto-select the first store for convenience (still user-changeable)
    if (stores.value.length > 0 && !selectedStore.value) {
      selectedStore.value = stores.value[0]
    }
  }
}

/** Load the user's wallet balance for the wallet-payment option display.
 *  Failures are non-fatal: balance stays 0 and the user can still pay offline. */
async function loadWalletBalance() {
  const res = await getMyWallet()
  if (res.success && res.data) {
    walletBalance.value = res.data.balance
  }
}

/** Store picker — small list, use native ActionSheet. */
function showStorePicker() {
  if (stores.value.length === 0) {
    uni.showToast({ title: '暂无可用门店', icon: 'none' })
    return
  }
  uni.showActionSheet({
    itemList: stores.value.map(s => s.name),
    success: (res) => {
      selectedStore.value = stores.value[res.tapIndex]
    },
  })
}

function goSelectAddress() {
  uni.navigateTo({ url: '/pages/addresses/index?mode=select' })
}

/** Receive the address chosen from the addresses page. */
function onAddressSelected(addr: AddressItem) {
  selectedAddress.value = addr
}

async function handleSubmit() {
  if (!contactName.value || !contactPhone.value) {
    uni.showToast({ title: '请填写联系人', icon: 'none' })
    return
  }
  if (checkedItems.value.length === 0) {
    uni.showToast({ title: '没有可结算的商品', icon: 'none' })
    return
  }
  if (deliveryMethod.value === 'PICKUP' && !selectedStore.value) {
    uni.showToast({ title: '请选择自提门店', icon: 'none' })
    return
  }
  if (deliveryMethod.value === 'EXPRESS' && !selectedAddress.value) {
    uni.showToast({ title: '请选择收货地址', icon: 'none' })
    return
  }
  if (paymentMethod.value === 'WALLET' && walletInsufficient.value) {
    uni.showToast({
      title: `钱包余额不足（¥${walletBalanceText.value}），请选择到店支付或充值`,
      icon: 'none',
    })
    return
  }

  submitting.value = true
  const res = await createOrder({
    deliveryMethod: deliveryMethod.value,
    storeId: deliveryMethod.value === 'PICKUP' ? selectedStore.value!.id : undefined,
    addressId: deliveryMethod.value === 'EXPRESS' ? selectedAddress.value!.addressId : undefined,
    contactName: contactName.value,
    contactPhone: contactPhone.value,
    remark: remark.value || undefined,
    paymentMethod: paymentMethod.value,
  })
  submitting.value = false

  if (res.success && res.data) {
    const payHint = paymentMethod.value === 'WALLET' ? '下单成功，已从钱包扣款' : '下单成功'
    uni.showToast({ title: payHint, icon: 'success' })
    setTimeout(() => {
      uni.redirectTo({ url: `/pages/order/detail?id=${res.data!.id}` })
    }, 1000)
  }
}

onMounted(() => {
  loadCart()
  loadStores()
  loadWalletBalance()
  uni.$on('address-selected', onAddressSelected)
})

onUnmounted(() => {
  uni.$off('address-selected', onAddressSelected)
})

// Reload when re-shown (e.g. navigating back then forward with different checked items)
onShow(() => {
  if (pageStatus.value !== 'loading') loadCart()
  // Refresh wallet balance too (it may have changed after a recharge or payment).
  loadWalletBalance()
})
</script>

<style scoped>
.order-confirm {
  padding: 20px;
}

.confirm-section {
  margin-bottom: 20px;
}

.confirm-label {
  font-size: 14px;
  font-weight: 600;
  color: #19322E;
  margin-bottom: 10px;
  display: block;
}

.confirm-items {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.confirm-item {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 10px;
  padding: 12px;
}

.confirm-item__img-wrap {
  width: 60px;
  height: 60px;
  border-radius: 8px;
  overflow: hidden;
  background: #fafafa;
  flex-shrink: 0;
}

.confirm-item__img {
  width: 100%;
  height: 100%;
}

.confirm-item__info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.confirm-item__name {
  font-size: 14px;
  color: #19322E;
  font-weight: 600;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.confirm-item__row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.confirm-item__price {
  font-size: 11px;
  color: #71817D;
}

.confirm-item__qty {
  font-size: 11px;
  color: #71817D;
}

.confirm-item__subtotal {
  font-size: 14px;
  color: #F5A623;
  font-weight: 700;
  flex-shrink: 0;
}

/* Delivery method switch */
.confirm-delivery {
  display: flex;
  gap: 10px;
}

.confirm-delivery__opt {
  flex: 1;
  height: 42px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #E2E9E6;
  display: flex;
  align-items: center;
  justify-content: center;
}

.confirm-delivery__opt--on {
  background: #11796F;
  border-color: #11796F;
}

.confirm-delivery__opt--on text {
  color: #fff;
  font-weight: 600;
}

.confirm-delivery__opt text {
  font-size: 14px;
  color: #19322E;
}

/* Wallet balance hint under the payment switch */
.confirm-wallet {
  margin-top: 10px;
  background: #FAF8F3;
  border-radius: 8px;
  padding: 10px 14px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.confirm-wallet__label {
  font-size: 12px;
  color: #71817D;
}

.confirm-wallet__balance {
  font-size: 14px;
  font-weight: 700;
  color: #11796F;
}

.confirm-wallet__balance--low {
  color: #E85D4E;
}

/* Pickup / address selector */
.confirm-pick {
  background: #fff;
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.confirm-pick__placeholder {
  font-size: 14px;
  color: #71817D;
}

.confirm-pick__text {
  font-size: 14px;
  font-weight: 600;
  color: #19322E;
}

.confirm-pick__addr {
  font-size: 11px;
  color: #71817D;
}

/* Form fields (plain inputs, NOT PcFormField) */
.confirm-field {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border-radius: 10px;
  padding: 12px 16px;
  margin-bottom: 8px;
}

.confirm-field__label {
  font-size: 14px;
  color: #71817D;
  flex-shrink: 0;
  width: 36px;
}

.confirm-field__input {
  flex: 1;
  font-size: 14px;
  color: #19322E;
  border: none;
  outline: none;
  background: transparent;
}

.confirm-field__input--full {
  width: 100%;
}

.confirm-total {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 0;
  border-top: 1px solid #E2E9E6;
}

.confirm-total__label {
  font-size: 14px;
  color: #71817D;
}

.confirm-total__amount {
  font-size: 20px;
  font-weight: 700;
  color: #F5A623;
}

.confirm-action {
  margin-top: 24px;
}

.confirm-submit-btn {
  width: 100%;
  height: 46px;
  border-radius: 23px;
  background: #11796F;
  display: flex;
  align-items: center;
  justify-content: center;
}

.confirm-submit-btn text {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
}

.confirm-submit-btn--loading {
  opacity: 0.7;
}

.confirm-submit-btn:active {
  opacity: 0.85;
}
</style>
