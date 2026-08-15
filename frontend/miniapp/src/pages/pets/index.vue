<template>
  <view class="pc-page pets-page">
    <PcPageHeader title="我的宠物" />

    <PcStatePanel
      :status="listStatus"
      empty-text="还没有添加宠物，点击下方按钮添加"
      @retry="loadPets"
    >
      <view class="pets-list">
        <view
          v-for="pet in pets"
          :key="pet.petId"
          class="pet-card"
          @tap="goEdit(pet.petId)"
        >
          <view class="pet-card__avatar">
            <text class="pet-card__avatar-text">{{ pet.name.charAt(0) }}</text>
          </view>
          <view class="pet-card__info">
            <text class="pet-card__name">{{ pet.name }}</text>
            <text class="pet-card__detail">{{ typeLabel(pet.type) }}{{ pet.breed ? ' · ' + pet.breed : '' }}</text>
            <view class="pet-card__tags">
              <text v-if="pet.gender != null" class="pet-card__tag">{{ pet.gender === 1 ? '公' : '母' }}</text>
              <text v-if="pet.age != null" class="pet-card__tag">{{ pet.age }}岁</text>
              <text v-if="pet.weight != null" class="pet-card__tag">{{ pet.weight }}kg</text>
            </view>
          </view>
          <text class="pet-card__arrow">?</text>
        </view>
      </view>
    </PcStatePanel>

    <view class="pets-action">
      <PcPrimaryButton text="添加宠物" @press="goAdd" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'
import { getMyPets, deletePet, type PetItem } from '@/api/user'

const listStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const pets = ref<PetItem[]>([])

const typeMap: Record<string, string> = { DOG: '狗狗', CAT: '猫咪', OTHER: '其他' }
function typeLabel(type: string): string {
  return typeMap[type] ?? type
}

async function loadPets() {
  listStatus.value = 'loading'
  const res = await getMyPets()
  if (!res.success || !res.data) {
    listStatus.value = 'error'
    return
  }
  pets.value = res.data
  listStatus.value = pets.value.length > 0 ? 'success' : 'empty'
}

function goAdd() {
  uni.navigateTo({ url: '/pages/pets/edit' })
}

function goEdit(petId: string) {
  uni.navigateTo({ url: `/pages/pets/edit?id=${petId}` })
}

async function handleDelete(petId: string) {
  uni.showModal({
    title: '确认删除',
    content: '确定要删除这只宠物吗？',
    success: async (res) => {
      if (res.confirm) {
        await deletePet(petId)
        loadPets()
      }
    },
  })
}

// expose for long-press delete via uni native
defineExpose({ handleDelete })

// 每次显示时刷新（从编辑页返回后自动更新列表）
onShow(() => {
  loadPets()
})
</script>

<style scoped>
.pets-page {
  padding: 40rpx;
}

.pets-list {
  display: flex;
  flex-direction: column;
  gap: 28rpx;
}

.pet-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
  background: var(--pc-user-surface);
  border-radius: 32rpx;
  padding: 32rpx;
  box-shadow: 0 2px 8px rgba(25, 50, 46, 0.06);
}

.pet-card__avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: var(--pc-user-soft);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.pet-card__avatar-text {
  font-size: 40rpx;
  font-weight: 700;
  color: var(--pc-user-primary);
}

.pet-card__info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.pet-card__name {
  font-size: 32rpx;
  font-weight: 600;
  color: var(--pc-user-ink);
}

.pet-card__detail {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.pet-card__tags {
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
}

.pet-card__tag {
  font-size: 22rpx;
  color: var(--pc-user-muted);
  background: var(--pc-user-cream);
  padding: 4rpx 16rpx;
  border-radius: 16rpx;
}

.pet-card__arrow {
  font-size: 28rpx;
  color: var(--pc-user-muted);
}

.pets-action {
  margin-top: 48rpx;
}
</style>
