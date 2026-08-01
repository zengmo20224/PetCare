<template>
  <div class="pc-wallet-accounts">
    <h2 class="pc-wallet-accounts__title">用户钱包</h2>
    <p class="pc-wallet-accounts__intro">
      管理端手工台账：为用户充值或调整余额。所有操作强制留痕（成功/失败均记录到操作日志）。
    </p>

    <FilterBar @search="fetchData" @reset="handleReset">
      <el-form-item label="手机号">
        <el-input
          v-model="queryParams.phone"
          placeholder="按手机号模糊搜索"
          clearable
          style="width: 200px"
          @keyup.enter="fetchData"
        />
      </el-form-item>
      <el-form-item label="用户编号">
        <el-input
          v-model="queryParams.userId"
          placeholder="按用户编号模糊搜索"
          clearable
          style="width: 220px"
          @keyup.enter="fetchData"
        />
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
      <el-table-column label="用户" min-width="180">
        <template #default="{ row }">
          <div class="pc-wallet-accounts__user">
            <strong>{{ row.userNickname || '（未设置昵称）' }}</strong>
            <span class="pc-wallet-accounts__phone">{{ row.userPhone || '无手机号' }}</span>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="用户编号" width="170">
        <template #default="{ row }">{{ row.userId }}</template>
      </el-table-column>
      <el-table-column label="余额" width="140">
        <template #default="{ row }">
          <span class="pc-wallet-accounts__balance">¥{{ Number(row.balance).toFixed(2) }}</span>
          <el-tag v-if="!row.walletId" size="small" type="info" class="pc-wallet-accounts__tag">未开通</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="冻结" width="100">
        <template #default="{ row }">¥{{ Number(row.frozenAmount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="updateTime" label="最后更新" width="170">
        <template #default="{ row }">{{ row.updateTime ? formatDateTime(row.updateTime) : '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button
            size="small"
            type="primary"
            @click="openRecharge(row)"
            :disabled="!userStore.hasPermission('wallet:account:recharge')"
          >
            充值
          </el-button>
          <el-button
            size="small"
            type="warning"
            @click="openAdjust(row)"
            :disabled="!userStore.hasPermission('wallet:account:adjust')"
          >
            调整
          </el-button>
        </template>
      </el-table-column>
    </DataTableShell>

    <!-- Recharge / Adjust Dialog -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="500px" @close="resetForm">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="90px">
        <el-form-item label="目标用户">
          <span>{{ targetUserLabel }}</span>
        </el-form-item>
        <el-form-item label="当前余额">
          <span class="pc-wallet-accounts__current">¥{{ currentBalanceText }}</span>
        </el-form-item>
        <el-form-item v-if="dialogMode === 'adjust'" label="调整方向" prop="direction">
          <el-radio-group v-model="form.direction">
            <el-radio value="CREDIT">增加（+）</el-radio>
            <el-radio value="DEBIT">扣减（-）</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="金额（元）" prop="amount">
          <el-input-number
            v-model="form.amount"
            :min="0.01"
            :precision="2"
            :step="10"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="操作理由" prop="reason">
          <el-input
            v-model="form.reason"
            type="textarea"
            :rows="3"
            placeholder="必填：说明本次充值/调整的原因（如：客户线下现金充值、纠错多扣等）"
            maxlength="500"
            show-word-limit
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitLoading">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import {
  getWalletAccounts,
  rechargeWallet,
  adjustWallet,
  type WalletAccount,
  type WalletAccountQueryParams,
} from '../../../api/wallet'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '../../../store/user'
import { showSuccess } from '../../../utils/feedback'
import FilterBar from '../../../components/FilterBar.vue'
import DataTableShell from '../../../components/DataTableShell.vue'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<WalletAccount[]>([])
const total = ref(0)
const queryParams = reactive<WalletAccountQueryParams>({ page: 1, size: 20, phone: '', userId: '' })

// ─── Dialog state ───
const dialogVisible = ref(false)
const dialogMode = ref<'recharge' | 'adjust'>('recharge')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const targetAccount = ref<WalletAccount | null>(null)

