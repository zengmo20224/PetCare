<template>
  <view class="pc-page wallet-tx-page">
    <PcPageHeader title="余额明细" />

    <PcStatePanel :status="pageStatus" :empty-text="'暂无交易记录'">
      <template v-if="transactions.length > 0">
        <view class="tx-list">
          <view v-for="tx in transactions" :key="tx.id" class="tx-item">
            <view class="tx-item__left">
              <text class="tx-item__title">{{ sourceLabel(tx.sourceType) }}</text>
              <text class="tx-item__time">{{ formatTime(tx.createTime) }}</text>
              <text v-if="tx.reason" class="tx-item__reason">{{ tx.reason }}</text>
            </view>
            <view class="tx-item__right">
              <text
                class="tx-item__amount"
                :class="{ 'tx-item__amount--in': tx.direction === 'CREDIT' }"
              >
                {{ tx.direction === 'CREDIT' ? '+' : '-' }}¥{{ tx.amount.toFixed(2) }}
              </text>
              <text class="tx-item__after">余额 ¥{{ tx.balanceAfter.toFixed(2) }}</text>
            </view>
          </view>
        </view>

        <view v-if="!hasMore && transactions.length > 0" class="tx-end">
          <text>没有更多了</text>
        </view>
        <view v-else-if="loadingMore" class="tx-end">
          <text>加载中...</text>
        </view>
      </template>
    </PcStatePanel>
  </view>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { onReachBottom } from '@dcloudio/uni-app'
import PcPageHeader from '@/components/PcPageHeader.vue'
import PcStatePanel from '@/components/PcStatePanel.vue'
import { getMyWalletTransactions } from '@/api/wallet'
import { WALLET_SOURCE_LABELS, type WalletTransaction } from '@/types/wallet'

const pageStatus = ref<'loading' | 'empty' | 'success' | 'error'>('loading')
const transactions = ref<WalletTransaction[]>([])
const page = ref(1)
const pageSize = 20
const hasMore = ref(true)
const loadingMore = ref(false)

function sourceLabel(sourceType: string): string {
  return WALLET_SOURCE_LABELS[sourceType] ?? sourceType
}

function formatTime(iso: string): string {
  if (!iso) return ''
  return iso.replace('T', ' ').slice(0, 16)
}

async function loadFirst() {
  pageStatus.value = 'loading'
  page.value = 1
  hasMore.value = true
  const res = await getMyWalletTransactions({ page: page.value, size: pageSize })
  if (!res.success || !res.data) {
    pageStatus.value = 'error'
    return
  }
  transactions.value = res.data.items
  hasMore.value = transactions.value.length < res.data.total
  pageStatus.value = transactions.value.length > 0 ? 'success' : 'empty'
}

async function loadMore() {
  if (!hasMore.value || loadingMore.value) return
  loadingMore.value = true
  page.value += 1
  const res = await getMyWalletTransactions({ page: page.value, size: pageSize })
  loadingMore.value = false
  if (res.success && res.data) {
    transactions.value.push(...res.data.items)
    hasMore.value = transactions.value.length < res.data.total
  }
}

onMounted(loadFirst)
onReachBottom(loadMore)
</script>

<style scoped>
.wallet-tx-page {
  padding: 20px;
}

.tx-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.tx-item {
  background: #fff;
  border-radius: 10px;
  padding: 14px 16px;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}

.tx-item__left {
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex: 1;
  min-width: 0;
}

.tx-item__title {
  font-size: 14px;
  font-weight: 600;
  color: #19322E;
}

.tx-item__time {
  font-size: 11px;
  color: #71817D;
}

.tx-item__reason {
  font-size: 11px;
  color: #71817D;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tx-item__right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
}

.tx-item__amount {
  font-size: 16px;
  font-weight: 700;
  color: #19322E;
}

.tx-item__amount--in {
  color: #11796F;
}

.tx-item__after {
  font-size: 11px;
  color: #71817D;
}

.tx-end {
  text-align: center;
  padding: 20px;
  color: #71817D;
  font-size: 12px;
}
</style>
