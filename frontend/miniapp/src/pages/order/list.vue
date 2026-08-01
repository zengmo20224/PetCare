<template>
  <view class="pc-page order-list">
    <PcPageHeader title="我的订单" />

    <PcStatePanel v-if="!isLoggedIn" status="unauthorized" />

    <PcStatePanel
      v-else
      :status="listStatus"
      empty-text="暂无订单"
      @retry="loadOrders"
    >
      <view class="order-list__items">
        <view
          v-for="item in orders"
          :key="item.id"
          class="order-card"
          @tap="goDetail(item.id)"
        >
          <view class="order-card__header">
            <text class="order-card__no">{{ item.orderNo }}</text>
            <PcStatusTag :label="statusLabels[item.status] || item.status" :type="statusType(item.status)" />
          </view>
          <view class="order-card__row">
            <text class="order-card__amount">¥{{ item.totalAmount }}</text>
            <text class="order-card__time">{{ item.createTime }}</text>
          </view>
        </view>
      </view>
    </PcStatePanel>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import PcStatusTag from '@/components/PcStatusTag.vue'
import { getMyOrders } from '@/api/order'
import { useUserStore } from '@/store/user'
import type { OrderItem } from '@/types/product'

const userStore = useUserStore()
const isLoggedIn = computed(() => userStore.isLoggedIn)

const listStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const orders = ref<OrderItem[]>([])

const statusLabels: Record<string, string> = {
  PENDING_CONFIRM: '待确认', PREPARING: '备货中', READY_FOR_PICKUP: '待自提',
  COMPLETED: '已完成', CANCELLED: '已取消', OUT_OF_STOCK: '缺货取消',
}

function statusType(status: string): string {
  const map: Record<string, string> = {
    PENDING_CONFIRM: 'warning', PREPARING: 'primary', READY_FOR_PICKUP: 'success',
    COMPLETED: 'info', CANCELLED: 'danger', OUT_OF_STOCK: 'danger',
  }
  return map[status] ?? 'info'
}

async function loadOrders() {
  listStatus.value = 'loading'
  const res = await getMyOrders({ size: 20 })
  if (!res.success || !res.data) {
    listStatus.value = 'error'
    return
  }
  orders.value = res.data.items
  listStatus.value = orders.value.length > 0 ? 'success' : 'empty'
}

function goDetail(id: string) {
  uni.navigateTo({ url: `/pages/order/detail?id=${id}` })
}

// 每次显示时刷新（从下单/详情返回后自动更新列表）
onShow(() => {
  if (isLoggedIn.value) loadOrders()
})
</script>

<style scoped>
.order-list {
  padding: 20px;
}

.order-list__items {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.order-card {
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

.order-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.order-card__no {
  font-size: 14px;
  font-weight: 600;
  color: #19322E;
}

.order-card__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.order-card__amount {
  font-size: 16px;
  font-weight: 700;
  color: #F5A623;
}

.order-card__time {
  font-size: 11px;
  color: #71817D;
}
</style>
