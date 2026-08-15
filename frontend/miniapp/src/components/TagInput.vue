<template>
  <view class="tag-input">
    <!-- Selected tags -->
    <view class="tag-input__tags" v-if="selectedTags.length > 0">
      <view
        v-for="(tag, index) in selectedTags"
        :key="index"
        class="tag-input__tag"
        @tap="removeTag(index)"
      >
        <text class="tag-input__tag-text">#{{ tag }}</text>
        <text class="tag-input__tag-close">×</text>
      </view>
    </view>

    <!-- Input row -->
    <view class="tag-input__row" v-if="selectedTags.length < max">
      <text class="tag-input__prefix">#</text>
      <input
        class="tag-input__field"
        type="text"
        v-model="inputText"
        :placeholder="placeholder"
        @input="handleInput"
        @confirm="addCurrentTag"
      />
    </view>

    <!-- Max reached hint -->
    <text class="tag-input__hint" v-if="selectedTags.length >= max">
      最多 {{ max }} 个标签
    </text>

    <!-- Autocomplete dropdown -->
    <view class="tag-input__dropdown" v-if="showDropdown && suggestions.length > 0">
      <view
        v-for="item in suggestions"
        :key="item.id"
        class="tag-input__suggestion"
        @tap="selectSuggestion(item.name)"
      >
        <text class="tag-input__suggestion-text">#{{ item.name }}</text>
        <text class="tag-input__suggestion-count">{{ item.usageCount }} 次使用</text>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { searchTags } from '@/api/community'
import type { TagItem } from '@/types/community'

const props = withDefaults(defineProps<{
  modelValue: string[]
  max?: number
  placeholder?: string
}>(), {
  max: 3,
  placeholder: '输入标签名，回车添加',
})

const emit = defineEmits<{
  'update:modelValue': [tags: string[]]
}>()

const inputText = ref('')
const showDropdown = ref(false)
const suggestions = ref<TagItem[]>([])
let debounceTimer: ReturnType<typeof setTimeout> | null = null

const selectedTags = ref<string[]>([...props.modelValue])

watch(() => props.modelValue, (val) => {
  selectedTags.value = [...val]
})

function handleInput() {
  const text = inputText.value.trim()

  if (!text) {
    showDropdown.value = false
    suggestions.value = []
    return
  }

  showDropdown.value = true

  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(async () => {
    const res = await searchTags(text, 10)
    if (res.success && res.data) {
      suggestions.value = res.data
    }
  }, 300)
}

function addCurrentTag() {
  const name = inputText.value.trim().replace(/^#/, '').toLowerCase()
  if (!name) return
  if (selectedTags.value.length >= props.max) return
  if (selectedTags.value.includes(name)) {
    inputText.value = ''
    showDropdown.value = false
    return
  }

  const updated = [...selectedTags.value, name]
  selectedTags.value = updated
  emit('update:modelValue', updated)

  inputText.value = ''
  showDropdown.value = false
  suggestions.value = []
}

function selectSuggestion(name: string) {
  if (selectedTags.value.length >= props.max) return
  if (selectedTags.value.includes(name)) {
    inputText.value = ''
    showDropdown.value = false
    return
  }

  const updated = [...selectedTags.value, name]
  selectedTags.value = updated
  emit('update:modelValue', updated)

  inputText.value = ''
  showDropdown.value = false
  suggestions.value = []
}

function removeTag(index: number) {
  const updated = selectedTags.value.filter((_, i) => i !== index)
  selectedTags.value = updated
  emit('update:modelValue', updated)
}
</script>

<style scoped>
.tag-input {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.tag-input__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.tag-input__tag {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 8rpx 24rpx;
  background: var(--pc-user-primary);
  border-radius: 32rpx;
}

.tag-input__tag-text {
  color: var(--pc-user-surface);
  font-size: 22rpx;
}

.tag-input__tag-close {
  color: rgba(255, 255, 255, 0.8);
  font-size: 32rpx;
}

.tag-input__row {
  display: flex;
  align-items: center;
  height: 88rpx;
  border: 1px solid var(--pc-user-line);
  border-radius: 24rpx;
  padding: 0 28rpx;
  background: var(--pc-user-surface);
}

.tag-input__prefix {
  color: var(--pc-user-primary);
  font-size: 28rpx;
  font-weight: 600;
  margin-right: 8rpx;
}

.tag-input__field {
  flex: 1;
  font-size: 28rpx;
  color: var(--pc-user-ink);
  border: none;
  outline: none;
}

.tag-input__hint {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.tag-input__dropdown {
  background: var(--pc-user-surface);
  border-radius: 24rpx;
  box-shadow: 0 4px 16px rgba(25, 50, 46, 0.12);
  overflow: hidden;
  z-index: 10;
}

.tag-input__suggestion {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 28rpx;
  border-bottom: 1px solid var(--pc-user-line);
}

.tag-input__suggestion:last-child {
  border-bottom: none;
}

.tag-input__suggestion-text {
  font-size: 28rpx;
  color: var(--pc-user-primary);
}

.tag-input__suggestion-count {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}
</style>
