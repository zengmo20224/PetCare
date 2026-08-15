<template>
  <view class="pc-page booking-detail">
    <PcPageHeader title="预约详情" />

    <PcStatePanel
      :status="pageStatus"
      empty-text="预约不存在"
      @retry="loadDetail"
    >
      <template v-if="booking">
        <view class="booking-detail__card">
          <view class="booking-detail__header">
            <text class="booking-detail__no">预约 #{{ booking.bookingNo }}</text>
            <PcStatusTag :label="statusLabels[booking.status] || booking.status" :type="statusType" />
          </view>

          <view v-if="booking.serviceItemName" class="booking-detail__row">
            <text class="booking-detail__label">服务项目</text>
            <text class="booking-detail__value">{{ booking.serviceItemName }}</text>
          </view>
          <view class="booking-detail__row">
            <text class="booking-detail__label">日期</text>
            <text class="booking-detail__value">{{ booking.bookingDate }}</text>
          </view>
          <view class="booking-detail__row">
            <text class="booking-detail__label">时间</text>
            <text class="booking-detail__value">{{ booking.startTime }} - {{ booking.endTime }}</text>
          </view>
          <view class="booking-detail__row">
            <text class="booking-detail__label">服务模式</text>
            <text class="booking-detail__value">{{ modeLabel }}</text>
          </view>
          <view class="booking-detail__row">
            <text class="booking-detail__label">联系人</text>
            <text class="booking-detail__value">{{ booking.contactName }}</text>
          </view>
          <view class="booking-detail__row">
            <text class="booking-detail__label">联系电话</text>
            <text class="booking-detail__value">{{ booking.contactPhone }}</text>
          </view>
          <view v-if="booking.price" class="booking-detail__row">
            <text class="booking-detail__label">价格</text>
            <text class="booking-detail__value">¥{{ booking.price }}</text>
          </view>
          <view class="booking-detail__row">
            <text class="booking-detail__label">支付状态</text>
            <text class="booking-detail__value">{{ paymentLabels[booking.paymentStatus] || booking.paymentStatus }}</text>
          </view>
          <view v-if="booking.merchantRemark" class="booking-detail__row">
            <text class="booking-detail__label">商家备注</text>
            <text class="booking-detail__value">{{ booking.merchantRemark }}</text>
          </view>
        </view>

        <view v-if="canCancel" class="booking-detail__action">
          <PcPrimaryButton text="取消预约" :loading="cancelling" @press="handleCancel" />
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
import { getBookingDetail, cancelBooking } from '@/api/booking'
import type { BookingItem } from '@/types/booking'
import { normalizeRouteParam } from '@/utils/route-query'

const booking = ref<BookingItem | null>(null)
const pageStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const cancelling = ref(false)
const currentBookingId = ref('')

const statusLabels: Record<string, string> = {
  PENDING_CONFIRM: '待确认', CONFIRMED: '已确认', IN_SERVICE: '服务中',
  COMPLETED: '已完成', CANCELLED: '已取消', REJECTED: '已拒绝',
}

const paymentLabels: Record<string, string> = {
  UNPAID: '未支付', OFFLINE_PAID: '已线下支付', REFUNDED: '已退款',
}

const statusType = computed(() => {
  const map: Record<string, string> = {
    PENDING_CONFIRM: 'warning', CONFIRMED: 'primary', IN_SERVICE: 'success',
    COMPLETED: 'info', CANCELLED: 'danger', REJECTED: 'danger',
  }
  return map[booking.value?.status ?? ''] ?? 'info'
})

const modeLabel = computed(() => {
  const m = booking.value?.serviceMode
  if (m === 'STORE') return '到店服务'
  if (m === 'HOME') return '上门服务'
  return m ?? ''
})

const canCancel = computed(() => {
  const s = booking.value?.status
  return s === 'PENDING_CONFIRM' || s === 'CONFIRMED'
})

async function loadDetail(routeId?: unknown) {
  const id = normalizeRouteParam(routeId ?? currentBookingId.value)

  if (!id) {
    booking.value = null
    pageStatus.value = 'empty'
    return
  }

  currentBookingId.value = id
  pageStatus.value = 'loading'
  const res = await getBookingDetail(id)

  if (!res.success || !res.data) {
    booking.value = null
    pageStatus.value = 'error'
    return
  }

  booking.value = res.data
  pageStatus.value = 'success'
}

async function handleCancel() {
  if (!booking.value) return
  cancelling.value = true
  const res = await cancelBooking(booking.value.id)
  cancelling.value = false

  if (res.success && res.data) {
    booking.value = res.data
    uni.showToast({ title: '已取消', icon: 'success' })
  }
}

onLoad((query) => {
  loadDetail(query?.id)
})
</script>

<style scoped>
.booking-detail {
  padding: 40rpx;
}

.booking-detail__card {
  background: var(--pc-user-surface);
  border-radius: 32rpx;
  padding: 40rpx;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

.booking-detail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 32rpx;
}

.booking-detail__no {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
}

.booking-detail__row {
  display: flex;
  justify-content: space-between;
  padding: 16rpx 0;
  border-bottom: 1px solid var(--pc-user-line);
}

.booking-detail__label {
  font-size: 28rpx;
  color: var(--pc-user-muted);
}

.booking-detail__value {
  font-size: 28rpx;
  color: var(--pc-user-ink);
  font-weight: 500;
}

.booking-detail__action {
  margin-top: 48rpx;
}
</style>
