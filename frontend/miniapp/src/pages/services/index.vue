<template>
  <view class="pc-page services-page">
    <PcPageHeader title="服务" />

    <view class="services-intro">
      <text class="services-intro__kicker">CARE WITH LOVE</text>
      <text class="services-intro__title">把每一次暂时离开，都变成它被温柔照顾的时光</text>
      <text class="services-intro__desc">洗护、美容、上门照护与寄养，让每一次托付都有安心回应。</text>
    </view>

    <scroll-view class="services-categories" scroll-x>
      <view class="services-categories__track">
        <view
          class="services-category"
          :class="{ 'services-category--active': activeCategoryId === '' }"
          @tap="switchCategory('')"
        >
          <text>全部服务</text>
        </view>
        <view
          v-for="category in categories"
          :key="category.id"
          class="services-category"
          :class="{ 'services-category--active': activeCategoryId === category.id }"
          @tap="switchCategory(category.id)"
        >
          <text>{{ category.name }}</text>
        </view>
      </view>
    </scroll-view>

    <!-- Pet Type Tabs -->
    <view class="services-tabs">
      <view
        v-for="tab in petTypeTabs"
        :key="tab.value"
        class="services-tab"
        :class="{ 'services-tab--active': activePetType === tab.value }"
        @tap="switchPetType(tab.value)"
      >
        <text>{{ tab.label }}</text>
      </view>
    </view>

    <!-- Service List -->
    <PcStatePanel
      :status="listStatus"
      empty-text="暂无可用服务"
      error-message=""
      @retry="loadServices"
    >
      <view class="services-list">
        <template v-for="item in displayItems" :key="item.key">
          <!-- Merged size-variant card (e.g. dog grooming) -->
          <PcServiceCard
            v-if="item.merged"
            :name="item.displayName"
            :mode="item.serviceMode"
            :duration-minutes="item.durationMinutes"
            :price="item.minPrice"
            :price-from="true"
            :image-url="item.coverUrl || undefined"
            @tap="openSizePicker(item)"
          />

          <!-- Normal single card -->
          <PcServiceCard
            v-else
            :name="item.displayName"
            :mode="item.serviceMode"
            :duration-minutes="item.durationMinutes"
            :price="item.price"
            :image-url="item.coverUrl || undefined"
            @tap="goDetail(item.firstId)"
          />
        </template>
      </view>
    </PcStatePanel>

    <!-- Size Picker Popup -->
    <view v-if="sizePickerVisible" class="size-picker-mask" @tap="closeSizePicker">
      <view class="size-picker" @tap.stop>
        <view class="size-picker__header">
          <text class="size-picker__title">{{ sizePickerData?.displayName }}</text>
          <text class="size-picker__close" @tap="closeSizePicker">✕</text>
        </view>
        <text class="size-picker__hint">请根据宠物体重选择合适的体型</text>

        <view
          v-for="option in sizePickerData?.variants"
          :key="option.id"
          class="size-picker__option"
          @tap="selectSize(option.id)"
        >
          <view class="size-picker__option-info">
            <text class="size-picker__option-name">{{ sizeLabels[option.petSize].label }}</text>
            <text class="size-picker__option-weight">{{ sizeLabels[option.petSize].hint }}</text>
          </view>
          <view class="size-picker__option-right">
            <text class="size-picker__option-price">¥{{ option.price }}</text>
            <text class="size-picker__option-arrow">?</text>
          </view>
        </view>
      </view>
    </view>
    <PcBottomNav current-path="pages/services/index" />
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import PcServiceCard from '@/components/PcServiceCard.vue'
import PcBottomNav from '@/components/PcBottomNav.vue'
import { getServiceCategories, getServiceItems } from '@/api/service'
import type { ServiceCategory, ServiceItem } from '@/types/service'
import { consumeServiceFilterIntent } from '@/utils/service-navigation'

// ---- Pet Type Filter ----
type PetTypeFilter = 'ALL' | 'DOG' | 'CAT'
const petTypeTabs: { label: string; value: PetTypeFilter }[] = [
  { label: '全部', value: 'ALL' },
  { label: '狗狗', value: 'DOG' },
  { label: '猫咪', value: 'CAT' },
]
const activePetType = ref<PetTypeFilter>('ALL')
const categories = ref<ServiceCategory[]>([])
const activeCategoryId = ref('')

// ---- Size labels with weight hints ----
const sizeLabels: Record<string, { label: string; hint: string }> = {
  SMALL: { label: '小型犬', hint: '体重 10kg 以下' },
  MEDIUM: { label: '中型犬', hint: '体重 10-25kg' },
  LARGE: { label: '大型犬', hint: '体重 25kg 以上' },
  ALL: { label: '通用', hint: '' },
}

