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
  padding: 40rpx;
}

.wallet-card {
  background: linear-gradient(135deg, var(--pc-user-primary) 0%, #0E6B61 100%);
  border-radius: 32rpx;
  padding: 48rpx 40rpx;
  color: var(--pc-user-surface);
  margin-bottom: 40rpx;
}

.wallet-card__label {
  font-size: 24rpx;
  opacity: 0.85;
  display: block;
}

.wallet-card__balance {
  font-size: 64rpx;
  font-weight: 700;
  margin: 16rpx 0 32rpx;
  display: block;
}

.wallet-card__actions {
  display: flex;
  gap: 20rpx;
}

.wallet-card__btn {
  flex: 1;
  height: 76rpx;
  border-radius: 38rpx;
  background: var(--pc-user-accent);
  display: flex;
  align-items: center;
  justify-content: center;
}

.wallet-card__btn--ghost {
  background: transparent;
  border: 1px solid rgba(255, 255, 255, 0.5);
}

.wallet-card__btn text {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--pc-user-surface);
}

.wallet-card__btn--ghost text {
  color: var(--pc-user-surface);
}

.wallet-section {
  margin-bottom: 40rpx;
}

.wallet-section__head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.wallet-section__title {
  font-size: 28rpx;
  font-weight: 600;
  color: var(--pc-user-ink);
}

.wallet-section__more {
  font-size: 24rpx;
  color: var(--pc-user-muted);
}

.wallet-empty {
  background: var(--pc-user-surface);
  border-radius: 20rpx;
  padding: 60rpx;
  text-align: center;
  color: var(--pc-user-muted);
  font-size: 26rpx;
}

.wallet-tx-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.wallet-tx {
  background: var(--pc-user-surface);
  border-radius: 20rpx;
  padding: 24rpx 32rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.wallet-tx__left {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.wallet-tx__title {
  font-size: 28rpx;
  color: var(--pc-user-ink);
  font-weight: 600;
}

.wallet-tx__time {
  font-size: 22rpx;
  color: var(--pc-user-muted);
}

.wallet-tx__amount {
  font-size: 30rpx;
  font-weight: 700;
  color: var(--pc-user-ink);
}

.wallet-tx__amount--in {
  color: var(--pc-user-primary);
}

.wallet-hint {
  margin-top: 48rpx;
  padding: 24rpx;
  background: var(--pc-user-cream);
  border-radius: 16rpx;
}

.wallet-hint__text {
  font-size: 22rpx;
  color: var(--pc-user-muted);
  line-height: 1.6;
}
</style>
