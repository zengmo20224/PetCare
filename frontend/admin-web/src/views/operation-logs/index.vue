<template>
  <div class="pc-operation-logs">
    <h2 class="pc-operation-logs__title">操作日志</h2>
    <p class="pc-operation-logs__intro">这里展示后台人员做过的管理动作，系统字段已转换为中文业务说明。</p>

    <FilterBar @search="fetchData" @reset="handleReset">
      <el-form-item label="业务模块">
        <el-select v-model="queryParams.module" placeholder="全部模块" clearable style="width: 180px">
          <el-option
            v-for="option in moduleOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
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
      <el-table-column prop="createTime" label="发生时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column prop="adminId" label="操作人" width="170">
        <template #default="{ row }">
          {{ formatAdminLabel(row.adminId) }}
        </template>
      </el-table-column>
      <el-table-column label="操作内容" min-width="300">
        <template #default="{ row }">
          <div class="pc-operation-logs__summary">
            <div class="pc-operation-logs__summary-main">
              <el-tag size="small" type="info">{{ formatModuleLabel(row.module) }}</el-tag>
              <strong>{{ formatOperationSummary(row) }}</strong>
            </div>
            <div class="pc-operation-logs__summary-meta">{{ formatTargetHint(row) }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column prop="result" label="处理结果" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="resultTagType(row.result)">{{ formatResultLabel(row.result) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="errorMessage" label="说明" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ formatReadableMessage(row) }}
        </template>
      </el-table-column>
      <el-table-column label="技术信息" width="130">
        <template #default="{ row }">
          <el-popover placement="left" width="360" trigger="click">
            <template #reference>
              <el-button link type="primary">查看技术信息</el-button>
            </template>
            <el-descriptions :column="1" size="small" border>
              <el-descriptions-item label="系统记录编号">{{ row.id }}</el-descriptions-item>
              <el-descriptions-item label="原始模块">{{ row.module || '-' }}</el-descriptions-item>
              <el-descriptions-item label="原始操作">{{ row.operation || '-' }}</el-descriptions-item>
              <el-descriptions-item label="请求动作">
                {{ formatMethodLabel(row.requestMethod) }}（{{ row.requestMethod || '-' }}）
              </el-descriptions-item>
              <el-descriptions-item label="原始地址">{{ row.requestUrl || '-' }}</el-descriptions-item>
            </el-descriptions>
          </el-popover>
        </template>
      </el-table-column>
    </DataTableShell>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getOperationLogs } from '../../api/operation-log'
import type { OperationLog } from '../../api/operation-log'
import FilterBar from '../../components/FilterBar.vue'
import DataTableShell from '../../components/DataTableShell.vue'

const loading = ref(false)
const tableData = ref<OperationLog[]>([])
const total = ref(0)
const queryParams = reactive({ page: 1, size: 20, module: '' })

const MODULE_LABELS: Record<string, string> = {
  store: '门店管理',
  service: '服务项目',
  product: '商品管理',
  product_order: '商品订单',
  booking: '预约管理',
  staff: '员工管理',
  user: '用户管理',
  community: '社区管理',
  moderation: '内容审核',
  admin: '后台权限',
  system: '系统设置',
  wallet: '钱包管理',
  AI分析: 'AI分析'
}

const OPERATION_LABELS: Record<string, string> = {
  'update-info': '更新门店信息',
  'update-config': '更新门店配置',
  'create-item': '新增项目或商品',
  'update-item': '编辑项目或商品',
  'disable-item': '下架或停用项目',
  'update-stock': '调整商品库存',
  'replace-carousel-images': '更新商品轮播图',
  'create-profile': '新增员工资料',
  'update-profile': '编辑员工资料',
  'disable-profile': '停用员工账号',
  'replace-skills': '更新员工服务技能',
  'create-schedule': '新增员工排班',
  'update-schedule': '编辑员工排班',
  'ban-user': '封禁用户',
  'unban-user': '解除用户封禁',
  'confirm-booking': '确认预约',
  'reject-booking': '拒绝预约',
  'start-booking': '开始服务',
  'complete-booking': '完成服务',
  'cancel-booking': '取消预约',
  'reassign-booking': '调整服务人员',
  confirm: '确认商品订单',
  ready: '标记商品已备好',
  'confirm-payment': '确认订单支付',
  complete: '完成商品订单',
  cancel: '取消商品订单',
  'out-of-stock': '标记商品缺货',
  'wallet-recharge': '钱包充值',
  'wallet-adjust': '钱包余额调整',
  生成报告: '生成AI分析报告'
}

const RESULT_LABELS = {
  success: '操作成功',
  fail: '操作失败',
  unknown: '状态未记录'
} as const

const METHOD_LABELS: Record<string, string> = {
  GET: '查看信息',
  POST: '提交处理',
  PUT: '保存修改',
  PATCH: '局部修改',
  DELETE: '删除内容'
}

const moduleOptions = Object.entries(MODULE_LABELS).map(([value, label]) => ({ value, label }))

