<template>
  <view class="pc-page sq-page">
    <PcPageHeader title="密保管理" />

    <view class="sq-card">
      <view class="sq-brand">
        <text class="sq-brand__title">密保问题管理</text>
        <text class="sq-brand__subtitle">用于忘记密码时找回账号，请妥善设置 2 个密保</text>
      </view>

      <!-- 当前已设密保（只读展示） -->
      <view v-if="currentQuestions.length > 0" class="sq-current">
        <text class="sq-current__title">当前密保</text>
        <view v-for="(q, i) in currentQuestions" :key="q.id" class="sq-current__item">
          <text class="sq-current__index">{{ i + 1 }}</text>
          <text class="sq-current__question">{{ q.question }}</text>
        </view>
      </view>

      <view class="sq-divider" />

      <text class="sq-section-title">设置新密保（需填满 2 个）</text>

      <!-- 两个密保选择器 -->
      <view v-for="(sq, index) in form" :key="index" class="sq-item">
        <PcFormField :label="`问题 ${index + 1}`">
          <view class="pc-select-wrap">
            <picker
              class="pc-picker"
              mode="selector"
              :range="questionPickerOptions(index)"
              range-key="label"
              :value="questionPickerIndex(index)"
              @change="onQuestionPickerChange($event, index)"
            >
              <view class="pc-select pc-select--picker" :class="{ 'pc-select--placeholder': sq.questionIndex === null }">
                <text class="pc-select__text">{{ questionPickerLabel(index) }}</text>
                <text class="pc-select__arrow">›</text>
              </view>
            </picker>
          </view>
        </PcFormField>
        <PcFormField label="答案">
          <input class="pc-input" type="text" v-model="sq.answer" placeholder="输入新答案" />
        </PcFormField>
      </view>

      <PcPrimaryButton text="更新密保" :loading="saving" @press="handleSave" />
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcFormField from '@/components/PcFormField.vue'
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'
import { getMySecurityQuestions, updateSecurityQuestions } from '@/api/user'
import { getPresetSecurityQuestions } from '@/api/user'
import type { MySecurityQuestion } from '@/api/user'

interface SecurityQuestionForm {
  questionIndex: number | null
  questionText: string
  answer: string
}

interface QuestionPickerOption {
  label: string
  value: number | null
  disabled: boolean
}

const currentQuestions = ref<MySecurityQuestion[]>([])
const presetQuestions = ref<{ index: number; text: string }[]>([])
const saving = ref(false)

const form = ref<SecurityQuestionForm[]>([
  { questionIndex: null, questionText: '', answer: '' },
  { questionIndex: null, questionText: '', answer: '' },
])

onMounted(async () => {
  const [myRes, presetRes] = await Promise.all([
    getMySecurityQuestions(),
    getPresetSecurityQuestions(),
  ])
  if (myRes.success && myRes.data) {
    currentQuestions.value = myRes.data
  }
  if (presetRes.success && presetRes.data) {
    presetQuestions.value = presetRes.data.map((text, index) => ({ index, text }))
  }
})

function isQuestionUsed(qIndex: number, currentRow: number): boolean {
  return form.value.some((sq, i) => i !== currentRow && sq.questionIndex === qIndex)
}

function questionPickerOptions(rowIndex: number): QuestionPickerOption[] {
  return [
    { label: '请选择安全问题', value: null, disabled: false },
    ...presetQuestions.value.map((q) => {
      const disabled = isQuestionUsed(q.index, rowIndex)
      return {
        label: `${q.text}${disabled ? '（已选）' : ''}`,
        value: q.index,
        disabled,
      }
    }),
  ]
}

function questionPickerIndex(rowIndex: number): number {
  const selected = form.value[rowIndex]?.questionIndex
  if (selected === null || selected === undefined) return 0
  const index = questionPickerOptions(rowIndex).findIndex((o) => o.value === selected)
  return index >= 0 ? index : 0
}

function questionPickerLabel(rowIndex: number): string {
  return questionPickerOptions(rowIndex)[questionPickerIndex(rowIndex)]?.label ?? '请选择安全问题'
}

function onQuestionPickerChange(e: any, rowIndex: number) {
  const idx = Number(e?.detail?.value)
  if (!Number.isFinite(idx)) return
  const option = questionPickerOptions(rowIndex)[idx]
  if (!option || option.value === null) {
    form.value[rowIndex].questionIndex = null
    form.value[rowIndex].questionText = ''
    return
  }
  if (option.disabled) {
    uni.showToast({ title: '该问题已选择，不能重复', icon: 'none' })
    return
  }
  const found = presetQuestions.value.find(q => q.index === option.value)
  if (!found) return
  form.value[rowIndex].questionIndex = found.index
  form.value[rowIndex].questionText = found.text
}

async function handleSave() {
  const filled = form.value.filter(sq => sq.questionIndex !== null && sq.answer.trim())
  if (filled.length < 2) {
    uni.showToast({ title: '请设置 2 个密保问题并填写答案', icon: 'none' })
    return
  }
  const answers = filled.map(sq => sq.answer.trim().toLowerCase())
  if (new Set(answers).size !== answers.length) {
    uni.showToast({ title: '不同问题的答案不能相同', icon: 'none' })
    return
  }

  saving.value = true
  const res = await updateSecurityQuestions({
    securityQuestions: filled.map(sq => ({
      questionIndex: sq.questionIndex!,
      answer: sq.answer,
    })),
  })
  saving.value = false

  if (res.success) {
    uni.showToast({ title: '密保更新成功', icon: 'success' })
    // 刷新当前密保展示
    const myRes = await getMySecurityQuestions()
    if (myRes.success && myRes.data) currentQuestions.value = myRes.data
    // 清空表单
    form.value = [
      { questionIndex: null, questionText: '', answer: '' },
      { questionIndex: null, questionText: '', answer: '' },
    ]
    setTimeout(() => uni.navigateBack(), 800)
  }
}
</script>

<style scoped>
.sq-page {
  padding: 40rpx;
}

.sq-card {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
  margin-top: 48rpx;
  padding: 48rpx 40rpx;
  border-radius: 48rpx;
  background: var(--pc-user-surface);
  box-shadow: 0 12px 32px rgba(25, 50, 46, 0.09);
}

.sq-brand {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.sq-brand__title {
  font-size: 36rpx;
  font-weight: 800;
  color: var(--pc-user-dark);
}

.sq-brand__subtitle {
  font-size: 24rpx;
  color: var(--pc-user-muted);
  line-height: 1.5;
}

.sq-current {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  background: var(--pc-user-cream);
  border-radius: 32rpx;
  padding: 28rpx;
}

.sq-current__title {
  font-size: 26rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
}

.sq-current__item {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.sq-current__index {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: var(--pc-user-primary);
  color: var(--pc-user-surface);
  font-size: 22rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sq-current__question {
  font-size: 26rpx;
  color: var(--pc-user-ink);
}

.sq-divider {
  height: 2rpx;
  background: var(--pc-user-line);
  margin: 8rpx 0;
}

.sq-section-title {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
}

.sq-item {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  background: var(--pc-user-cream);
  border-radius: 32rpx;
  padding: 24rpx;
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

.pc-select-wrap {
  position: relative;
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
