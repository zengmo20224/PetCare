<template>
  <view class="pc-page booking-create">
    <PcPageHeader title="创建预约" />

    <PcStatePanel :status="pageStatus" empty-text="请从服务页面进入预约">
      <template v-if="serviceItem">
        <!-- Service Summary -->
        <view class="booking-service">
          <text class="booking-service__name">{{ serviceItem.name }}</text>
          <text class="booking-service__price">¥{{ serviceItem.price }}</text>
        </view>

        <!-- Service Mode Selector (for BOTH) -->
        <view v-if="serviceItem.serviceMode === 'BOTH'" class="booking-section">
          <text class="booking-label">服务方式</text>
          <view class="booking-modes">
            <view
              class="booking-mode"
              :class="{ 'booking-mode--active': selectedMode === 'STORE' }"
              @tap="switchMode('STORE')"
            >
              <text>到店服务</text>
            </view>
            <view
              class="booking-mode"
              :class="{ 'booking-mode--active': selectedMode === 'HOME' }"
              @tap="switchMode('HOME')"
            >
              <text>上门服务</text>
            </view>
          </view>
        </view>

        <!-- Date Selection -->
        <view class="booking-section">
          <text class="booking-label">选择日期</text>
          <view class="booking-dates">
            <view
              v-for="d in dateOptions"
              :key="d.value"
              class="booking-date"
              :class="{ 'booking-date--active': selectedDate === d.value }"
              @tap="selectDate(d.value)"
            >
              <text>{{ d.label }}</text>
            </view>
          </view>
        </view>

        <!-- Time Slots -->
        <view class="booking-section">
          <text class="booking-label">可预约时段</text>
          <PcStatePanel :status="slotsStatus" empty-text="当天暂无可预约时段">
            <view class="booking-slots">
              <view
                v-for="slot in slots"
                :key="slot.startTime"
                class="booking-slot"
                :class="{ 'booking-slot--active': selectedSlot === slot.startTime }"
                @tap="selectedSlot = slot.startTime"
              >
                <text>{{ slot.startTime }}</text>
                <text class="booking-slot__count">{{ slot.availableStaffCount }}人可选</text>
              </view>
            </view>
          </PcStatePanel>
        </view>

        <!-- Pet Selection (optional) -->
        <view class="booking-section">
          <text class="booking-label">选择宠物{{ serviceItem.needPet ? '（必选）' : '（可选）' }}</text>
          <PcStatePanel :status="petsStatus" empty-text="暂未添加宠物">
            <picker
              class="pc-picker"
              mode="selector"
              :range="petPickerOptions"
              range-key="label"
              :value="selectedPetIndex"
              @change="handlePetPickerChange"
            >
              <view class="pc-select pc-select--picker">
                <text class="pc-select__text">{{ selectedPetLabel }}</text>
                <text class="pc-select__arrow">?</text>
              </view>
            </picker>
            <template #empty-action>
              <view class="booking-empty-action" @tap="goAddPet">+ 添加宠物</view>
            </template>
          </PcStatePanel>
        </view>

        <!-- Address Selection (HOME / BOTH only) -->
        <view v-if="needAddress" class="booking-section">
          <text class="booking-label">上门地址（必选）</text>
          <PcStatePanel :status="addressesStatus" empty-text="暂未添加地址">
            <picker
              class="pc-picker"
              mode="selector"
              :range="addressPickerOptions"
              range-key="label"
              :value="selectedAddressIndex"
              @change="handleAddressPickerChange"
            >
              <view class="pc-select pc-select--picker" :class="{ 'pc-select--placeholder': !selectedAddressId }">
                <text class="pc-select__text">{{ selectedAddressLabel }}</text>
                <text class="pc-select__arrow">?</text>
              </view>
            </picker>
            <template #empty-action>
              <view class="booking-empty-action" @tap="goAddAddress">+ 添加地址</view>
            </template>
          </PcStatePanel>
        </view>

        <!-- Contact Info -->
        <view class="booking-section">
          <text class="booking-label">联系人信息</text>
          <PcFormField label="姓名">
            <input class="pc-input" type="text" v-model="contactName" placeholder="联系人姓名" />
          </PcFormField>
          <PcFormField label="电话">
            <input class="pc-input" type="text" v-model="contactPhone" placeholder="联系电话" />
          </PcFormField>
        </view>

        <!-- Remark -->
        <view class="booking-section">
          <PcFormField label="备注（可选）">
            <input class="pc-input" type="text" v-model="remark" placeholder="如有特殊需求请备注" />
          </PcFormField>
        </view>

        <!-- Payment method switch (CR-20260718-003) -->
        <view class="booking-section">
          <text class="booking-label">支付方式</text>
          <view class="booking-pay">
            <view
              class="booking-pay__opt"
              :class="{ 'booking-pay__opt--on': paymentMethod === 'OFFLINE_STORE' }"
              @tap="paymentMethod = 'OFFLINE_STORE'"
            >
              <text>到店支付</text>
            </view>
            <view
              class="booking-pay__opt"
              :class="{ 'booking-pay__opt--on': paymentMethod === 'WALLET' }"
              @tap="paymentMethod = 'WALLET'"
            >
              <text>钱包余额</text>
            </view>
          </view>
          <view v-if="paymentMethod === 'WALLET'" class="booking-wallet-hint">
            <text>钱包余额：¥{{ walletBalance.toFixed(2) }}</text>
            <text v-if="walletInsufficient" class="booking-wallet-hint--low">（余额不足，请充值或选择到店支付）</text>
          </view>
        </view>

        <!-- Submit -->
        <view class="booking-action">
          <PcPrimaryButton text="提交预约" :loading="submitting" @press="handleSubmit" />
        </view>
      </template>
    </PcStatePanel>
  </view>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'
