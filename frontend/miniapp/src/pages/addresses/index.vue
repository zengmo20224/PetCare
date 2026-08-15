<template>
  <view class="pc-page addresses-page">
    <PcPageHeader title="我的地址" />

    <PcStatePanel
      :status="listStatus"
      empty-text="还没有添加地址，点击下方按钮添加"
      @retry="loadAddresses"
    >
      <view class="addresses-list">
        <view
          v-for="addr in addresses"
          :key="addr.addressId"
          class="addr-card"
          @tap="onCardTap(addr)"
        >
          <view class="addr-card__info">
            <view class="addr-card__header">
              <text class="addr-card__name">{{ addr.contactName }}</text>
              <text class="addr-card__phone">{{ addr.contactPhone }}</text>
              <text v-if="addr.isDefault" class="addr-card__default">默认</text>
            </view>
            <text class="addr-card__detail">{{ fullAddress(addr) }}</text>
          </view>
          <text class="addr-card__arrow">?</text>
        </view>
      </view>
    </PcStatePanel>

    <view class="addresses-action">
      <PcPrimaryButton text="添加地址" @press="goAdd" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'
import { getMyAddresses, type AddressItem } from '@/api/user'
import { normalizeRouteParam } from '@/utils/route-query'

const listStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const addresses = ref<AddressItem[]>([])
const isSelectMode = ref(false)

// Selection mode: when navigated to with ?mode=select, tapping a card emits an
// event and returns to the caller (order confirm page) instead of opening edit.
onLoad((query) => {
  isSelectMode.value = normalizeRouteParam(query?.mode) === 'select'
})

// 每次显示时刷新（从编辑页返回后自动更新列表）
onShow(() => {
  loadAddresses()
})

function fullAddress(addr: AddressItem): string {
  return `${addr.province ?? ''}${addr.city ?? ''}${addr.district ?? ''} ${addr.detailAddress ?? ''}`
}

function onCardTap(addr: AddressItem) {
  if (isSelectMode.value) {
    uni.$emit('address-selected', addr)
    uni.navigateBack()
  } else {
    goEdit(addr.addressId)
  }
}

async function loadAddresses() {
  listStatus.value = 'loading'
  const res = await getMyAddresses()
  if (!res.success || !res.data) {
    listStatus.value = 'error'
    return
  }
  addresses.value = res.data
  listStatus.value = addresses.value.length > 0 ? 'success' : 'empty'
}

function goAdd() {
  uni.navigateTo({ url: '/pages/addresses/edit' })
}

function goEdit(addressId: string) {
  uni.navigateTo({ url: `/pages/addresses/edit?id=${addressId}` })
}

loadAddresses()
</script>

<style scoped>
.addresses-page {
  padding: 40rpx;
}

.addresses-list {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}

.addr-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
  background: var(--pc-user-surface);
  border-radius: 32rpx;
  padding: 32rpx;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

.addr-card__info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.addr-card__header {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.addr-card__name {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--pc-user-ink);
}

.addr-card__phone {
  font-size: 28rpx;
  color: var(--pc-user-muted);
}

.addr-card__default {
  font-size: 22rpx;
  color: var(--pc-user-surface);
  background: var(--pc-user-primary);
  padding: 2rpx 16rpx;
  border-radius: 12rpx;
}

.addr-card__detail {
  font-size: 28rpx;
  color: var(--pc-user-muted);
}

.addr-card__arrow {
  font-size: 28rpx;
  color: var(--pc-user-muted);
}

.addresses-action {
  margin-top: 48rpx;
}
</style>
