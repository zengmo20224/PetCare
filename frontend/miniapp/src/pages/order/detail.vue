<template>
  <view class="pc-page order-detail">
    <PcPageHeader title="订单详情" />

    <PcStatePanel
      :status="pageStatus"
      empty-text="订单不存在"
      @retry="loadDetail"
    >
      <template v-if="order">
        <view class="order-detail__card">
          <view class="order-detail__header">
            <text class="order-detail__no">{{ order.orderNo }}</text>
            <PcStatusTag :label="statusLabels[order.status] || order.status" :type="statusType(order.status)" />
          </view>

          <!-- Items -->
          <view class="order-detail__items">
            <view v-for="item in order.items" :key="item.id" class="order-detail__item">
              <text class="order-detail__item-name">{{ item.productName }} ×{{ item.quantity }}</text>
              <text class="order-detail__item-price">¥{{ item.totalAmount }}</text>
            </view>
          </view>

          <view class="order-detail__row">
            <text class="order-detail__label">合计</text>
            <text class="order-detail__value order-detail__value--accent">¥{{ order.totalAmount }}</text>
          </view>
          <view class="order-detail__row">
            <text class="order-detail__label">联系人</text>
            <text class="order-detail__value">{{ order.contactName }}</text>
          </view>
          <view class="order-detail__row">
            <text class="order-detail__label">电话</text>
            <text class="order-detail__value">{{ order.contactPhone }}</text>
          </view>
          <view class="order-detail__row">
            <text class="order-detail__label">支付状态</text>
            <text class="order-detail__value">{{ payLabels[order.paymentStatus] || order.paymentStatus }}</text>
          </view>
          <view class="order-detail__row">
            <text class="order-detail__label">自提状态</text>
            <text class="order-detail__value">{{ pickupLabels[order.pickupStatus] || order.pickupStatus }}</text>
          </view>
          <view v-if="order.merchantRemark" class="order-detail__row">
            <text class="order-detail__label">商家备注</text>
            <text class="order-detail__value">{{ order.merchantRemark }}</text>
          </view>
        </view>

        <view v-if="canCancel" class="order-detail__action">
          <PcPrimaryButton text="取消订单" :loading="cancelling" @press="handleCancel" />
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
import PcStatusTag from '@/components/PcStatusTag.vue'
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'
import { getOrderDetail, cancelOrder } from '@/api/order'
import type { OrderDetail } from '@/types/product'
import { normalizeRouteParam } from '@/utils/route-query'

const order = ref<OrderDetail | null>(null)
const pageStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const cancelling = ref(false)
const currentOrderId = ref('')

const statusLabels: Record<string, string> = {
  PENDING_CONFIRM: '待确认', PREPARING: '备货中', READY_FOR_PICKUP: '待自提',
  COMPLETED: '已完成', CANCELLED: '已取消', OUT_OF_STOCK: '缺货取消',
}
const payLabels: Record<string, string> = { UNPAID: '未支付', OFFLINE_PAID: '已线下支付', REFUNDED: '已退款' }
const pickupLabels: Record<string, string> = { WAIT_PREPARE: '待备货', READY_FOR_PICKUP: '待自提', PICKED_UP: '已自提' }

function statusType(status: string): string {
  const map: Record<string, string> = {
    PENDING_CONFIRM: 'warning', PREPARING: 'primary', READY_FOR_PICKUP: 'success',
    COMPLETED: 'info', CANCELLED: 'danger', OUT_OF_STOCK: 'danger',
  }
  return map[status] ?? 'info'
}

const canCancel = computed(() => {
  const s = order.value?.status
  return s === 'PENDING_CONFIRM' || s === 'PREPARING'
})

async function loadDetail(routeId?: unknown) {
  const id = normalizeRouteParam(routeId ?? currentOrderId.value)
  if (!id) {
    order.value = null
    pageStatus.value = 'empty'
    return
  }

  currentOrderId.value = id
  pageStatus.value = 'loading'
  const res = await getOrderDetail(id)
  if (!res.success || !res.data) {
    order.value = null
    pageStatus.value = 'error'
    return
  }
  order.value = res.data
  pageStatus.value = 'success'
}

async function handleCancel() {
  if (!order.value) return
  cancelling.value = true
  const res = await cancelOrder(order.value.id)
  cancelling.value = false
  if (res.success) {
    uni.showToast({ title: '已取消', icon: 'success' })
    await loadDetail()
  }
}

onLoad((query) => {
  loadDetail(query?.id)
})
</script>

<style scoped>
.order-detail {
  padding: 40rpx;
}

.order-detail__card {
  background: var(--pc-user-surface);
  border-radius: 32rpx;
  padding: 40rpx;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

.order-detail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32rpx;
}

.order-detail__no {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
}

.order-detail__items {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  padding-bottom: 24rpx;
  border-bottom: 1px solid var(--pc-user-line);
  margin-bottom: 24rpx;
}

.order-detail__item {
  display: flex;
  justify-content: space-between;
}

.order-detail__item-name {
  font-size: 28rpx;
  color: var(--pc-user-ink);
}

.order-detail__item-price {
  font-size: 28rpx;
  color: var(--pc-user-accent);
}

.order-detail__row {
  display: flex;
  justify-content: space-between;
  padding: 12rpx 0;
}

.order-detail__label {
  font-size: 28rpx;
  color: var(--pc-user-muted);
}

.order-detail__value {
  font-size: 28rpx;
  color: var(--pc-user-ink);
  font-weight: 500;
}

.order-detail__value--accent {
  color: var(--pc-user-accent);
  font-weight: 700;
  font-size: 32rpx;
}

.order-detail__action {
  margin-top: 48rpx;
}
</style>
