<template>
  <view class="pc-time-slot-grid">
    <view
      v-for="slot in slots"
      :key="slot.time"
      class="pc-time-slot-grid__item"
      :class="{
        'pc-time-slot-grid__item--active': modelValue === slot.time,
        'pc-time-slot-grid__item--disabled': slot.disabled,
      }"
      @tap="!slot.disabled && $emit('update:modelValue', slot.time)"
    >
      <text>{{ slot.time }}</text>
    </view>
  </view>
</template>

<script setup lang="ts">
interface TimeSlot {
  time: string
  disabled?: boolean
}

defineProps<{
  slots: TimeSlot[]
  modelValue?: string
}>()

defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()
</script>

<style scoped>
.pc-time-slot-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16rpx;
}

.pc-time-slot-grid__item {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20rpx 0;
  border-radius: 24rpx;
  background: var(--pc-user-surface);
  border: 1px solid var(--pc-user-line);
  font-size: 28rpx;
  color: var(--pc-user-ink);
}

.pc-time-slot-grid__item--active {
  background: var(--pc-user-primary);
  border-color: var(--pc-user-primary);
  color: var(--pc-user-surface);
}

.pc-time-slot-grid__item--disabled {
  opacity: 0.4;
}
</style>