import PcFormField from '@/components/PcFormField.vue'
import { getServiceDetail } from '@/api/service'
import { getAvailability, createBooking } from '@/api/booking'
import { getMyPets, getMyAddresses, type PetItem, type AddressItem } from '@/api/user'
import { getMyWallet } from '@/api/wallet'
import { useUserStore } from '@/store/user'
import type { ServiceItem } from '@/types/service'
import type { BookingSlot } from '@/types/booking'
import { normalizeRouteParam } from '@/utils/route-query'

const STORE_ID = '1001'

const userStore = useUserStore()
const serviceItem = ref<ServiceItem | null>(null)
const pageStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const currentServiceId = ref('')

const selectedMode = ref<'STORE' | 'HOME'>('STORE')
const dateOptions = ref<{label: string; value: string}[]>([])
const selectedDate = ref('')
const selectedSlot = ref('')

const slots = ref<BookingSlot[]>([])
const slotsStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')

const contactName = ref('')
const contactPhone = ref('')
const remark = ref('')
const submitting = ref(false)

// Payment (CR-20260718-003): OFFLINE_STORE (default) or WALLET.
const paymentMethod = ref<'OFFLINE_STORE' | 'WALLET'>('OFFLINE_STORE')
const walletBalance = ref(0)
const walletInsufficient = computed(() => {
  if (paymentMethod.value !== 'WALLET' || !serviceItem.value?.price) return false
  return walletBalance.value < serviceItem.value.price
})

// Pet selection
const pets = ref<PetItem[]>([])
const petsStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const selectedPetId = ref('')

// Address selection
const addresses = ref<AddressItem[]>([])
const addressesStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const selectedAddressId = ref('')

interface PickerOption {
  label: string
  value: string
}

const petPickerOptions = computed<PickerOption[]>(() => [
  { label: '不选择宠物', value: '' },
  ...pets.value.map((pet) => ({
    label: `${pet.name}（${pet.type}${pet.breed ? ' · ' + pet.breed : ''}）`,
    value: pet.petId,
  })),
])