// ---- Display item: either a single service or a merged group ----
interface DisplayItem {
  key: string
  displayName: string
  serviceMode: string
  durationMinutes: number
  coverUrl: string | null
  merged: boolean
  firstId: string
  price?: number
  minPrice?: number
  variants?: ServiceItem[]
}

const listStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const rawServices = ref<ServiceItem[]>([])
const displayItems = ref<DisplayItem[]>([])

// Size picker state
const sizePickerVisible = ref(false)
const sizePickerData = ref<DisplayItem | null>(null)

function switchPetType(value: PetTypeFilter) {
  if (activePetType.value === value) return
  activePetType.value = value
  loadServices()
}

function switchCategory(categoryId: string) {
  if (activeCategoryId.value === categoryId) return
  activeCategoryId.value = categoryId
  loadServices()
}

async function loadServices() {
  listStatus.value = 'loading'

  const params: { size: number; categoryId?: string; petType?: string } = { size: 50 }
  if (activeCategoryId.value) params.categoryId = activeCategoryId.value
  if (activePetType.value === 'DOG') params.petType = 'DOG'
  else if (activePetType.value === 'CAT') params.petType = 'CAT'

  const res = await getServiceItems(params)
  if (!res.success || !res.data) {
    listStatus.value = 'error'
    return
  }

  rawServices.value = res.data.items
  displayItems.value = buildDisplayItems(rawServices.value)
  listStatus.value = displayItems.value.length > 0 ? 'success' : 'empty'
}

/**
 * Group services by category. When multiple services share the same
 * category + petType but differ only in petSize (SMALL/MEDIUM/LARGE),
 * merge them into one card.
 */
function buildDisplayItems(items: ServiceItem[]): DisplayItem[] {
  // Group key: categoryId + serviceMode (treat items with size variants as one)
  const groups = new Map<string, ServiceItem[]>()

  for (const item of items) {
    // Items with petSize ALL are standalone — use their own id as key
    // Items with specific sizes (SMALL/MEDIUM/LARGE) group by category
    let groupKey: string
    if (item.petSize === 'ALL' || !item.petSize) {
      groupKey = `solo-${item.id}`
    } else {
      groupKey = `cat-${item.categoryId}-${item.serviceMode}`
    }

    if (!groups.has(groupKey)) groups.set(groupKey, [])
    groups.get(groupKey)!.push(item)
  }

  const result: DisplayItem[] = []
  for (const [key, group] of groups) {
    if (group.length > 1) {
      // Merged: multiple size variants
      const sorted = group.slice().sort((a, b) => (a.price ?? 0) - (b.price ?? 0))
      const categoryName = deriveCategoryName(sorted[0])
      result.push({
        key,
        displayName: categoryName,
        serviceMode: sorted[0].serviceMode,
        durationMinutes: sorted[0].durationMinutes,
        coverUrl: sorted[0].coverUrl,
        merged: true,
        firstId: sorted[0].id,
        minPrice: sorted[0].price,
        variants: sorted,
      })
    } else {
      const item = group[0]
      result.push({
        key,
        displayName: item.name,
        serviceMode: item.serviceMode,
        durationMinutes: item.durationMinutes,
        coverUrl: item.coverUrl,
        merged: false,
        firstId: item.id,
        price: item.price,
      })
    }
  }

  // Sort by min price for consistency
  result.sort((a, b) => {
    const pa = a.merged ? (a.minPrice ?? 0) : (a.price ?? 0)
    const pb = b.merged ? (b.minPrice ?? 0) : (b.price ?? 0)
    return pa - pb
  })

  return result
}

/** Derive a clean display name from the first variant */
function deriveCategoryName(item: ServiceItem): string {
  // Strip size prefix from name: "小型犬基础洗护" → "犬类洗护"
  // Use the category-based name
  const name = item.name
  if (name.includes('洗护')) return '犬类洗护'
  if (name.includes('美容')) return '犬类美容'
  // Fallback: remove size prefix
  return name.replace(/^(小型|中型|大型)[犬猫]/, '').trim() || name
}

function openSizePicker(item: DisplayItem) {
  if (!item.variants || item.variants.length === 0) return
  sizePickerData.value = item
  sizePickerVisible.value = true
}

function closeSizePicker() {
  sizePickerVisible.value = false
}

function selectSize(id: string) {
  sizePickerVisible.value = false
  goDetail(id)
}

