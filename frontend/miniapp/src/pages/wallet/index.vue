<template>
  <view class="pc-page wallet-page">
    <PcPageHeader title="我的钱包" />

    <PcStatePanel :status="pageStatus" empty-text="钱包信息加载中">
      <template v-if="pageStatus === 'success'">
        <!-- Balance Card -->
        <view class="wallet-card">
          <text class="wallet-card__label">可用余额（元）</text>
          <text class="wallet-card__balance">¥{{ formattedBalance }}</text>
          <view class="wallet-card__actions">
            <view class="wallet-card__btn" @tap="hintRecharge">
              <text>充值</text>
            </view>
            <view class="wallet-card__btn wallet-card__btn--ghost" @tap="goTransactions">
              <text>余额明细</text>
            </view>
          </view>
        </view>

        <!-- Recent transactions -->
        <view class="wallet-section">
          <view class="wallet-section__head">
            <text class="wallet-section__title">最近明细</text>
            <text v-if="recentTransactions.length > 0" class="wallet-section__more" @tap="goTransactions">全部 ›</text>
          </view>
          <view v-if="recentTransactions.length === 0" class="wallet-empty">
            <text>暂无交易记录</text>
          </view>
          <view v-else class="wallet-tx-list">
            <view v-for="tx in recentTransactions" :key="tx.id" class="wallet-tx">
              <view class="wallet-tx__left">
                <text class="wallet-tx__title">{{ sourceLabel(tx.sourceType) }}</text>
                <text class="wallet-tx__time">{{ formatTime(tx.createTime) }}</text>
              </view>
              <text
                class="wallet-tx__amount"
                :class="{ 'wallet-tx__amount--in': tx.direction === 'CREDIT' }"
              >
                {{ tx.direction === 'CREDIT' ? '+' : '-' }}¥{{ tx.amount.toFixed(2) }}
              </text>
            </view>
          </view>
        </view>

        <!-- Recharge hint -->
        <view class="wallet-hint">
          <text class="wallet-hint__text">
            充值请联系门店工作人员操作。钱包余额为管理端手工台账，不计息、不可提现、不可转账。
          </text>
        </view>
      </template>
    </PcStatePanel>
  </view>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import { getMyWallet, getMyWalletTransactions } from '@/api/wallet'
import {
  WALLET_SOURCE_LABELS,
  type WalletInfo,
  type WalletTransaction,
} from '@/types/wallet'

const pageStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const wallet = ref<WalletInfo | null>(null)
const recentTransactions = ref<WalletTransaction[]>([])

const formattedBalance = computed(() =>
  wallet.value ? wallet.value.balance.toFixed(2) : '0.00'
)

function sourceLabel(sourceType: string): string {
  return WALLET_SOURCE_LABELS[sourceType] ?? sourceType
}

function formatTime(iso: string): string {
  if (!iso) return ''
  // Show YYYY-MM-DD HH:mm (truncate seconds/millis).
  return iso.replace('T', ' ').slice(0, 16)
}

async function loadWallet() {
  pageStatus.value = 'loading'
  const [walletRes, txRes] = await Promise.all([
    getMyWallet(),
    getMyWalletTransactions({ page: 1, size: 5 }),
  ])
  if (!walletRes.success || !walletRes.data) {
    pageStatus.value = 'error'
    return
  }
  wallet.value = walletRes.data
  recentTransactions.value = txRes.success && txRes.data ? txRes.data.items : []
  pageStatus.value = 'success'
}

function goTransactions() {
  uni.navigateTo({ url: '/pages/wallet/transactions' })
}

function hintRecharge() {
  uni.showModal({
    title: '充值说明',
    content: '钱包充值由门店工作人员在管理端操作。如有充值需求，请联系门店工作人员。',
    showCancel: false,
    confirmText: '知道了',
  })
}

onMounted(loadWallet)
// Reload balance when returning from transactions page or after a payment.
onShow(() => {
  if (pageStatus.value === 'success') loadWallet()
})
</script>

<style scoped>
.wallet-page {
  padding: 20px;
}

.wallet-card {
  background: linear-gradient(135deg, #11796F 0%, #0E6B61 100%);
  border-radius: 16px;
  padding: 24px 20px;
  color: #fff;
  margin-bottom: 20px;
}

.wallet-card__label {
  font-size: 12px;
  opacity: 0.85;
  display: block;
}

.wallet-card__balance {
  font-size: 32px;
  font-weight: 700;
  margin: 8px 0 16px;
  display: block;
}

.wallet-card__actions {
  display: flex;
  gap: 10px;
}

.wallet-card__btn {
  flex: 1;
  height: 38px;
  border-radius: 19px;
  background: #F5A623;
  display: flex;
  align-items: center;
  justify-content: center;
}

.wallet-card__btn--ghost {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.5);
}

.wallet-card__btn text {
  font-size: 14px;
  font-weight: 600;
  color: #fff;
}

.wallet-card__btn--ghost text {
  color: #fff;
}

.wallet-section {
  margin-bottom: 20px;
}

.wallet-section__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.wallet-section__title {
  font-size: 14px;
  font-weight: 600;
  color: #19322E;
}

.wallet-section__more {
  font-size: 12px;
  color: #71817D;
}

.wallet-empty {
  background: #fff;
  border-radius: 10px;
  padding: 30px;
  text-align: center;
  color: #71817D;
  font-size: 13px;
}

.wallet-tx-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.wallet-tx {
  background: #fff;
  border-radius: 10px;
  padding: 12px 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.wallet-tx__left {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.wallet-tx__title {
  font-size: 14px;
  color: #19322E;
  font-weight: 600;
}

.wallet-tx__time {
  font-size: 11px;
  color: #71817D;
}

.wallet-tx__amount {
  font-size: 15px;
  font-weight: 700;
  color: #19322E;
}

.wallet-tx__amount--in {
  color: #11796F;
}

.wallet-hint {
  margin-top: 24px;
  padding: 12px;
  background: #FAF8F3;
  border-radius: 8px;
}

.wallet-hint__text {
  font-size: 11px;
  color: #71817D;
  line-height: 1.6;
}
</style>
