<template>
  <el-dialog
    :model-value="visible"
    :title="title"
    :width="width"
    :close-on-click-modal="!loading"
    :close-on-press-escape="!loading"
    :show-close="!loading"
    @close="handleCancel"
  >
    <p class="pc-action-confirm-dialog__message">{{ message }}</p>
    <template v-if="$slots.default">
      <slot />
    </template>
    <template #footer>
      <el-button :disabled="loading" @click="handleCancel">取消</el-button>
      <el-button
        :type="danger ? 'danger' : 'primary'"
        :loading="loading"
        :disabled="loading"
        @click="handleConfirm"
      >
        确认
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
const props = withDefaults(defineProps<{
  visible: boolean
  title?: string
  message?: string
  danger?: boolean
  width?: string
  loading?: boolean
}>(), {
  title: '确认操作',
  message: '确定执行此操作吗？',
  danger: false,
  width: '420px',
  loading: false,
})

const emit = defineEmits<{
  confirm: []
  cancel: []
}>()

const handleConfirm = () => {
  // loading 时不允许重复触发
  if (props.loading) return
  emit('confirm')
}

const handleCancel = () => {
  if (props.loading) return
  emit('cancel')
}
</script>

<style scoped>
.pc-action-confirm-dialog__message {
  color: var(--pc-ink);
  font-size: var(--pc-font-size-base);
  margin: 0 0 var(--pc-spacing-md) 0;
  line-height: 1.6;
}
</style>
