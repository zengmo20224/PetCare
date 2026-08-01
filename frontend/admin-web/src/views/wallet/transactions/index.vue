<template>
  <div class="pc-wallet-tx">
    <h2 class="pc-wallet-tx__title">钱包流水</h2>
    <p class="pc-wallet-tx__intro">
      所有钱包余额变更的不可变流水记录。每条含变更前后余额快照，便于对账。
    </p>

    <FilterBar @search="fetchData" @reset="handleReset">
      <el-form-item label="用户编号">
        <el-input
          v-model="queryParams.userId"
          placeholder="精确用户编号"
          clearable
          style="width: 180px"
          @keyup.enter="fetchData"
        />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="queryParams.sourceType" placeholder="全部类型" clearable style="width: 150px">
          <el-option
            v-for="(label, value) in WALLET_SOURCE_LABELS"
            :key="value"
            :label="label"
            :value="value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="方向">
        <el-select v-model="queryParams.direction" placeholder="全部" clearable style="width: 120px">
          <el-option label="收入" value="CREDIT" />
          <el-option label="支出" value="DEBIT" />
        </el-select>
      </el-form-item>
    </FilterBar>

    <DataTableShell
      :data="tableData"
      :total="total"
      :page="queryParams.page"
      :size="queryParams.size"
      :loading="loading"
      @page-change="handlePageChange"
    >
      <el-table-column prop="createTime" label="发生时间" width="170">
        <template #default="{ row }">{{ formatDateTime(row.createTime) }}</template>
      </el-table-column>
      <el-table-column label="用户" min-width="200">
        <template #default="{ row }">
          <div class="pc-wallet-tx__user">
            <strong>{{ row.userNickname || '（未设置昵称）' }}</strong>
            <span class="pc-wallet-tx__user-meta">{{ row.userPhone || '' }} · 编号 {{ row.userId }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="方向" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.direction === 'CREDIT' ? 'success' : 'danger'">
            {{ WALLET_DIRECTION_LABELS[row.direction] || row.direction }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          {{ WALLET_SOURCE_LABELS[row.sourceType] || row.sourceType }}
        </template>
      </el-table-column>
      <el-table-column label="金额" width="110">
        <template #default="{ row }">
          <span :class="row.direction === 'CREDIT' ? 'pc-wallet-tx__in' : 'pc-wallet-tx__out'">
            {{ row.direction === 'CREDIT' ? '+' : '-' }}¥{{ Number(row.amount).toFixed(2) }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="变更后余额" width="120">
        <template #default="{ row }">¥{{ Number(row.balanceAfter).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="关联订单" width="180">
        <template #default="{ row }">
          <span v-if="row.relatedOrderId">
            {{ orderTypeLabel(row.relatedOrderType) }} {{ row.relatedOrderId }}
          </span>
          <span v-else class="pc-wallet-tx__muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="操作方" width="110">
        <template #default="{ row }">
          {{ operatorLabel(row.operatorType, row.operatorId) }}
        </template>
      </el-table-column>
      <el-table-column prop="reason" label="理由" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.reason || '-' }}</template>
      </el-table-column>
    </DataTableShell>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import {
  getWalletTransactions,
  WALLET_SOURCE_LABELS,
  WALLET_DIRECTION_LABELS,
  type WalletTransaction,
  type WalletTransactionQueryParams,
} from '../../../api/wallet'
import FilterBar from '../../../components/FilterBar.vue'
import DataTableShell from '../../../components/DataTableShell.vue'

const loading = ref(false)
const tableData = ref<WalletTransaction[]>([])
const total = ref(0)
const queryParams = reactive<WalletTransactionQueryParams>({
  page: 1,
  size: 20,
  userId: '',
  sourceType: '',
  direction: '',
})

function formatDateTime(value: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

function orderTypeLabel(type: string | null): string {
  if (!type) return ''
  if (type === 'PRODUCT_ORDER') return '商品订单'
  if (type === 'SERVICE_BOOKING') return '服务预约'
  return type
}

function operatorLabel(operatorType: string, operatorId: string | null): string {
  const typeLabel =
    operatorType === 'ADMIN' ? '管理员' : operatorType === 'SYSTEM' ? '系统' : '用户'
  return operatorId ? `${typeLabel} ${operatorId}` : typeLabel
}

async function fetchData() {
  loading.value = true
  try {
    // Strip empty-string params so the backend treats them as "no filter".
    const params: WalletTransactionQueryParams = { page: queryParams.page, size: queryParams.size }
    if (queryParams.userId) params.userId = queryParams.userId
    if (queryParams.sourceType) params.sourceType = queryParams.sourceType
    if (queryParams.direction) params.direction = queryParams.direction
    const res = await getWalletTransactions(params)
    if (res.data) {
      tableData.value = res.data.items
      total.value = res.data.total
    }
  } catch {
    /* handled */
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number, size: number) {
  queryParams.page = page
  queryParams.size = size
  fetchData()
}

function handleReset() {
  queryParams.userId = ''
  queryParams.sourceType = ''
  queryParams.direction = ''
  queryParams.page = 1
  fetchData()
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.pc-wallet-tx {
  padding: 0;
}

.pc-wallet-tx__title {
  margin: 0 0 var(--pc-spacing-lg) 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--pc-ink);
}

.pc-wallet-tx__intro {
  margin: calc(-1 * var(--pc-spacing-sm)) 0 var(--pc-spacing-lg);
  color: var(--pc-text-muted);
  font-size: 14px;
}

.pc-wallet-tx__in {
  color: var(--pc-success, #67c23a);
  font-weight: 700;
}

.pc-wallet-tx__out {
  color: var(--pc-danger, #f56c6c);
  font-weight: 700;
}

.pc-wallet-tx__muted {
  color: var(--pc-text-muted);
}

.pc-wallet-tx__user {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pc-wallet-tx__user-meta {
  color: var(--pc-text-muted);
  font-size: 12px;
}
</style>
