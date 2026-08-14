<template>
  <div class="pc-ai-usage">
    <div class="pc-ai-usage__header">
      <div>
        <h2 class="pc-ai-usage__title">AI 调用用量</h2>
        <p class="pc-ai-usage__intro">
          全部 AI 能力（客服对话 / 宠物陪伴 / 内容生成 / 经营分析 / 内容审核）的调用记录与 token 消耗。不包含 prompt 与响应原文。
        </p>
      </div>
    </div>

    <FilterBar @search="fetchData" @reset="handleReset">
      <el-form-item label="调用类型">
        <el-select v-model="queryParams.apiType" placeholder="全部类型" clearable style="width: 180px">
          <el-option
            v-for="option in apiTypeOptions"
            :key="option.value"
            :label="option.label"
            :value="option.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="结果">
        <el-select v-model="queryParams.success" placeholder="全部" clearable style="width: 140px">
          <el-option label="成功" :value="true" />
          <el-option label="失败" :value="false" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          style="width: 240px"
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
      <el-table-column label="调用类型" width="130">
        <template #default="{ row }">
          <el-tag size="small" :type="apiTypeTagType(row.apiType)">
            {{ formatApiType(row.apiType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="调用方" width="170">
        <template #default="{ row }">
          <span v-if="row.userId">用户 #{{ row.userId }}</span>
          <span v-else-if="row.adminId">管理员 #{{ row.adminId }}</span>
          <span v-else>系统</span>
        </template>
      </el-table-column>
      <el-table-column prop="modelName" label="模型" width="140">
        <template #default="{ row }">
          {{ row.modelName || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="Token 消耗" width="220">
        <template #default="{ row }">
          <span v-if="row.totalTokens != null">
            共 {{ row.totalTokens }}（输入 {{ row.promptTokens ?? '-' }} / 输出 {{ row.completionTokens ?? '-' }}）
          </span>
          <span v-else>-</span>
        </template>
      </el-table-column>
      <el-table-column label="结果" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.success ? 'success' : 'danger'">
            {{ row.success ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="失败原因" min-width="160" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.errorMessage || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="调用时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) }}
        </template>
      </el-table-column>
    </DataTableShell>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { listUsage } from '../../api/ai-usage'
import type { AiUsageLog, AiUsageQueryParams } from '../../api/ai-usage'
import FilterBar from '../../components/FilterBar.vue'
import DataTableShell from '../../components/DataTableShell.vue'

const loading = ref(false)
const tableData = ref<AiUsageLog[]>([])
const total = ref(0)
const dateRange = ref<[string, string] | null>(null)

const queryParams = reactive<AiUsageQueryParams>({
  page: 1,
  size: 20,
  apiType: '',
  success: '',
})

const API_TYPE_LABELS: Record<string, string> = {
  CUSTOMER_SERVICE: '客服对话',
  CHAT: '宠物陪伴',
  CONTENT_GENERATE: '内容生成',
  ANALYSIS: '经营分析',
  MODERATION: '内容审核',
}

const apiTypeOptions = Object.keys(API_TYPE_LABELS).map((value) => ({
  value,
  label: API_TYPE_LABELS[value],
}))

const formatApiType = (type: string) => API_TYPE_LABELS[type] || type

const apiTypeTagType = (type: string): 'primary' | 'success' | 'warning' | 'info' | 'danger' => {
  switch (type) {
    case 'CUSTOMER_SERVICE':
      return 'primary'
    case 'CHAT':
      return 'success'
    case 'CONTENT_GENERATE':
      return 'warning'
    case 'ANALYSIS':
      return 'info'
    case 'MODERATION':
      return 'danger'
    default:
      return 'info'
  }
}

const formatDateTime = (value: string) => {
  if (!value) return '未记录时间'
  return value.replace('T', ' ').slice(0, 19)
}

const fetchData = async () => {
  loading.value = true
  try {
    const res = await listUsage({
      ...queryParams,
      success: queryParams.success === '' ? undefined : queryParams.success,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined,
    })
    if (res.data) {
      tableData.value = res.data.items
      total.value = res.data.total
    }
  } catch {
    /* handled by interceptor */
  } finally {
    loading.value = false
  }
}

const handlePageChange = (page: number, size: number) => {
  queryParams.page = page
  queryParams.size = size
  fetchData()
}

const handleReset = () => {
  queryParams.apiType = ''
  queryParams.success = ''
  dateRange.value = null
  queryParams.page = 1
  fetchData()
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.pc-ai-usage {
  padding: 0;
}

.pc-ai-usage__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--pc-spacing-lg);
}

.pc-ai-usage__title {
  margin: 0 0 var(--pc-spacing-sm) 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--pc-ink);
}

.pc-ai-usage__intro {
  margin: 0;
  color: var(--pc-text-muted);
  font-size: 14px;
  max-width: 640px;
}
</style>
