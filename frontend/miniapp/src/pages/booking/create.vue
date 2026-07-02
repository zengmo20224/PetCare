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
          <PcStatePanel :status="petsStatus" empty-text="暂未添加宠物，可不选">
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
          </PcStatePanel>
        </view>

        <!-- Address Selection (HOME / BOTH only) -->
        <view v-if="needAddress" class="booking-section">
          <text class="booking-label">上门地址（必选）</text>
          <PcStatePanel :status="addressesStatus" empty-text="暂未添加地址，请先在个人中心添加地址">
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

        <!-- Submit -->
        <view class="booking-action">
          <PcPrimaryButton text="提交预约" :loading="submitting" @tap="handleSubmit" />
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
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'
import PcFormField from '@/components/PcFormField.vue'
import { getServiceDetail } from '@/api/service'
import { getAvailability, createBooking } from '@/api/booking'
import { getMyPets, getMyAddresses, type PetItem, type AddressItem } from '@/api/user'
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

  submitting.value = true
  const res = await createBooking({
    storeId: STORE_ID,
    serviceItemId: serviceItem.value.id,
    serviceMode: selectedMode.value,
    bookingDate: selectedDate.value,
    startTime: selectedSlot.value,
    contactName: contactName.value,
    contactPhone: contactPhone.value,
    paymentMethod: 'OFFLINE_STORE',
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
})
</script>

<style scoped>
.booking-create {
  padding: 20px;
}

.booking-service {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-radius: 16px;
  padding: 16px;
  margin-bottom: 16px;
}

.booking-service__name {
  font-size: 16px;
  font-weight: 700;
  color: #19322E;
}

.booking-service__price {
  font-size: 14px;
  color: #F5A623;
  font-weight: 700;
}

.booking-section {
  margin-bottom: 20px;
}

.booking-label {
  font-size: 14px;
  font-weight: 600;
  color: #19322E;
  margin-bottom: 8px;
}

.booking-dates {
  display: flex;
  gap: 8px;
  overflow-x: auto;
}

.booking-modes {
  display: flex;
  gap: 8px;
}

.booking-mode {
  flex: 1;
  padding: 12px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #E2E9E6;
  text-align: center;
}

.booking-mode--active {
  background: #11796F;
  border-color: #11796F;
}

.booking-mode--active text {
  color: #fff;
}

.booking-date {
  padding: 8px 16px;
  border-radius: 12px;
  background: #fff;
  border: 1px solid #E2E9E6;
  white-space: nowrap;
}

.booking-date--active {
  background: #11796F;
  border-color: #11796F;
}

.booking-date--active text {
  color: #fff;
}

.booking-slots {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.booking-slot {
  padding: 8px 14px;
  border-radius: 10px;
  background: #fff;
  border: 1px solid #E2E9E6;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
}

.booking-slot--active {
  background: #DFF2ED;
  border-color: #11796F;
}

.booking-slot__count {
  font-size: 11px;
  color: #71817D;
}

.pc-input {
  height: 44px;
  border: 1px solid #E2E9E6;
  border-radius: 12px;
  padding: 0 14px;
  font-size: 14px;
  color: #19322E;
  background: #fff;
}

.pc-select {
  width: 100%;
  height: 44px;
  border: 1px solid #E2E9E6;
  border-radius: 12px;
  padding: 0 14px;
  font-size: 14px;
  color: #19322E;
  background: #fff;
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
  color: #71817D;
}

.pc-select__text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pc-select__arrow {
  margin-left: 8px;
  color: #71817D;
}

.booking-action {
  margin-top: 24px;
}
</style>