const formatModuleLabel = (module: string) => MODULE_LABELS[module] || '其他模块'

const formatOperationLabel = (operation: string) => OPERATION_LABELS[operation] || '系统操作'

const normalizeResult = (result: string) => {
  const normalized = (result || '').toUpperCase()
  if (normalized === 'SUCCESS') return 'success'
  if (normalized === 'FAIL' || normalized === 'FAILED' || normalized === 'ERROR') return 'fail'
  return 'unknown'
}

const formatResultLabel = (result: string) => RESULT_LABELS[normalizeResult(result)]

const resultTagType = (result: string) => {
  const normalized = normalizeResult(result)
  if (normalized === 'success') return 'success'
  if (normalized === 'fail') return 'danger'
  return 'info'
}

const formatMethodLabel = (method: string) => METHOD_LABELS[(method || '').toUpperCase()] || '系统处理'

const formatOperationSummary = (row: OperationLog) => formatOperationLabel(row.operation)

/**
 * Parses the operation target hint from requestParams (preferred) or requestUrl (fallback).
 *
 * Backend writes params as a flat "key=value, key2=value2" string. The most human-readable
 * key is `targetName` (e.g. staff/user/product name); secondary keys like `phone`,
 * `orderNo`, `bookingNo` add context. If params is empty, we fall back to extracting a
 * numeric ID from the URL so legacy log rows still show something useful.
 */
const formatTargetHint = (row: OperationLog) => {
  const parts = parseParams(row.requestParams)
  if (parts.size > 0) {
    const segments: string[] = []
    // Prefer the explicit human-readable name fields, in priority order.
    const nameKeys = ['targetName', 'staffName', 'userNickname', 'productName', 'serviceName', 'storeName']
    const nameKey = nameKeys.find((k) => parts.has(k))
    if (nameKey) segments.push(parts.get(nameKey)!)
    // Add secondary identifiers for context (phone / business numbers).
    const contextKeys = ['phone', 'orderNo', 'bookingNo', 'targetId', 'staffId', 'userId', 'productId', 'bookingId']
    for (const k of contextKeys) {
      if (parts.has(k) && k !== nameKey) {
        segments.push(`${k}=${parts.get(k)}`)
      }
    }
    if (segments.length > 0) return segments.join(' · ')
  }
  // Fallback: extract numeric ID from URL (legacy rows without params).
  const urlSegments = (row.requestUrl || '').split('/').filter(Boolean)
  const targetId = [...urlSegments].reverse().find((segment) => /^\d{6,}$/.test(segment))
  return targetId ? `关联对象编号：${targetId}` : '未记录具体对象编号'
}

/** Parses a "k1=v1, k2=v2" string into a Map. Tolerant of whitespace and missing values. */
const parseParams = (raw: string | null): Map<string, string> => {
  const result = new Map<string, string>()
  if (!raw) return result
  for (const chunk of raw.split(',')) {
    const idx = chunk.indexOf('=')
    if (idx <= 0) continue
    const key = chunk.slice(0, idx).trim()
    const value = chunk.slice(idx + 1).trim()
    if (key && value) result.set(key, value)
  }
  return result
}

const formatAdminLabel = (adminId: OperationLog['adminId']) => {
  if (adminId === null || adminId === undefined || adminId === '') return '未记录操作人'
  return `管理员编号：${adminId}`
}

const formatDateTime = (value: string) => {
  if (!value) return '未记录时间'
  return value.replace('T', ' ').slice(0, 19)
}

const formatReadableMessage = (row: OperationLog) => {
  if (normalizeResult(row.result) === 'success') return '已完成'
  return row.errorMessage || '操作未完成，系统未记录具体原因'
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getOperationLogs(queryParams)
    if (res.data) { tableData.value = res.data.items; total.value = res.data.total }
  } catch { /* handled */ } finally { loading.value = false }
}

const handlePageChange = (page: number, size: number) => {
  queryParams.page = page
  queryParams.size = size
  fetchData()
}

const handleReset = () => {
  queryParams.module = ''
  queryParams.page = 1
  fetchData()
}

onMounted(() => { fetchData() })
</script>

<style scoped>
.pc-operation-logs {
  padding: 0;
}

.pc-operation-logs__title {
  margin: 0 0 var(--pc-spacing-lg) 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--pc-ink);
}

.pc-operation-logs__intro {
  margin: calc(-1 * var(--pc-spacing-sm)) 0 var(--pc-spacing-lg);
  color: var(--pc-text-muted);
  font-size: 14px;
}

.pc-operation-logs__summary {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 0;
}

.pc-operation-logs__summary-main {
  display: flex;
  align-items: center;
  gap: var(--pc-spacing-sm);
}

.pc-operation-logs__summary-main strong {
  color: var(--pc-ink);
  font-weight: 600;
}

.pc-operation-logs__summary-meta {
  color: var(--pc-text-muted);
  font-size: 12px;
}
</style>