function goDetail(id: string) {
  uni.navigateTo({ url: `/pages/services/detail?id=${id}` })
}

async function loadCatalog() {
  const categoriesResponse = await getServiceCategories()
  if (categoriesResponse.success && categoriesResponse.data) {
    categories.value = categoriesResponse.data
  }

  const intent = consumeServiceFilterIntent()
  if (intent) {
    activeCategoryId.value = intent.categoryName === null
      ? ''
      : categories.value.find(category => category.name === intent.categoryName)?.id ?? ''
    activePetType.value = 'ALL'
  }

  await loadServices()
}

onShow(loadCatalog)
</script>

<style scoped>
.services-page {
  /* 底部留白避开 fixed PcBottomNav（64px 高 + 安全余量），与 community/products/profile 一致 */
  padding: 20px 20px 96px;
}

.services-intro {
  display: flex;
  flex-direction: column;
  gap: 5px;
  margin-bottom: 18px;
  min-height: 118px;
  padding: 21px 20px;
  border: 1px solid rgba(17, 121, 111, 0.16);
  border-radius: 22px;
  background:
    radial-gradient(circle at 92% 18%, rgba(255, 218, 138, 0.82) 0 38px, transparent 39px),
    linear-gradient(135deg, #F4FFFB 0%, #FFFFFF 46%, #E7F6F1 100%);
  background:
    radial-gradient(circle at 92% 18%, rgba(245, 166, 35, 0.3) 0 38px, transparent 39px),
    linear-gradient(135deg, #fff, #DFF2ED);
  box-shadow: 0 12px 30px rgba(25, 50, 46, 0.08);
}

.services-intro__kicker {
  font-size: 9px;
  font-weight: 800;
  letter-spacing: 1.4px;
  color: #E97951;
  color: #E97951;
}

.services-intro__title {
  max-width: 300px;
  font-size: 20px;
  line-height: 1.35;
  font-weight: 800;
  color: #19322E;
  color: #0C4D48;
}

.services-intro__desc {
  max-width: 310px;
  font-size: 11px;
  color: #5F746F;
  color: #71817D;
}

.services-categories {
  margin-bottom: 12px;
  white-space: nowrap;
  width: 100%;
}

.services-categories__track {
  display: inline-flex;
  gap: 8px;
  min-width: 100%;
  padding-right: 20px;
  box-sizing: border-box;
}

.services-category {
  display: flex;
  align-items: center;
  height: 34px;
  padding: 0 16px;
  border: 1px solid #E2E9E6;
  border: 1px solid #E2E9E6;
  border-radius: 999px;
  background: #fff;
  flex-shrink: 0;
}

.services-category text {
  color: #5F746F;
  color: #71817D;
  font-size: 14px;
  font-weight: 600;
}

.services-category--active {
  border-color: #11796F;
  border-color: #11796F;
  background: #11796F;
  background: #11796F;
}

.services-category--active text {
  color: #fff;
}

.services-tabs {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.services-tab {
  padding: 6px 16px;
  border-radius: 20px;
  background: #fff;
  border: 1px solid #E2E9E6;
  border: 1px solid #E2E9E6;
}

.services-tab--active {
  background: #11796F;
  background: #11796F;
  border-color: #11796F;
  border-color: #11796F;
}

.services-tab--active text {
  color: #fff;
}

.services-tab text {
  font-size: 14px;
  color: #5F746F;
  color: #71817D;
}

.services-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 14px;
}

/* Size Picker Popup */
.size-picker-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.size-picker {
  width: 100%;
  background: #fff;
  border-radius: 20px 20px 0 0;
  padding: 20px 16px 32px;
}

.size-picker__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.size-picker__title {
  font-size: 18px;
  font-weight: 700;
  color: #19322E;
}

.size-picker__close {
  font-size: 18px;
  color: #71817D;
  padding: 4px 12px;
}

.size-picker__hint {
  font-size: 11px;
  color: #71817D;
  margin-bottom: 16px;
}

.size-picker__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  border-radius: 12px;
  background: #FAF8F3;
  margin-bottom: 8px;
}

.size-picker__option-info {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.size-picker__option-name {
  font-size: 16px;
  font-weight: 600;
  color: #19322E;
}

.size-picker__option-weight {
  font-size: 11px;
  color: #71817D;
}

.size-picker__option-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.size-picker__option-price {
  font-size: 16px;
  font-weight: 700;
  color: #F5A623;
}

.size-picker__option-arrow {
  font-size: 14px;
  color: #71817D;
}
</style>
