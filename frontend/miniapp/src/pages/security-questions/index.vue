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

      <PcPrimaryButton text="更新密保" :loading="saving" @tap="handleSave" />
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
  padding: 20px;
}

.sq-card {
  display: flex;
  flex-direction: column;
  gap: 16px;
  margin-top: 24px;
  padding: 24px 20px;
  border-radius: 24px;
  background: #FFFFFF;
  box-shadow: 0 12px 32px rgba(25, 50, 46, 0.09);
}

.sq-brand {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.sq-brand__title {
  font-size: 18px;
  font-weight: 800;
  color: #0C4D48;
}

.sq-brand__subtitle {
  font-size: 12px;
  color: #71817D;
  line-height: 1.5;
}

.sq-current {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #FAF8F3;
  border-radius: 16px;
  padding: 14px;
}

.sq-current__title {
  font-size: 13px;
  font-weight: 700;
  color: #19322E;
}

.sq-current__item {
  display: flex;
  align-items: center;
  gap: 10px;
}

.sq-current__index {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #11796F;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.sq-current__question {
  font-size: 13px;
  color: #19322E;
}

.sq-divider {
  height: 1px;
  background: #E2E9E6;
  margin: 4px 0;
}

.sq-section-title {
  font-size: 14px;
  font-weight: 700;
  color: #19322E;
}

.sq-item {
  display: flex;
  flex-direction: column;
  gap: 8px;
  background: #FAF8F3;
  border-radius: 16px;
  padding: 12px;
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

.pc-select-wrap {
  position: relative;
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
</style>
