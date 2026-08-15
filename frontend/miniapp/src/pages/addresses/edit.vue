<template>
  <view class="pc-page addr-edit">
    <PcPageHeader :title="isEdit ? '编辑地址' : '添加地址'" />

    <view class="addr-edit__form">
      <PcFormField label="联系人姓名（必填）">
        <input class="pc-input" type="text" v-model="form.contactName" placeholder="联系人姓名" />
      </PcFormField>

      <PcFormField label="联系人手机号（必填）">
        <input class="pc-input" type="text" v-model="form.contactPhone" placeholder="手机号码" />
      </PcFormField>

      <PcFormField label="省份（必填）">
        <input class="pc-input" type="text" v-model="form.province" placeholder="如：广东省" />
      </PcFormField>

      <PcFormField label="城市（必填）">
        <input class="pc-input" type="text" v-model="form.city" placeholder="如：深圳市" />
      </PcFormField>

      <PcFormField label="区/县">
        <input class="pc-input" type="text" v-model="form.district" placeholder="如：南山区" />
      </PcFormField>

      <PcFormField label="详细地址（必填）">
        <input class="pc-input" type="text" v-model="form.detailAddress" placeholder="街道、门牌号等" />
      </PcFormField>

      <PcFormField label="设为默认地址">
        <view class="addr-edit__toggle" @tap="form.isDefault = !form.isDefault">
          <text :class="form.isDefault ? 'addr-edit__toggle--on' : ''">{{ form.isDefault ? '✓' : '' }}</text>
        </view>
      </PcFormField>

      <view class="addr-edit__actions">
        <PcPrimaryButton :text="isEdit ? '保存修改' : '添加地址'" :loading="saving" @press="handleSave" />
      </view>

      <view v-if="isEdit" class="addr-edit__delete" @tap="handleDelete">
        <text class="addr-edit__delete-text">删除这个地址</text>
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
import { getMyAddresses, createAddress, updateAddress, deleteAddress } from '@/api/user'
import { normalizeRouteParam } from '@/utils/route-query'

const isEdit = ref(false)
const editId = ref('')
const saving = ref(false)

const form = reactive({
  contactName: '',
  contactPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  isDefault: false,
})

function buildPayload() {
  return {
    contactName: form.contactName,
    contactPhone: form.contactPhone,
    province: form.province,
    city: form.city,
    district: form.district || undefined,
    detailAddress: form.detailAddress,
    isDefault: form.isDefault,
  }
}

async function loadAddress(id: string) {
  const res = await getMyAddresses()
  if (res.success && res.data) {
    const addr = res.data.find((a) => a.addressId === id)
    if (addr) {
      form.contactName = addr.contactName
      form.contactPhone = addr.contactPhone
      form.province = addr.province ?? ''
      form.city = addr.city ?? ''
      form.district = addr.district ?? ''
      form.detailAddress = addr.detailAddress ?? ''
      form.isDefault = addr.isDefault
    }
  }
}

async function handleSave() {
  if (!form.contactName || !form.contactPhone) {
    uni.showToast({ title: '请填写联系人信息', icon: 'none' })
    return
  }
  if (!form.province || !form.city || !form.detailAddress) {
    uni.showToast({ title: '请填写完整地址', icon: 'none' })
    return
  }

  saving.value = true
  const payload = buildPayload()
  const res = isEdit.value
    ? await updateAddress(editId.value, payload)
    : await createAddress(payload)
  saving.value = false

  if (res.success) {
    uni.showToast({ title: isEdit.value ? '已保存' : '已添加', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  }
}

async function handleDelete() {
  uni.showModal({
    title: '确认删除',
    content: '确定要删除这个地址吗？',
    success: async (res) => {
      if (res.confirm) {
        await deleteAddress(editId.value)
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
    loadAddress(id)
  }
})
</script>

<style scoped>
.addr-edit {
  padding: 40rpx;
}

.addr-edit__form {
  margin-top: 32rpx;
}

.addr-edit__actions {
  margin-top: 48rpx;
}

.addr-edit__delete {
  margin-top: 32rpx;
  text-align: center;
  padding: 24rpx;
}

.addr-edit__delete-text {
  font-size: 28rpx;
  color: #e05050;
}

.addr-edit__toggle {
  width: 48rpx;
  height: 48rpx;
  border: 2px solid var(--pc-user-line);
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.addr-edit__toggle--on {
  font-size: 32rpx;
  color: var(--pc-user-primary);
  font-weight: 700;
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
</style>
