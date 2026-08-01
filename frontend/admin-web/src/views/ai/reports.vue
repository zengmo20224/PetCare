<template>
  <div class="pc-ai-reports">
    <div class="pc-ai-reports__header">
      <div>
        <h2 class="pc-ai-reports__title">AI 分析报告</h2>
        <p class="pc-ai-reports__intro">
          基于后端聚合的经营统计数据，由 AI 生成分析摘要与管理建议。报告类型覆盖业务、社区、销售与营销活动。
        </p>
      </div>
      <el-button
        v-if="hasGeneratePermission"
        type="primary"
        :icon="Plus"
        @click="openCreateDialog"
      >
        生成报告
      </el-button>
    </div>

    <FilterBar @search="fetchData" @reset="handleReset">
      <el-form-item label="报告类型">
        <el-select v-model="queryParams.reportType" placeholder="全部类型" clearable style="width: 180px">
          <el-option
            v-for="option in reportTypeOptions"
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
      <el-table-column label="报告类型" width="120">
        <template #default="{ row }">
          <el-tag size="small" :type="reportTypeTagType(row.reportType)">
            {{ formatReportType(row.reportType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="统计区间" width="200">
        <template #default="{ row }">
          {{ row.startDate }} ~ {{ row.endDate }}
        </template>
      </el-table-column>
      <el-table-column label="AI 摘要" min-width="280" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.aiSummary || '-' }}
        </template>
      </el-table-column>
      <el-table-column label="管理建议" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ row.suggestions || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="生成时间" width="180">
        <template #default="{ row }">
          {{ formatDateTime(row.createTime) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetailDialog(row)">查看</el-button>
        </template>
      </el-table-column>
    </DataTableShell>

    <!-- 生成报告对话框 -->
    <el-dialog v-model="createDialogVisible" title="生成 AI 分析报告" width="480px">
      <el-form :model="createForm" label-width="90px">
        <el-form-item label="报告类型" required>
          <el-select v-model="createForm.reportType" placeholder="请选择报告类型" style="width: 100%">
            <el-option
              v-for="option in reportTypeOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="统计区间" required>
          <el-date-picker
            v-model="dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            range-separator="至"
            start-placeholder="开始日期"
            end-placeholder="结束日期"
            style="width: 100%"
            :disabled-date="disableFuture"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">开始生成</el-button>
      </template>
    </el-dialog>

    <!-- 报告详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="报告详情" width="640px">
      <template v-if="detailReport">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="报告类型">
            {{ formatReportType(detailReport.reportType) }}
          </el-descriptions-item>
          <el-descriptions-item label="统计区间">
            {{ detailReport.startDate }} ~ {{ detailReport.endDate }}
          </el-descriptions-item>
          <el-descriptions-item label="生成时间" :span="2">
            {{ formatDateTime(detailReport.createTime) }}
          </el-descriptions-item>
        </el-descriptions>
        <h4 class="pc-ai-reports__section-title">AI 摘要</h4>
        <div class="pc-ai-reports__text-block">{{ detailReport.aiSummary || '暂无' }}</div>
        <h4 class="pc-ai-reports__section-title">管理建议</h4>
        <div class="pc-ai-reports__text-block">{{ detailReport.suggestions || '暂无' }}</div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  generateReport,
  listReports,
} from '../../api/ai-report'
import type {
  AiAnalysisReport,
  AiReportType,
  AiReportQueryParams,
} from '../../api/ai-report'
import { useUserStore } from '../../store/user'
import FilterBar from '../../components/FilterBar.vue'
import DataTableShell from '../../components/DataTableShell.vue'

const userStore = useUserStore()
const hasGeneratePermission = computed(() => userStore.hasPermission('ai:analysis:generate'))

const loading = ref(false)
const tableData = ref<AiAnalysisReport[]>([])
const total = ref(0)
const queryParams = reactive<AiReportQueryParams>({
  page: 1,
  size: 20,
  reportType: '',
})

const REPORT_TYPE_LABELS: Record<AiReportType, string> = {
  BUSINESS: '业务运营',
  COMMUNITY: '社区活跃',
  SALES: '商品销售',
  ACTIVITY: '营销活动',
}

const reportTypeOptions = (Object.keys(REPORT_TYPE_LABELS) as AiReportType[]).map((value) => ({
  value,
  label: REPORT_TYPE_LABELS[value],
}))

const formatReportType = (type: AiReportType) => REPORT_TYPE_LABELS[type] || type

const reportTypeTagType = (type: AiReportType): 'primary' | 'success' | 'warning' | 'info' => {
  switch (type) {
    case 'BUSINESS':
      return 'primary'
    case 'COMMUNITY':
      return 'success'
    case 'SALES':
      return 'warning'
    case 'ACTIVITY':
      return 'info'
    default:
      return 'info'
  }
}

const formatDateTime = (value: string) => {
  if (!value) return '未记录时间'
  return value.replace('T', ' ').slice(0, 19)
}

const disableFuture = (date: Date) => date.getTime() > Date.now()

// ─── List ───
const fetchData = async () => {
  loading.value = true
  try {
    const res = await listReports(queryParams)
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
  queryParams.reportType = ''
  queryParams.page = 1
  fetchData()
}

// ─── Create ───
const createDialogVisible = ref(false)
const creating = ref(false)
const createForm = reactive<{ reportType: AiReportType | '' }>({ reportType: '' })
const dateRange = ref<[string, string] | null>(null)

const openCreateDialog = () => {
  createForm.reportType = ''
  dateRange.value = null
  createDialogVisible.value = true
}

const handleCreate = async () => {
  if (!createForm.reportType) {
    ElMessage.warning('请选择报告类型')
    return
  }
  if (!dateRange.value || dateRange.value.length !== 2) {
    ElMessage.warning('请选择统计区间')
    return
  }
  creating.value = true
  try {
    const res = await generateReport({
      reportType: createForm.reportType as AiReportType,
      startDate: dateRange.value[0],
      endDate: dateRange.value[1],
    })
    if (res.data) {
      ElMessage.success('报告生成成功')
      createDialogVisible.value = false
      queryParams.page = 1
      await fetchData()
    }
  } catch {
    /* handled */
  } finally {
    creating.value = false
  }
}

// ─── Detail ───
const detailDialogVisible = ref(false)
const detailReport = ref<AiAnalysisReport | null>(null)

const openDetailDialog = (row: AiAnalysisReport) => {
  detailReport.value = row
  detailDialogVisible.value = true
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.pc-ai-reports {
  padding: 0;
}

.pc-ai-reports__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: var(--pc-spacing-lg);
}

.pc-ai-reports__title {
  margin: 0 0 var(--pc-spacing-sm) 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--pc-ink);
}

.pc-ai-reports__intro {
  margin: 0;
  color: var(--pc-text-muted);
  font-size: 14px;
  max-width: 640px;
}

.pc-ai-reports__section-title {
  margin: var(--pc-spacing-lg) 0 var(--pc-spacing-sm);
  font-size: 14px;
  font-weight: 600;
  color: var(--pc-ink);
}

.pc-ai-reports__text-block {
  white-space: pre-wrap;
  line-height: 1.6;
  color: var(--pc-ink);
  background: var(--pc-surface);
  border-radius: 8px;
  padding: var(--pc-spacing-sm) var(--pc-spacing-md);
  font-size: 14px;
}
</style>
