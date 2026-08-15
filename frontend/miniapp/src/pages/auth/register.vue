<template>
  <view class="pc-page auth-page">
    <PcPageHeader title="注册" />

    <view class="auth-card">
      <view class="auth-brand">
        <text class="auth-brand__title">创建账号 🐾</text>
        <text class="auth-brand__subtitle">加入萌宠家园，开启科学养宠之旅</text>
      </view>

      <wd-cell-group border class="auth-form">
        <wd-input
          v-model="form.phone"
          label="手机号"
          label-width="80px"
          placeholder="请输入手机号"
          type="number"
          :maxlength="11"
          clearable
        />
        <wd-input
          v-model="form.password"
          label="密码"
          label-width="80px"
          placeholder="8-32位，含数字和字母"
          show-password
          clearable
        />
        <wd-input
          v-model="form.nickname"
          label="昵称"
          label-width="80px"
          placeholder="给自己起个昵称"
          clearable
        />
      </wd-cell-group>

      <!-- Security Questions -->
      <text class="auth-section-title">安全问题（用于找回密码，请至少选择 2 个）</text>
      <view v-for="(sq, index) in form.securityQuestions" :key="index" class="auth-sq-item">
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
                <text class="pc-select__arrow">?</text>
              </view>
            </picker>
          </view>
        </PcFormField>
        <PcFormField label="答案">
          <input class="pc-input" type="text" v-model="sq.answer" placeholder="输入答案" />
        </PcFormField>
      </view>

      <PcPrimaryButton text="注册" :loading="loading" @press="handleRegister" />
      <view class="auth-back">
        <wd-button type="text" size="small" @click="goLogin">已有账号？去登录</wd-button>
      </view>
    </view>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcFormField from '@/components/PcFormField.vue'
import PcPrimaryButton from '@/components/PcPrimaryButton.vue'
import { register, getPresetSecurityQuestions } from '@/api/user'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const loading = ref(false)
const presetQuestions = ref<{ index: number; text: string }[]>([])

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

const form = ref({
  phone: '',
  password: '',
  nickname: '',
  securityQuestions: [
    { questionIndex: null, questionText: '', answer: '' } as SecurityQuestionForm,
    { questionIndex: null, questionText: '', answer: '' } as SecurityQuestionForm,
  ],
})

onMounted(async () => {
  const res = await getPresetSecurityQuestions()
  if (res.success && res.data) {
    presetQuestions.value = res.data.map((text, index) => ({ index, text }))
  }
})

function isQuestionUsed(qIndex: number, currentRow: number): boolean {
  return form.value.securityQuestions.some(
    (sq, i) => i !== currentRow && sq.questionIndex === qIndex
  )
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
  const selected = form.value.securityQuestions[rowIndex]?.questionIndex
  if (selected === null || selected === undefined) {
    return 0
  }
  const index = questionPickerOptions(rowIndex).findIndex((option) => option.value === selected)
  return index >= 0 ? index : 0
}

function questionPickerLabel(rowIndex: number): string {
  return questionPickerOptions(rowIndex)[questionPickerIndex(rowIndex)]?.label ?? '请选择安全问题'
}

function getPickerEventIndex(e: any): number {
  const index = Number(e?.detail?.value)
  return Number.isFinite(index) ? index : 0
}

function clearQuestion(rowIndex: number) {
  form.value.securityQuestions[rowIndex].questionIndex = null
  form.value.securityQuestions[rowIndex].questionText = ''
}

function onQuestionPickerChange(e: any, rowIndex: number) {
  const option = questionPickerOptions(rowIndex)[getPickerEventIndex(e)]
  if (!option || option.value === null) {
    clearQuestion(rowIndex)
    return
  }
  if (option.disabled) {
    uni.showToast({ title: '该问题已选择，不能重复', icon: 'none' })
    return
  }
  const found = presetQuestions.value.find(q => q.index === option.value)
  if (!found) return
  form.value.securityQuestions[rowIndex].questionIndex = found.index
  form.value.securityQuestions[rowIndex].questionText = found.text
}

async function handleRegister() {
  if (!form.value.phone || !form.value.password || !form.value.nickname) {
    uni.showToast({ title: '请填写完整信息', icon: 'none' })
    return
  }
  if (form.value.password.length < 8) {
    uni.showToast({ title: '密码至少 8 位', icon: 'none' })
    return
  }
  if (form.value.password.length > 32) {
    uni.showToast({ title: '密码不能超过 32 位', icon: 'none' })
    return
  }
  if (!/(?=.*[A-Za-z])(?=.*\d)/.test(form.value.password)) {
    uni.showToast({ title: '密码必须包含数字和字母', icon: 'none' })
    return
  }

  const validQuestions = form.value.securityQuestions.filter(sq => sq.questionIndex !== null)
  if (validQuestions.length < 2) {
    uni.showToast({ title: '请至少选择 2 个安全问题', icon: 'none' })
    return
  }
  for (const sq of validQuestions) {
    if (!sq.answer.trim()) {
      uni.showToast({ title: '请填写所有安全问题的答案', icon: 'none' })
      return
    }
  }
  const answers = validQuestions.map(sq => sq.answer.trim().toLowerCase())
  if (new Set(answers).size !== answers.length) {
    uni.showToast({ title: '不同问题的答案不能相同', icon: 'none' })
    return
  }

  loading.value = true
  const res = await register({
    phone: form.value.phone,
    password: form.value.password,
    nickname: form.value.nickname,
    securityQuestions: validQuestions.map(sq => ({
      questionIndex: sq.questionIndex!,
      answer: sq.answer,
    })),
  })
  loading.value = false

  if (res.success && res.data) {
    userStore.setAuthToken(res.data.accessToken)
    uni.showToast({ title: '注册成功', icon: 'success' })
    await userStore.fetchProfile()
    uni.switchTab({ url: '/pages/profile/index' })
  }
}

function goLogin() {
  uni.navigateBack()
}
</script>

<style scoped>
.auth-page {
  padding: 40rpx;
}

.auth-card {
  display: flex;
  flex-direction: column;
  gap: 32rpx;
  margin-top: 48rpx;
  padding: 48rpx 40rpx;
  border-radius: 48rpx;
  background: var(--pc-user-surface);
  box-shadow: 0 12px 32px rgba(25, 50, 46, 0.09);
}

.auth-brand {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.auth-brand__title {
  font-size: 44rpx;
  font-weight: 800;
  color: var(--pc-user-dark);
}

.auth-brand__subtitle {
  font-size: 26rpx;
  color: var(--pc-user-muted);
}

.auth-form {
  border-radius: 32rpx;
  overflow: hidden;
}

.auth-section-title {
  font-size: 28rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
  margin-top: 8rpx;
}

.auth-sq-item {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  background: var(--pc-user-cream);
  border-radius: 32rpx;
  padding: 24rpx;
}

.auth-back {
  display: flex;
  justify-content: center;
  padding-top: 8rpx;
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

.pc-field-hint {
  font-size: 22rpx;
  color: var(--pc-user-muted);
  margin-top: 8rpx;
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
