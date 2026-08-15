<template>
  <scroll-view scroll-x class="pc-date-selector">
    <view class="pc-date-selector__list">
      <view
        v-for="(day, idx) in days"
        :key="idx"
        class="pc-date-selector__item"
        :class="{ 'pc-date-selector__item--active': modelValue === day.value }"
        @tap="$emit('update:modelValue', day.value)"
      >
        <text class="pc-date-selector__weekday">{{ day.weekday }}</text>
        <text class="pc-date-selector__date">{{ day.label }}</text>
      </view>
    </view>
  </scroll-view>
</template>

<script setup lang="ts">
import { computed } from 'vue'

defineProps<{
  modelValue?: string
}>()

defineEmits<{
  (e: 'update:modelValue', value: string): void
}>()

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六']

const days = computed(() => {
  const result: { value: string; label: string; weekday: string }[] = []
  const now = new Date()
  for (let i = 0; i < 14; i++) {
    const d = new Date(now)
    d.setDate(d.getDate() + i)
    const month = d.getMonth() + 1
    const day = d.getDate()
    result.push({
      value: `${d.getFullYear()}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`,
      label: `${month}/${day}`,
      weekday: i === 0 ? '今天' : `周${WEEKDAYS[d.getDay()]}`,
    })
  }
  return result
})
</script>

<style scoped>
.pc-date-selector {
  white-space: nowrap;
}

.pc-date-selector__list {
  display: flex;
  gap: 16rpx;
  padding: 8rpx 0;
}

.pc-date-selector__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 16rpx 24rpx;
  border-radius: 24rpx;
  background: var(--pc-user-surface);
  border: 1px solid var(--pc-user-line);
  min-width: 104rpx;
}

.pc-date-selector__item--active {
  background: var(--pc-user-primary);
  border-color: var(--pc-user-primary);
}

.pc-date-selector__item--active .pc-date-selector__weekday,
.pc-date-selector__item--active .pc-date-selector__date {
  color: var(--pc-user-surface);
}

.pc-date-selector__weekday {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.pc-date-selector__date {
  font-size: 28rpx;
  color: var(--pc-user-ink);
  font-weight: 600;
}
</style>
