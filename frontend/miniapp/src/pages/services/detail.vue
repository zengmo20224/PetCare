<template>
  <view class="pc-page service-detail">
    <PcStatePanel
      :status="pageStatus"
      empty-text="服务不存在"
      @retry="loadDetail"
    >
      <template v-if="service">
        <view v-if="coverImage" class="service-detail__cover">
          <image
            class="service-detail__cover-img"
            :src="coverImage"
            mode="aspectFill"
            @tap="previewCover"
          />
        </view>
        <view v-else class="service-detail__cover service-detail__cover-placeholder">
          <text class="service-detail__cover-placeholder-text">{{ modeLabel }}</text>
        </view>

        <view class="service-detail__body">
          <view class="service-detail__header">
            <text class="service-detail__name">{{ service.name }}</text>
            <PcStatusTag :label="modeLabel" type="primary" />
          </view>
          <view class="service-detail__info">
            <view class="service-detail__info-row">
              <text class="service-detail__label">服务时长</text>
              <text class="service-detail__value">{{ durationText }}</text>
            </view>
            <view class="service-detail__info-row">
              <text class="service-detail__label">适用宠物</text>
              <text class="service-detail__value">{{ petTypeText }}</text>
            </view>
            <view class="service-detail__info-row">
              <text class="service-detail__label">参考价格</text>
              <text class="service-detail__price">{{ priceText }}起</text>
            </view>
          </view>
          <view class="service-detail__action">
            <PcPrimaryButton text="立即预约" @press="goBooking" />
          </view>
        </view>

        <!-- Service details section: text description + image grid (like product detail) -->
        <view v-if="service.description || detailImages.length > 0" class="service-detail__desc">
          <text class="service-detail__desc-title">服务详情</text>
          <text v-if="service.description" class="service-detail__desc-content" decode>{{ service.description }}</text>
          <view v-if="detailImages.length > 0" class="service-detail__desc-images">
            <view
              v-for="(url, index) in detailImages"
              :key="index"
              class="service-detail__desc-image-wrap"
              @tap="previewDetailImage(index)"
            >
              <image class="service-detail__desc-image" :src="url" mode="widthFix" />
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
import PcStatePanel from '@/components/PcStatePanel.vue'
import PcStatusTag from '@/components/PcStatusTag.vue'
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'
import { getServiceDetail } from '@/api/service'
import { useUserStore } from '@/store/user'
import type { ServiceItem } from '@/types/service'
import { formatDuration, formatYuan } from '@/utils/format'
import { normalizeRouteParam } from '@/utils/route-query'
import { assetFullUrl } from '@/utils/asset-url'

const userStore = useUserStore()

const service = ref<ServiceItem | null>(null)
const pageStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const currentServiceId = ref('')

const modeLabel = computed(() => {
  const map: Record<string, string> = { STORE: '到店', HOME: '上门', BOTH: '到店/上门' }
  return map[service.value?.serviceMode ?? ''] ?? ''
})

const durationText = computed(() => formatDuration(service.value?.durationMinutes))
const priceText = computed(() => formatYuan(service.value?.price ?? 0))

const petTypeText = computed(() => {
  const map: Record<string, string> = { DOG: '犬类', CAT: '猫类', ALL: '所有宠物' }
  return map[service.value?.petType ?? ''] ?? '所有宠物'
})

/**
 * Cover image: a single coverUrl shown at the top (no swiper).
 */
const coverImage = computed(() => {
  if (!service.value?.coverUrl) return ''
  return assetFullUrl(service.value.coverUrl)
})

/**
 * Detail images: imageUrls shown below as a "service details" introduction grid,
 * mirroring the product detail page (no carousel).
 */
const detailImages = computed(() => {
  if (!service.value?.imageUrls?.length) return []
  return service.value.imageUrls.map(assetFullUrl)
})

function previewCover() {
  if (!coverImage.value) return
  uni.previewImage({
    current: coverImage.value,
    urls: [coverImage.value],
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

async function loadDetail(routeId?: unknown) {
  const id = normalizeRouteParam(routeId ?? currentServiceId.value)

  if (!id) {
    service.value = null
    pageStatus.value = 'empty'
    return
  }

  currentServiceId.value = id
  pageStatus.value = 'loading'
  const res = await getServiceDetail(id)

  if (!res.success || !res.data) {
    service.value = null
    pageStatus.value = 'error'
    return
  }

  service.value = res.data
  pageStatus.value = 'success'
}

function goBooking() {
  if (!userStore.isLoggedIn) {
    uni.showToast({ title: '请先登录后再预约', icon: 'none' })
    setTimeout(() => uni.navigateTo({ url: '/pages/auth/login' }), 1000)
    return
  }
  if (service.value) {
    uni.navigateTo({ url: `/pages/booking/create?serviceId=${service.value.id}` })
  }
}

onLoad((query) => {
  loadDetail(query?.id)
})
</script>

<style scoped>
.service-detail {
  padding: 40rpx;
}

.service-detail__cover {
  position: relative;
  width: 100%;
  height: 400rpx;
  border-radius: 40rpx;
  overflow: hidden;
  margin-bottom: 32rpx;
}

.service-detail__cover-img {
  width: 100%;
  height: 100%;
}

.service-detail__cover-placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--pc-user-soft);
}

.service-detail__cover-placeholder-text {
  font-size: 96rpx;
  font-weight: 700;
  color: var(--pc-user-primary);
  opacity: 0.3;
}

.service-detail__body {
  background: var(--pc-user-surface);
  border-radius: 32rpx;
  padding: 40rpx;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

.service-detail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.service-detail__name {
  font-size: 48rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
}

/* Service details section (text description + image grid) */
.service-detail__desc {
  margin-top: 32rpx;
  background: var(--pc-user-surface);
  border-radius: 32rpx;
  padding: 40rpx;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

.service-detail__desc-title {
  display: block;
  font-size: 32rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
  margin-bottom: 24rpx;
}

.service-detail__desc-content {
  display: block;
  width: 100%;
  font-size: 28rpx;
  color: var(--pc-user-ink);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
  margin-bottom: 24rpx;
}

/* Description images: always one per row (single column, full width) */
.service-detail__desc-images {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16rpx;
}

.service-detail__desc-image-wrap {
  width: 100%;
  border-radius: 16rpx;
  overflow: hidden;
  background: #f5f5f5;
}

.service-detail__desc-image {
  width: 100%;
  height: auto;
  display: block;
}

.service-detail__info {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  margin-bottom: 48rpx;
}

.service-detail__info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.service-detail__label {
  font-size: 28rpx;
  color: var(--pc-user-muted);
}

.service-detail__value {
  font-size: 28rpx;
  color: var(--pc-user-ink);
  font-weight: 500;
}

.service-detail__price {
  font-size: 40rpx;
  color: var(--pc-user-danger);
  font-weight: 700;
}

.service-detail__action {
  margin-top: 16rpx;
}
</style>
