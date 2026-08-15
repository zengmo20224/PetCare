<template>
  <view class="pc-page pet-edit">
    <PcPageHeader :title="isEdit ? '编辑宠物' : '添加宠物'" />

    <view class="pet-edit__form">
      <PcFormField label="宠物名称（必填）">
        <input class="pc-input" type="text" v-model="form.name" placeholder="如：小黑" />
      </PcFormField>

      <PcFormField label="宠物类型（必填）">
        <picker
          class="pc-picker"
          mode="selector"
          :range="petTypeOptions"
          range-key="label"
          :value="pickerIndex(petTypeOptions, form.type)"
          @change="handleTypePickerChange"
        >
          <view class="pc-select pc-select--picker" :class="{ 'pc-select--placeholder': !form.type }">
            <text class="pc-select__text">{{ pickerLabel(petTypeOptions, form.type) }}</text>
            <text class="pc-select__arrow">?</text>
          </view>
        </picker>
      </PcFormField>

      <PcFormField label="品种">
        <input class="pc-input" type="text" v-model="form.breed" placeholder="如：金毛" />
      </PcFormField>

      <PcFormField label="性别">
        <picker
          class="pc-picker"
          mode="selector"
          :range="genderOptions"
          range-key="label"
          :value="pickerIndex(genderOptions, form.gender)"
          @change="handleGenderPickerChange"
        >
          <view class="pc-select pc-select--picker" :class="{ 'pc-select--placeholder': !form.gender }">
            <text class="pc-select__text">{{ pickerLabel(genderOptions, form.gender) }}</text>
            <text class="pc-select__arrow">?</text>
          </view>
        </picker>
      </PcFormField>

      <PcFormField label="年龄（岁）">
        <input class="pc-input" type="number" v-model="form.age" placeholder="如：2" />
      </PcFormField>

      <PcFormField label="体重（kg）">
        <input class="pc-input" type="number" v-model="form.weight" placeholder="如：5.5" />
      </PcFormField>

      <PcFormField label="体型">
        <picker
          class="pc-picker"
          mode="selector"
          :range="sizeOptions"
          range-key="label"
          :value="pickerIndex(sizeOptions, form.size)"
          @change="handleSizePickerChange"
        >
          <view class="pc-select pc-select--picker" :class="{ 'pc-select--placeholder': !form.size }">
            <text class="pc-select__text">{{ pickerLabel(sizeOptions, form.size) }}</text>
            <text class="pc-select__arrow">?</text>
          </view>
        </picker>
      </PcFormField>

      <PcFormField label="是否绝育">
        <picker
          class="pc-picker"
          mode="selector"
          :range="sterilizedOptions"
          range-key="label"
          :value="pickerIndex(sterilizedOptions, form.sterilized)"
          @change="handleSterilizedPickerChange"
        >
          <view class="pc-select pc-select--picker" :class="{ 'pc-select--placeholder': !form.sterilized }">
            <text class="pc-select__text">{{ pickerLabel(sterilizedOptions, form.sterilized) }}</text>
            <text class="pc-select__arrow">?</text>
          </view>
        </picker>
      </PcFormField>

      <PcFormField label="备注">
        <input class="pc-input" type="text" v-model="form.remark" placeholder="如：对某种食物过敏" />
      </PcFormField>

      <view class="pet-edit__actions">
        <PcPrimaryButton :text="isEdit ? '保存修改' : '添加宠物'" :loading="saving" @press="handleSave" />
      </view>

      <view v-if="isEdit" class="pet-edit__delete" @tap="handleDelete">
        <text class="pet-edit__delete-text">删除这只宠物</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcFormField from '@/components/PcFormField.vue'
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'
import { getMyPets, createPet, updatePet, deletePet } from '@/api/user'
import { normalizeRouteParam } from '@/utils/route-query'

const isEdit = ref(false)
const editId = ref('')
const saving = ref(false)

interface PickerOption {
  label: string
  value: string
}

const petTypeOptions: PickerOption[] = [
  { label: '请选择', value: '' },
  { label: '狗狗', value: 'DOG' },
  { label: '猫咪', value: 'CAT' },
  { label: '其他', value: 'OTHER' },
]

const genderOptions: PickerOption[] = [
  { label: '不选择', value: '' },
  { label: '公', value: '1' },
  { label: '母', value: '0' },
]

