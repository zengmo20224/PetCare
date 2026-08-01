<template>
  <div class="pc-booking">
    <h2 class="pc-booking__title">预约管理</h2>

    <FilterBar @search="fetchData" @reset="handleReset">
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="(v, k) in BOOKING_STATUS" :key="k" :label="v.label" :value="k" />
        </el-select>
      </el-form-item>
      <el-form-item label="预约日期">
        <el-input v-model="queryParams.bookingDate" placeholder="YYYY-MM-DD" clearable style="width: 150px" />
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
      <el-table-column prop="bookingNo" label="预约编号" width="150" />
      <el-table-column prop="serviceMode" label="模式" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.serviceMode === 'STORE' ? 'success' : 'warning'">
            {{ row.serviceMode === 'STORE' ? '到店' : '上门' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="serviceItemName" label="服务项目" min-width="120" show-overflow-tooltip />
      <el-table-column prop="bookingDate" label="日期" width="110" />
      <el-table-column label="时间" width="120">
        <template #default="{ row }">{{ row.startTime }} - {{ row.endTime }}</template>
      </el-table-column>
      <el-table-column prop="contactName" label="联系人" width="90" />
      <el-table-column prop="price" label="价格" width="90">
        <template #default="{ row }">{{ Number(row.price).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="paymentStatus" label="支付" width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="PAYMENT_STATUS[row.paymentStatus as PaymentStatus]?.color || 'info'">
            {{ PAYMENT_STATUS[row.paymentStatus as PaymentStatus]?.label || row.paymentStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="BOOKING_STATUS[row.status as BookingStatusType]?.color || 'info'">
            {{ BOOKING_STATUS[row.status as BookingStatusType]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="viewDetail(row.id)">详情</el-button>
          <!-- 拒绝与取消已合并为单一“取消”入口（取消=拒绝/作废），统一走 cancel 接口，
               强制填写原因并经 admin_operation_log + booking_status_log 双层留痕。
               开始服务/完成两步服务流程由下方两个按钮承载（a5a8810 起的设计）。 -->
          <el-button size="small" type="primary" v-if="getBookingActions(row.status).includes('start')" @click="handleStart(row.id)" :disabled="!userStore.hasPermission('booking:booking:start')">开始服务</el-button>
          <el-button size="small" type="success" v-if="getBookingActions(row.status).includes('complete')" @click="handleComplete(row.id)" :disabled="!userStore.hasPermission('booking:booking:complete')">完成</el-button>
          <el-button size="small" type="danger" v-if="getBookingActions(row.status).includes('cancel')" @click="handleCancel(row.id)" :disabled="!userStore.hasPermission('booking:booking:cancel')">取消</el-button>
        </template>
      </el-table-column>
    </DataTableShell>

    <!-- Detail Drawer -->
    <DetailDrawer
      :visible="detailVisible"
      title="预约详情"
      width="550px"
      @close="handleDetailClose"
    >
      <el-descriptions :column="2" border v-if="detailData">
        <el-descriptions-item label="预约编号">{{ detailData.bookingNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ BOOKING_STATUS[detailData.status as BookingStatusType]?.label || detailData.status }}</el-descriptions-item>
        <el-descriptions-item label="服务模式">{{ detailData.serviceMode }}</el-descriptions-item>
        <el-descriptions-item label="服务项目">{{ detailData.serviceItemName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="日期">{{ detailData.bookingDate }}</el-descriptions-item>
        <el-descriptions-item label="时间段">{{ detailData.startTime }} - {{ detailData.endTime }}</el-descriptions-item>
        <el-descriptions-item label="价格">{{ Number(detailData.price).toFixed(2) }}</el-descriptions-item>
        <el-descriptions-item label="联系人">{{ detailData.contactName }}</el-descriptions-item>
        <el-descriptions-item label="联系电话">{{ detailData.contactPhone }}</el-descriptions-item>
        <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
        <el-descriptions-item label="商家备注" :span="2">{{ detailData.merchantRemark || '-' }}</el-descriptions-item>
      </el-descriptions>
    </DetailDrawer>

    <!-- Cancel Dialog (拒绝/取消合并入口；需填写原因，记录留痕) -->
    <el-dialog title="取消预约" v-model="cancelVisible" width="400px">
      <el-form ref="cancelFormRef" :model="cancelForm" :rules="cancelRules" label-width="80px">
        <el-form-item label="取消原因" prop="reason">
          <el-input v-model="cancelForm.reason" type="textarea" :rows="3" placeholder="请输入取消原因（必填，将记录留痕）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelVisible = false">返回</el-button>
        <el-button type="danger" @click="submitCancel" :loading="cancelLoading">确认取消</el-button>
      </template>
    </el-dialog>

    <!-- Start Dialog -->
    <el-dialog title="开始服务" v-model="startVisible" width="400px">
      <p style="margin:0;color:#606266;line-height:1.6;">确定开始此预约的服务吗？开始后状态将变为"进行中"。</p>
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="primary" @click="submitStart" :loading="startLoading">确认开始</el-button>
      </template>
    </el-dialog>

    <!-- Complete Dialog -->
    <el-dialog title="完成预约" v-model="completeVisible" width="400px">
      <p style="margin:0;color:#606266;line-height:1.6;">确定完成此预约吗？完成后状态将变为"已完成"，不可再更改。</p>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="success" @click="submitComplete" :loading="completeLoading">确认完成</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getBookingList, getBookingDetail, startBooking, completeBooking, cancelBooking } from '../../api/booking'
import type { Booking } from '../../api/booking'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '../../store/user'
import { showSuccess, showConflict, showError } from '../../utils/feedback'
import { BOOKING_STATUS, PAYMENT_STATUS, getBookingActions } from '../../types/status'
import type { BookingStatus as BookingStatusType, PaymentStatus } from '../../types/status'
import FilterBar from '../../components/FilterBar.vue'
import DataTableShell from '../../components/DataTableShell.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
// ActionConfirmDialog 已不再用于预约页（改用独立 el-dialog 避免 emit 时序问题）

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<Booking[]>([])
const total = ref(0)
const queryParams = reactive({ page: 1, size: 10, status: '', bookingDate: '' })

// ─── Detail Drawer ───

const detailVisible = ref(false)
const detailData = ref<Booking | null>(null)

const viewDetail = async (id: number) => {
  try {
    const res = await getBookingDetail(id)
    if (res.data) { detailData.value = res.data; detailVisible.value = true }
  } catch { /* handled by request interceptor */ }
}

const handleDetailClose = () => {
  detailVisible.value = false
  detailData.value = null
}

// ─── Actions ───

const handleStart = (id: number) => {
  startTargetId.value = id
  startVisible.value = true
}

// ─── Start (独立弹窗，不走 ActionConfirmDialog) ───

const startVisible = ref(false)
const startLoading = ref(false)
const startTargetId = ref(0)

const submitStart = async () => {
  startLoading.value = true
  try {
    await startBooking(startTargetId.value)
    showSuccess('服务已开始')
    startVisible.value = false
    await fetchData()
  } catch (error: unknown) {
    if (error && typeof error === 'object' && 'response' in error) {
      const resp = (error as { response?: { status?: number } }).response
      if (resp?.status === 409) {
        showConflict()
        await fetchData()
        startVisible.value = false
        return
      }
    }
    showError(error instanceof Error ? error.message : '操作失败')
  } finally {
    startLoading.value = false
  }
}

const handleComplete = (id: number) => {
  completeTargetId.value = id
  completeVisible.value = true
}

// ─── Complete (独立弹窗) ───

const completeVisible = ref(false)
const completeLoading = ref(false)
const completeTargetId = ref(0)

const submitComplete = async () => {
  completeLoading.value = true
  try {
    await completeBooking(completeTargetId.value)
    showSuccess('预约已完成')
    completeVisible.value = false
    await fetchData()
  } catch (error: unknown) {
    if (error && typeof error === 'object' && 'response' in error) {
      const resp = (error as { response?: { status?: number } }).response
      if (resp?.status === 409) {
        showConflict()
        await fetchData()
        completeVisible.value = false
        return
      }
    }
    showError(error instanceof Error ? error.message : '操作失败')
  } finally {
    completeLoading.value = false
  }
}

const handleCancel = (id: number) => {
  cancelTargetId.value = id
  cancelForm.reason = ''
  cancelVisible.value = true
}

// ─── Cancel (需要填写原因) ───

const cancelVisible = ref(false)
const cancelLoading = ref(false)
const cancelFormRef = ref<FormInstance>()
const cancelTargetId = ref(0)
const cancelForm = reactive({ reason: '' })
const cancelRules: FormRules = { reason: [{ required: true, message: '请输入取消原因', trigger: 'blur' }] }

const submitCancel = async () => {
  if (!cancelFormRef.value) return
  await cancelFormRef.value.validate(async (valid) => {
    if (!valid) return
    cancelLoading.value = true
    try {
      await cancelBooking(cancelTargetId.value, { reason: cancelForm.reason })
      showSuccess('预约已取消')
      cancelVisible.value = false
      await fetchData()
    } catch (error: unknown) {
      if (error && typeof error === 'object' && 'response' in error) {
        const resp = (error as { response?: { status?: number } }).response
        if (resp?.status === 409) {
          showConflict()
          await fetchData()
          return
        }
      }
      showError(error instanceof Error ? error.message : '操作失败')
    } finally {
      cancelLoading.value = false
    }
  })
}

// ─── Reject 与 Cancel 已合并：统一走 Cancel 入口（cancelBooking API）。
//     历史的 reject 独立弹窗/表单/submitReject 已移除。后端 cancelBookingAdmin 同样
//     写 admin_operation_log + booking_status_log，留痕等价。

// ─── Data Fetching ───

const fetchData = async () => {
  loading.value = true
  try {
    const res = await getBookingList(queryParams)
    if (res.data) { tableData.value = res.data.items; total.value = res.data.total }
  } catch { /* handled */ } finally { loading.value = false }
}

const handlePageChange = (page: number, size: number) => {
  queryParams.page = page
  queryParams.size = size
  fetchData()
}

const handleReset = () => {
  queryParams.status = ''
  queryParams.bookingDate = ''
  queryParams.page = 1
  fetchData()
}

onMounted(() => { fetchData() })
</script>

<style scoped>
.pc-booking {
  padding: 0;
}

.pc-booking__title {
  margin: 0 0 var(--pc-spacing-lg) 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--pc-ink);
}
</style>