const addressPickerOptions = computed<PickerOption[]>(() => [
  { label: '请选择地址', value: '' },
  ...addresses.value.map((addr) => ({
    label: formatAddressLabel(addr),
    value: addr.addressId,
  })),
])

const selectedPetIndex = computed(() => findPickerIndex(petPickerOptions.value, selectedPetId.value))
const selectedAddressIndex = computed(() =>
  findPickerIndex(addressPickerOptions.value, selectedAddressId.value)
)
const selectedPetLabel = computed(() => petPickerOptions.value[selectedPetIndex.value]?.label ?? '不选择宠物')
const selectedAddressLabel = computed(() =>
  addressPickerOptions.value[selectedAddressIndex.value]?.label ?? '请选择地址'
)

const needAddress = computed(() => {
  return selectedMode.value === 'HOME'
})

function formatAddressLabel(addr: AddressItem): string {
  const address = `${addr.province}${addr.city}${addr.district} ${addr.detailAddress}`
  const contact = `（${addr.contactName} ${addr.contactPhone}）`
  return `${address}${contact}${addr.isDefault ? ' ★' : ''}`
}

function findPickerIndex(options: PickerOption[], selectedValue: string): number {
  const index = options.findIndex((option) => option.value === selectedValue)
  return index >= 0 ? index : 0
}

function getPickerEventIndex(e: any): number {
  const index = Number(e?.detail?.value)
  return Number.isFinite(index) ? index : 0
}

function handlePetPickerChange(e: any) {
  const option = petPickerOptions.value[getPickerEventIndex(e)]
  selectedPetId.value = option?.value ?? ''
}

function handleAddressPickerChange(e: any) {
  const option = addressPickerOptions.value[getPickerEventIndex(e)]
  selectedAddressId.value = option?.value ?? ''
}

function switchMode(mode: 'STORE' | 'HOME') {
  if (selectedMode.value === mode) return
  selectedMode.value = mode
  selectedSlot.value = ''
  loadSlots()
  if (mode === 'HOME') {
    loadAddresses()
  }
}

function initDateOptions() {
  const today = new Date()
  const opts: {label: string; value: string}[] = []
  for (let i = 0; i < 7; i++) {
    const d = new Date(today)
    d.setDate(d.getDate() + i)
    // 用本地日期分量拼接，避免 toISOString() 走 UTC 导致日期偏移 -1 天
    const value = `${d.getFullYear()}-${String(d.getMonth()+1).padStart(2,'0')}-${String(d.getDate()).padStart(2,'0')}`
    const label = i === 0 ? '今天' : i === 1 ? '明天' : `${d.getMonth()+1}/${d.getDate()}`
    opts.push({ label, value })
  }
  dateOptions.value = opts
}

async function loadService(id: string) {
  pageStatus.value = 'loading'
  const res = await getServiceDetail(id)
  if (!res.success || !res.data) {
    pageStatus.value = 'error'
    return
  }
  serviceItem.value = res.data
  pageStatus.value = 'success'

  // Initialize service mode based on service config
  const rawMode = serviceItem.value.serviceMode
  selectedMode.value = rawMode === 'HOME' ? 'HOME' : 'STORE'

  // Pre-fill contact phone from user profile
  if (userStore.profile?.phone) {
    contactPhone.value = userStore.profile.phone
  }
  if (userStore.profile?.nickname) {
    contactName.value = userStore.profile.nickname
  }

  if (dateOptions.value.length > 0) {
    selectDate(dateOptions.value[0].value)
  }

  // Load pets and addresses in parallel (non-blocking)
  loadPets()
  if (selectedMode.value === 'HOME') {
    loadAddresses()
  }
}

async function loadPets() {
  petsStatus.value = 'loading'
  const res = await getMyPets()
  if (!res.success || !res.data) {
    petsStatus.value = 'empty'
    return
  }
  pets.value = res.data
  petsStatus.value = pets.value.length > 0 ? 'success' : 'empty'
}