const sizeOptions: PickerOption[] = [
  { label: '不选择', value: '' },
  { label: '小型', value: 'SMALL' },
  { label: '中型', value: 'MEDIUM' },
  { label: '大型', value: 'LARGE' },
]

const sterilizedOptions: PickerOption[] = [
  { label: '不选择', value: '' },
  { label: '已绝育', value: '1' },
  { label: '未绝育', value: '0' },
]

const form = reactive({
  name: '',
  type: '',
  breed: '',
  gender: '' as string,
  age: '' as string,
  weight: '' as string,
  size: '',
  sterilized: '' as string,
  remark: '',
})

function pickerIndex(options: PickerOption[], selectedValue: string): number {
  const index = options.findIndex((option) => option.value === selectedValue)
  return index >= 0 ? index : 0
}

function pickerLabel(options: PickerOption[], selectedValue: string): string {
  return options[pickerIndex(options, selectedValue)]?.label ?? options[0]?.label ?? ''
}

function getPickerEventIndex(e: any): number {
  const index = Number(e?.detail?.value)
  return Number.isFinite(index) ? index : 0
}

function pickValue(options: PickerOption[], e: any): string {
  return options[getPickerEventIndex(e)]?.value ?? ''
}

function handleTypePickerChange(e: any) {
  form.type = pickValue(petTypeOptions, e)
}

function handleGenderPickerChange(e: any) {
  form.gender = pickValue(genderOptions, e)
}

function handleSizePickerChange(e: any) {
  form.size = pickValue(sizeOptions, e)
}

function handleSterilizedPickerChange(e: any) {
  form.sterilized = pickValue(sterilizedOptions, e)
}

function buildPayload() {
  return {
    name: form.name,
    type: form.type,
    breed: form.breed || undefined,
    gender: form.gender !== '' ? Number(form.gender) : undefined,
    age: form.age !== '' ? Number(form.age) : undefined,
    weight: form.weight !== '' ? Number(form.weight) : undefined,
    size: form.size || undefined,
    sterilized: form.sterilized !== '' ? Number(form.sterilized) : undefined,
    remark: form.remark || undefined,
  }
}

async function loadPet(id: string) {
  // Load all pets and find the one — simpler than adding a single-pet API
  const res = await getMyPets()
  if (res.success && res.data) {
    const pet = res.data.find((p) => p.petId === id)
    if (pet) {
      form.name = pet.name
      form.type = pet.type
      form.breed = pet.breed ?? ''
      form.gender = pet.gender != null ? String(pet.gender) : ''
      form.age = pet.age != null ? String(pet.age) : ''
      form.weight = pet.weight != null ? String(pet.weight) : ''
      form.size = pet.size ?? ''
      form.sterilized = pet.sterilized != null ? String(pet.sterilized) : ''
      form.remark = pet.remark ?? ''
    }
  }
}

async function handleSave() {
  if (!form.name) {
    uni.showToast({ title: '请填写宠物名称', icon: 'none' })
    return
  }
  if (!form.type) {
    uni.showToast({ title: '请选择宠物类型', icon: 'none' })
    return
  }

  saving.value = true
  const payload = buildPayload()
  const res = isEdit.value
    ? await updatePet(editId.value, payload)
    : await createPet(payload)
  saving.value = false

  if (res.success) {
    uni.showToast({ title: isEdit.value ? '已保存' : '已添加', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  }
}

async function handleDelete() {
  uni.showModal({
    title: '确认删除',
    content: '确定要删除这只宠物吗？',
    success: async (res) => {
      if (res.confirm) {
        await deletePet(editId.value)
        uni.showToast({ title: '已删除', icon: 'success' })
        setTimeout(() => uni.navigateBack(), 1000)
      }
    },
  })
}

onLoad((query) => {
  const id = normalizeRouteParam(query?.id)
  if (id) {
    isEdit.value = true
    editId.value = id
    loadPet(id)
  }
})
</script>

<style scoped>
.pet-edit {
  padding: 40rpx;
}

.pet-edit__form {
  margin-top: 32rpx;
}

.pet-edit__actions {
  margin-top: 48rpx;
}

.pet-edit__delete {
  margin-top: 32rpx;
  text-align: center;
  padding: 24rpx;
}

.pet-edit__delete-text {
  font-size: 28rpx;
  color: #e05050;
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
</style>
