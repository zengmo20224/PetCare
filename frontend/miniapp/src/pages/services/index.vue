<template>
  <view class="pc-page services-page">
    <!-- Hero 温情宣传栏（demo 风格：90px 通栏纯文字） -->
    <PcHeroStrip
      title="把每一次托付，都交给温柔专业的人"
      highlight="托付"
      desc="洗护、美容、上门照护与寄养，让陪伴始终如一。"
    />

    <!-- 搜索栏（demo search-bar） -->
    <view class="svc-search-bar">
      <view class="svc-search-bar__input">
        <PcIcon name="search" :size="16" color="#B2B2B2" />
        <input
          v-model="searchKeyword"
          class="svc-search-bar__field"
          type="text"
          confirm-type="search"
          placeholder="搜索服务名称"
          placeholder-class="svc-search-bar__ph"
        />
      </view>
      <view class="svc-search-bar__btn" @tap="handleSearch">
        <text>搜索</text>
      </view>
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
      empty-icon="🐾"
      :empty-text="emptyText"
      :empty-hint="emptyHint"
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
            @press="openSizePicker(item)"
          />

          <!-- Normal single card -->
          <PcServiceCard
            v-else
            :name="item.displayName"
            :mode="item.serviceMode"
            :duration-minutes="item.durationMinutes"
            :price="item.price"
            :image-url="item.coverUrl || undefined"
            @press="goDetail(item.firstId)"
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
import { ref, computed } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PcHeroStrip from '@/components/PcHeroStrip.vue'
import PcIcon from '@/components/PcIcon.vue'
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

// 搜索关键字（纯视觉，暂不接过滤逻辑）
const searchKeyword = ref('')
function handleSearch() {
  // 预留：后续接搜索过滤
}

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

/** Whether the user is filtering by category or pet type — affects empty-state copy. */
const hasFilter = computed(() => !!activeCategoryId.value || activePetType.value !== 'ALL')
const emptyText = computed(() => hasFilter.value ? '没有找到相关服务' : '暂无可用服务')
const emptyHint = computed(() =>
  hasFilter.value ? '换个分类或宠物类型试试' : '新服务上线后会在这里展示'
)

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
  /* hero/搜索/pills/tabs 通栏贴边；服务网格由下方规则补左右留白 */
  padding: 0 0;
}

/* 服务网格左右留白（demo svc-grid padding: 12px 16px）*/
.services-page .services-list {
  padding-left: 32rpx;
  padding-right: 32rpx;
}

/* 底部留白：H5 端 fixed PcBottomNav 高 128rpx + 安全余量；小程序端用原生 tabBar，仅留 32rpx 防贴边。 */
/* #ifdef H5 */
.services-page {
  padding-bottom: 192rpx;
}
/* #endif */
/* #ifdef MP-WEIXIN */
.services-page {
  padding-bottom: 32rpx;
}
/* #endif */

/* ─── 搜索栏（demo search-bar）─── */
.svc-search-bar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 32rpx;
}

.svc-search-bar__input {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
  background: #FFFFFF;
  border-radius: 12rpx;
  padding: 14rpx 24rpx;
}

.svc-search-bar__field {
  flex: 1;
  font-size: 26rpx;
  color: #333333;
}

.svc-search-bar__ph {
  color: #B2B2B2;
  font-size: 26rpx;
}

.svc-search-bar__btn {
  background: #11796F;
  border-radius: 8rpx;
  padding: 14rpx 32rpx;
  flex-shrink: 0;
}

.svc-search-bar__btn:active {
  opacity: 0.85;
}

.svc-search-bar__btn text {
  color: #FFFFFF;
  font-size: 26rpx;
}

/* ─── 分类 pills（demo cat-pills：4px 圆角小 pill）─── */
.services-categories {
  white-space: nowrap;
  width: 100%;
  padding: 0 32rpx 24rpx;
  box-sizing: border-box;
}

.services-categories__track {
  display: inline-flex;
  gap: 16rpx;
}

.services-category {
  display: inline-flex;
  align-items: center;
  padding: 12rpx 28rpx;
  border: 1rpx solid #E5E5E5;
  border-radius: 8rpx;
  background: #FFFFFF;
  flex-shrink: 0;
}

.services-category text {
  color: #666666;
  font-size: 26rpx;
  font-weight: 400;
}

.services-category--active {
  background: #11796F;
  border-color: #11796F;
}

.services-category--active text {
  color: #FFFFFF;
}

/* ─── 宠物 tabs（demo pet-tabs：下划线指示器）─── */
.services-tabs {
  display: flex;
  padding: 0 32rpx;
  border-bottom: 1rpx solid #E5E5E5;
}

.services-tab {
  flex: 1;
  text-align: center;
  padding: 20rpx 0;
  position: relative;
}

.services-tab text {
  color: #666666;
  font-size: 28rpx;
  font-weight: 400;
}

.services-tab--active text {
  color: #11796F;
  font-weight: 600;
}

.services-tab--active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48rpx;
  height: 4rpx;
  background: #11796F;
  border-radius: 2rpx;
}

.services-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 28rpx;
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
  background: var(--pc-user-surface);
  border-radius: 40rpx 40rpx 0 0;
  padding: 40rpx 32rpx 64rpx;
}

.size-picker__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8rpx;
}

.size-picker__title {
  font-size: 36rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
}

.size-picker__close {
  font-size: 36rpx;
  color: var(--pc-user-muted);
  padding: 8rpx 24rpx;
}

.size-picker__hint {
  font-size: 22rpx;
  color: var(--pc-user-muted);
  margin-bottom: 32rpx;
}

.size-picker__option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx;
  border-radius: 24rpx;
  background: var(--pc-user-cream);
  margin-bottom: 16rpx;
}

.size-picker__option-info {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.size-picker__option-name {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--pc-user-ink);
}

.size-picker__option-weight {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.size-picker__option-right {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.size-picker__option-price {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--pc-user-accent);
}

.size-picker__option-arrow {
  font-size: 28rpx;
  color: var(--pc-user-muted);
}
</style>