async function loadAddresses() {
  addressesStatus.value = 'loading'
  const res = await getMyAddresses()
  if (!res.success || !res.data) {
    addressesStatus.value = 'empty'
    return
  }
  addresses.value = res.data
  // Auto-select default address
  const defaultAddr = addresses.value.find(a => a.isDefault)
  if (defaultAddr) {
    selectedAddressId.value = defaultAddr.addressId
  }
  addressesStatus.value = addresses.value.length > 0 ? 'success' : 'empty'
}

async function selectDate(date: string) {
  selectedDate.value = date
  selectedSlot.value = ''
  await loadSlots()
}

async function loadSlots() {
  if (!serviceItem.value || !selectedDate.value) return

  slotsStatus.value = 'loading'
  const res = await getAvailability({
    storeId: STORE_ID,
    serviceItemId: serviceItem.value.id,
    bookingDate: selectedDate.value,
    serviceMode: selectedMode.value,
  })

  if (!res.success || !res.data) {
    slotsStatus.value = 'error'
    return
  }

  slots.value = res.data.slots
  slotsStatus.value = slots.value.length > 0 ? 'success' : 'empty'
}

function goAddPet() {
  uni.navigateTo({ url: '/pages/pets/index' })
}

function goAddAddress() {
  uni.navigateTo({ url: '/pages/addresses/index' })
}

async function handleSubmit() {
  if (!userStore.isLoggedIn) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => uni.navigateTo({ url: '/pages/auth/login' }), 1000)
    return
  }
  if (!serviceItem.value || !selectedDate.value || !selectedSlot.value) {
    uni.showToast({ title: '请选择预约时间', icon: 'none' })
    return
  }
  if (!contactName.value || !contactPhone.value) {
    uni.showToast({ title: '请填写联系人', icon: 'none' })
    return
  }

  // Validate pet if required
  if (serviceItem.value.needPet && !selectedPetId.value) {
    uni.showToast({ title: '请选择宠物', icon: 'none' })
    return
  }

  // Validate address for HOME mode
  if (needAddress.value && !selectedAddressId.value) {
    uni.showToast({ title: '请选择上门地址', icon: 'none' })
    return
  }

  // Validate wallet balance if paying by wallet (CR-20260718-003)
  if (paymentMethod.value === 'WALLET' && walletInsufficient.value) {
    uni.showToast({
      title: '钱包余额不足，请充值或选择到店支付',
      icon: 'none',
    })
    return
  }

  submitting.value = true
  const res = await createBooking({
    storeId: STORE_ID,
    serviceItemId: serviceItem.value.id,
    serviceMode: selectedMode.value,
    bookingDate: selectedDate.value,
    startTime: selectedSlot.value,
    contactName: contactName.value,
    contactPhone: contactPhone.value,
    paymentMethod: paymentMethod.value,
    petId: selectedPetId.value || undefined,
    addressId: selectedAddressId.value || undefined,
    remark: remark.value || undefined,
  })
  submitting.value = false

  if (res.success) {
    uni.redirectTo({ url: '/pages/booking/success' })
  }
}

onLoad((query) => {
  initDateOptions()
  const serviceId = normalizeRouteParam(query?.serviceId ?? currentServiceId.value)

  if (!serviceId) {
    serviceItem.value = null
    pageStatus.value = 'empty'
    return
  }

  currentServiceId.value = serviceId
  loadService(serviceId)
  loadWalletBalance()
})

/** Load the user's wallet balance for the wallet-payment option. Non-fatal on failure. */
async function loadWalletBalance() {
  const res = await getMyWallet()
  if (res.success && res.data) {
    walletBalance.value = res.data.balance
  }
}

// Refresh pets/addresses when returning from their add pages
onShow(() => {
  if (!currentServiceId.value) return
  loadPets()
  if (selectedMode.value === 'HOME') {
    loadAddresses()
  }
})
</script>