interface WalletForm {
  amount: number
  direction: 'CREDIT' | 'DEBIT'
  reason: string
}
const form = ref<WalletForm>({ amount: 10, direction: 'CREDIT', reason: '' })

const rules: FormRules = {
  amount: [
    { required: true, message: '请输入金额', trigger: 'blur' },
    {
      validator: (_rule, value, callback) => {
        if (value == null || value <= 0) callback(new Error('金额必须大于 0'))
        else callback()
      },
      trigger: 'blur',
    },
  ],
  direction: [{ required: true, message: '请选择调整方向', trigger: 'change' }],
  reason: [
    { required: true, message: '请填写操作理由', trigger: 'blur' },
    { max: 500, message: '理由不能超过 500 字符', trigger: 'blur' },
  ],
}

const dialogTitle = computed(() =>
  dialogMode.value === 'recharge' ? '钱包充值' : '钱包余额调整'
)
const targetUserLabel = computed(() => {
  const a = targetAccount.value
  if (!a) return '-'
  return `${a.userNickname || '（未设置昵称）'} · ${a.userPhone || '无手机号'} · ${a.userId}`
})
const currentBalanceText = computed(() =>
  targetAccount.value ? Number(targetAccount.value.balance).toFixed(2) : '0.00'
)

function formatDateTime(value: string) {
  if (!value) return '-'
  return value.replace('T', ' ').slice(0, 19)
}

async function fetchData() {
  loading.value = true
  try {
    // Strip empty-string filters so the backend treats them as "no filter".
    const params: WalletAccountQueryParams = { page: queryParams.page, size: queryParams.size }
    if (queryParams.phone) params.phone = queryParams.phone
    if (queryParams.userId) params.userId = queryParams.userId
    const res = await getWalletAccounts(params)
    if (res.data) {
      tableData.value = res.data.items
      total.value = res.data.total
    }
  } catch {
    /* handled by request interceptor */
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
  queryParams.phone = ''
  queryParams.userId = ''
  queryParams.page = 1
  fetchData()
}

function openRecharge(row: WalletAccount) {
  dialogMode.value = 'recharge'
  targetAccount.value = row
  form.value = { amount: 10, direction: 'CREDIT', reason: '' }
  dialogVisible.value = true
}

function openAdjust(row: WalletAccount) {
  dialogMode.value = 'adjust'
  targetAccount.value = row
  form.value = { amount: 10, direction: 'CREDIT', reason: '' }
  dialogVisible.value = true
}

function resetForm() {
  form.value = { amount: 10, direction: 'CREDIT', reason: '' }
  formRef.value?.clearValidate()
}

async function submitForm() {
  if (!formRef.value || !targetAccount.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      const userId = targetAccount.value!.userId
      if (dialogMode.value === 'recharge') {
        await rechargeWallet(userId, {
          amount: form.value.amount,
          reason: form.value.reason,
        })
        showSuccess('充值成功')
      } else {
        await adjustWallet(userId, {
          amount: form.value.amount,
          direction: form.value.direction,
          reason: form.value.reason,
        })
        showSuccess('调整成功')
      }
      dialogVisible.value = false
      fetchData()
    } catch {
      /* handled */
    } finally {
      submitLoading.value = false
    }
  })
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.pc-wallet-accounts {
  padding: 0;
}

.pc-wallet-accounts__title {
  margin: 0 0 var(--pc-spacing-lg) 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--pc-ink);
}

.pc-wallet-accounts__intro {
  margin: calc(-1 * var(--pc-spacing-sm)) 0 var(--pc-spacing-lg);
  color: var(--pc-text-muted);
  font-size: 14px;
}

.pc-wallet-accounts__user {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.pc-wallet-accounts__phone {
  color: var(--pc-text-muted);
  font-size: 12px;
}

.pc-wallet-accounts__balance {
  font-weight: 700;
  color: var(--pc-primary);
}

.pc-wallet-accounts__tag {
  margin-left: 6px;
  vertical-align: middle;
}

.pc-wallet-accounts__current {
  font-weight: 700;
  color: var(--pc-primary);
  font-size: 16px;
}
</style>
