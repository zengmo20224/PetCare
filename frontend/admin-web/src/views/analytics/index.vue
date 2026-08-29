<template>
  <div class="analytics-container">
    <!-- 筛选与导出 -->
    <el-card shadow="never" class="filter-card">
      <div class="filter-bar">
        <el-date-picker
          v-model="range"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          :clearable="false"
        />
        <el-button type="primary" :loading="loading" @click="loadAll">查询</el-button>
        <el-button :loading="exporting" @click="handleExport">导出 Excel</el-button>
      </div>
    </el-card>

    <!-- 概览卡 -->
    <div class="stat-grid">
      <el-card shadow="hover" class="stat-card">
        <div class="stat-card__value">¥{{ formatAmount(overview?.totalRevenue) }}</div>
        <div class="stat-card__label">总营业额（完成口径）</div>
      </el-card>
      <el-card shadow="hover" class="stat-card">
        <div class="stat-card__value">¥{{ formatAmount(overview?.bookingRevenue) }}</div>
        <div class="stat-card__label">服务营业额 · 完成率 {{ rate(overview?.bookingCompletionRate) }}</div>
      </el-card>
      <el-card shadow="hover" class="stat-card">
        <div class="stat-card__value">¥{{ formatAmount(overview?.productRevenue) }}</div>
        <div class="stat-card__label">商品营业额 · 完成率 {{ rate(overview?.productCompletionRate) }}</div>
      </el-card>
      <el-card shadow="hover" class="stat-card">
        <div class="stat-card__value">{{ overview?.totalValidOrders ?? '—' }}</div>
        <div class="stat-card__label">有效订单数（服务 {{ overview?.validBookingOrders ?? 0 }} / 商品 {{ overview?.validProductOrders ?? 0 }}）</div>
      </el-card>
    </div>

    <!-- 每日趋势图 -->
    <el-card shadow="never" class="chart-card">
      <template #header><span class="card-title">每日营业额与订单趋势</span></template>
      <div ref="chartEl" class="chart"></div>
    </el-card>

    <!-- 销量 Top -->
    <div class="top-grid">
      <el-card shadow="never">
        <template #header><span class="card-title">服务销量 Top10</span></template>
        <el-table :data="top?.services ?? []" size="small">
          <el-table-column type="index" label="排名" width="60" />
          <el-table-column prop="itemName" label="服务名称">
            <template #default="{ row }">{{ row.itemName ?? '（已下架服务）' }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="完成单数" width="90" />
          <el-table-column label="销售额（元）" width="110">
            <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
          </el-table-column>
        </el-table>
      </el-card>
      <el-card shadow="never">
        <template #header><span class="card-title">商品销量 Top10</span></template>
        <el-table :data="top?.products ?? []" size="small">
          <el-table-column type="index" label="排名" width="60" />
          <el-table-column prop="itemName" label="商品名称" />
          <el-table-column prop="quantity" label="销量" width="90" />
          <el-table-column label="销售额（元）" width="110">
            <template #default="{ row }">{{ formatAmount(row.amount) }}</template>
          </el-table-column>
        </el-table>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { showSuccess, showError } from '../../utils/feedback'
import {
  getAnalyticsOverview,
  getDailyTrend,
  getTopItems,
  exportAnalyticsReport,
  downloadReportBlob,
} from '../../api/analytics'
import type { OverviewReport, DailyTrendPoint, TopItemsReport } from '../../api/analytics'

function isoDay(offsetDays: number): string {
  const d = new Date()
  d.setDate(d.getDate() + offsetDays)
  return d.toISOString().slice(0, 10)
}

const range = ref<[string, string]>([isoDay(-29), isoDay(0)])
const loading = ref(false)
const exporting = ref(false)
const overview = ref<OverviewReport | null>(null)
const trend = ref<DailyTrendPoint[]>([])
const top = ref<TopItemsReport | null>(null)

const chartEl = ref<HTMLDivElement | null>(null)
let chart: echarts.ECharts | null = null

function formatAmount(value?: number | null): string {
  return (value ?? 0).toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
}

function rate(value?: number | null): string {
  return value == null ? '—' : `${value}%`
}

function renderChart() {
  if (!chartEl.value) return
  if (!chart) {
    chart = echarts.init(chartEl.value)
  }
  chart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['服务营业额', '商品营业额', '有效订单数'], top: 0 },
    grid: { left: 60, right: 60, top: 48, bottom: 56 },
    xAxis: { type: 'category', data: trend.value.map(p => p.statDate) },
    yAxis: [
      { type: 'value', name: '营业额（元）' },
      { type: 'value', name: '订单数', minInterval: 1 },
    ],
    series: [
      {
        name: '服务营业额',
        type: 'bar',
        stack: 'revenue',
        data: trend.value.map(p => p.bookingAmount),
      },
      {
        name: '商品营业额',
        type: 'bar',
        stack: 'revenue',
        data: trend.value.map(p => p.productAmount),
      },
      {
        name: '有效订单数',
        type: 'line',
        yAxisIndex: 1,
        smooth: true,
        data: trend.value.map(p => p.bookingCount + p.productCount),
      },
    ],
  })
}

async function loadAll() {
  const [startDate, endDate] = range.value
  loading.value = true
  try {
    const [overviewRes, trendRes, topRes] = await Promise.all([
      getAnalyticsOverview(startDate, endDate),
      getDailyTrend(startDate, endDate),
      getTopItems(startDate, endDate),
    ])
    overview.value = overviewRes.data ?? null
    trend.value = trendRes.data ?? []
    top.value = topRes.data ?? null
    renderChart()
  } catch {
    // 错误提示由 request 拦截器统一弹出
  } finally {
    loading.value = false
  }
}

async function handleExport() {
  const [startDate, endDate] = range.value
  exporting.value = true
  try {
    const blob = await exportAnalyticsReport(startDate, endDate)
    downloadReportBlob(blob, `petcare-analytics-${startDate}_${endDate}.xlsx`)
    showSuccess('报表已导出')
  } catch {
    showError('报表导出失败，请重试')
  } finally {
    exporting.value = false
  }
}

function handleResize() {
  chart?.resize()
}

onMounted(() => {
  window.addEventListener('resize', handleResize)
  loadAll()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.analytics-container {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.stat-card__value {
  font-size: 22px;
  font-weight: 600;
  color: var(--pc-text-primary, #303133);
}

.stat-card__label {
  margin-top: 6px;
  font-size: 13px;
  color: var(--pc-text-secondary, #909399);
}

.chart-card .chart {
  width: 100%;
  height: 360px;
}

.card-title {
  font-weight: 600;
  font-size: 14px;
}

.top-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
}

@media (max-width: 1024px) {
  .stat-grid {
    grid-template-columns: repeat(2, 1fr);
  }
  .top-grid {
    grid-template-columns: 1fr;
  }
}
</style>
