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
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="viewDetail(row.id)">详情</el-button>
          <el-button size="small" type="danger" v-if="getBookingActions(row.status).includes('reject')" @click="openRejectDialog(row.id)" :disabled="!userStore.hasPermission('booking:booking:reject')">拒绝</el-button>
          <el-button size="small" type="primary" v-if="getBookingActions(row.status).includes('start')" @click="handleStart(row.id)" :disabled="!userStore.hasPermission('booking:booking:start')">开始服务</el-button>
          <el-button size="small" type="success" v-if="getBookingActions(row.status).includes('complete')" @click="handleComplete(row.id)" :disabled="!userStore.hasPermission('booking:booking:complete')">完成</el-button>
          <el-button size="small" v-if="getBookingActions(row.status).includes('cancel')" @click="handleCancel(row.id)" :disabled="!userStore.hasPermission('booking:booking:cancel')">取消</el-button>
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

    <!-- Confirm Action Dialog -->
    <ActionConfirmDialog
      :visible="confirmDialogVisible"
      :title="confirmDialogTitle"
      :message="confirmDialogMessage"
      :danger="confirmDialogDanger"
      @confirm="executeConfirmedAction"
      @cancel="confirmDialogVisible = false"
    />

    <!-- Reject Dialog (needs form input) -->
    <el-dialog title="拒绝预约" v-model="rejectVisible" width="400px">
      <el-form ref="rejectFormRef" :model="rejectForm" :rules="rejectRules" label-width="80px">
        <el-form-item label="拒绝原因" prop="reason">
          <el-input v-model="rejectForm.reason" type="textarea" :rows="3" placeholder="请输入拒绝原因" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="submitReject" :loading="rejectLoading">确认拒绝</el-button>
      </template>
    </el-dialog>

    <!-- Cancel Dialog (需要填写取消原因) -->
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
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getBookingList, getBookingDetail, rejectBooking, startBooking, completeBooking, cancelBooking } from '../../api/booking'
import type { Booking } from '../../api/booking'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '../../store/user'
import { showSuccess, showConflict, showError } from '../../utils/feedback'
import { BOOKING_STATUS, PAYMENT_STATUS, getBookingActions } from '../../types/status'
import type { BookingStatus as BookingStatusType, PaymentStatus } from '../../types/status'
import FilterBar from '../../components/FilterBar.vue'
import DataTableShell from '../../components/DataTableShell.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
import ActionConfirmDialog from '../../components/ActionConfirmDialog.vue'

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

// ─── Confirm Action Dialog ───

const confirmDialogVisible = ref(false)
const confirmDialogTitle = ref('')
const confirmDialogMessage = ref('')
const confirmDialogDanger = ref(false)
const pendingAction = ref<(() => Promise<void>) | null>(null)

const openConfirmDialog = (title: string, message: string, danger: boolean, action: () => Promise<void>) => {
  confirmDialogTitle.value = title
  confirmDialogMessage.value = message
  confirmDialogDanger.value = danger
  pendingAction.value = action
  confirmDialogVisible.value = true
}

const executeConfirmedAction = async () => {
  if (!pendingAction.value) return
  confirmDialogVisible.value = false
  try {
    await pendingAction.value
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
  }
}

// ─── Actions ───

const handleStart = (id: number) => {
  openConfirmDialog('开始服务', '确定开始服务吗？', false, async () => {
    await startBooking(id)
    showSuccess('服务已开始')
    await fetchData()
  })
}

const handleComplete = (id: number) => {
  openConfirmDialog('完成预约', '确定完成此预约吗？', false, async () => {
    await completeBooking(id)
    showSuccess('预约已完成')
    await fetchData()
  })
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

// ─── Reject ───

const rejectVisible = ref(false)
const rejectLoading = ref(false)
const rejectFormRef = ref<FormInstance>()
const rejectTargetId = ref(0)
const rejectForm = reactive({ reason: '' })
const rejectRules: FormRules = { reason: [{ required: true, message: '请输入拒绝原因', trigger: 'blur' }] }

const openRejectDialog = (id: number) => {
  rejectTargetId.value = id
  rejectForm.reason = ''
  rejectVisible.value = true
}

const submitReject = async () => {
  if (!rejectFormRef.value) return
  await rejectFormRef.value.validate(async (valid) => {
    if (!valid) return
    rejectLoading.value = true
    try {
      await rejectBooking(rejectTargetId.value, { reason: rejectForm.reason })
      showSuccess('预约已拒绝')
      rejectVisible.value = false
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
      rejectLoading.value = false
    }
  })
}

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