<style scoped>
.booking-create {
  padding: 40rpx;
}

.booking-service {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--pc-user-surface);
  border-radius: 32rpx;
  padding: 32rpx;
  margin-bottom: 32rpx;
}

.booking-service__name {
  font-size: 32rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
}

.booking-service__price {
  font-size: 28rpx;
  color: var(--pc-user-danger);
  font-weight: 700;
}

.booking-section {
  margin-bottom: 40rpx;
}

.booking-label {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--pc-user-ink);
  margin-bottom: 16rpx;
}

.booking-dates {
  display: flex;
  gap: 16rpx;
  overflow-x: auto;
}

.booking-modes {
  display: flex;
  gap: 16rpx;
}

.booking-mode {
  flex: 1;
  padding: 24rpx;
  border-radius: 24rpx;
  background: var(--pc-user-surface);
  border: 1px solid var(--pc-user-line);
  text-align: center;
}

.booking-mode--active {
  background: var(--pc-user-primary);
  border-color: var(--pc-user-primary);
}

.booking-mode--active text {
  color: var(--pc-user-surface);
}

.booking-date {
  padding: 16rpx 32rpx;
  border-radius: 24rpx;
  background: var(--pc-user-surface);
  border: 1px solid var(--pc-user-line);
  white-space: nowrap;
}

.booking-date--active {
  background: var(--pc-user-primary);
  border-color: var(--pc-user-primary);
}

.booking-date--active text {
  color: var(--pc-user-surface);
}

.booking-slots {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.booking-slot {
  padding: 16rpx 28rpx;
  border-radius: 20rpx;
  background: var(--pc-user-surface);
  border: 1px solid var(--pc-user-line);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}

.booking-slot--active {
  background: var(--pc-user-soft);
  border-color: var(--pc-user-primary);
}

.booking-slot__count {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.pc-input {
  height: 88rpx;
  border: 1px solid var(--pc-user-line);
  border-radius: 24rpx;
  padding: 0 28rpx;
  font-size: 28rpx;
  color: var(--pc-user-ink);
  background: var(--pc-user-surface);
}

.pc-select {
  width: 100%;
  height: 88rpx;
  border: 1px solid var(--pc-user-line);
  border-radius: 24rpx;
  padding: 0 28rpx;
  font-size: 28rpx;
  color: var(--pc-user-ink);
  background: var(--pc-user-surface);
  box-sizing: border-box;
}

.pc-picker {
  display: block;
}

.pc-select--picker {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pc-select--placeholder {
  color: var(--pc-user-muted);
}

.pc-select__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pc-select__arrow {
  margin-left: 16rpx;
  color: var(--pc-user-muted);
}

.booking-action {
  margin-top: 48rpx;
}

.booking-empty-action {
  margin-top: 20rpx;
  padding: 16rpx 40rpx;
  border-radius: 1998rpx;
  background: var(--pc-user-primary);
  color: var(--pc-user-surface);
  font-size: 26rpx;
  font-weight: 600;
  display: inline-block;
}

/* Payment method switch (CR-20260718-003) */
.booking-pay {
  display: flex;
  gap: 20rpx;
}

.booking-pay__opt {
  flex: 1;
  height: 84rpx;
  border-radius: 16rpx;
  background: var(--pc-user-surface);
  border: 1px solid var(--pc-user-line);
  display: flex;
  align-items: center;
  justify-content: center;
}

.booking-pay__opt--on {
  background: var(--pc-user-primary);
  border-color: var(--pc-user-primary);
}

.booking-pay__opt--on text {
  color: var(--pc-user-surface);
  font-weight: 600;
}

.booking-pay__opt text {
  font-size: 28rpx;
  color: var(--pc-user-ink);
}

.booking-wallet-hint {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: var(--pc-user-muted);
}

.booking-wallet-hint--low {
  color: #E85D4E;
}
</style>
