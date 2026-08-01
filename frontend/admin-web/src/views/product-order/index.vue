<template>
  <div class="pc-product-order">
    <h2 class="pc-product-order__title">自提订单管理</h2>

    <FilterBar @search="fetchData" @reset="handleReset">
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="(v, k) in PRODUCT_ORDER_STATUS" :key="k" :label="v.label" :value="k" />
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
      <el-table-column prop="orderNo" label="订单号" width="160" />
      <el-table-column prop="contactName" label="联系人" width="100" />
      <el-table-column prop="contactPhone" label="电话" width="130" />
      <el-table-column prop="totalAmount" label="金额" width="100">
        <template #default="{ row }">{{ Number(row.totalAmount).toFixed(2) }}</template>
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
          <el-tag :type="PRODUCT_ORDER_STATUS[row.status as ProductOrderStatusType]?.color || 'info'">
            {{ PRODUCT_ORDER_STATUS[row.status as ProductOrderStatusType]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="viewDetail(row.id)">详情</el-button>
          <el-button size="small" type="success" v-if="getProductOrderActions({ status: row.status, paymentStatus: row.paymentStatus, pickupStatus: row.pickupStatus }).includes('confirm')" @click="handleConfirm(row.id)" :disabled="!userStore.hasPermission('product:order:confirm')">确认</el-button>
          <el-button size="small" v-if="getProductOrderActions({ status: row.status, paymentStatus: row.paymentStatus, pickupStatus: row.pickupStatus }).includes('confirm-payment')" @click="handleConfirmPayment(row.id)" :disabled="!userStore.hasPermission('product:order:confirm-payment')">确认支付</el-button>
          <el-button size="small" type="primary" v-if="getProductOrderActions({ status: row.status, paymentStatus: row.paymentStatus, pickupStatus: row.pickupStatus }).includes('ready')" @click="handleReady(row.id)" :disabled="!userStore.hasPermission('product:order:ready')">备货完成</el-button>
          <el-button size="small" type="success" v-if="getProductOrderActions({ status: row.status, paymentStatus: row.paymentStatus, pickupStatus: row.pickupStatus }).includes('complete')" @click="handleComplete(row.id)" :disabled="!userStore.hasPermission('product:order:complete')">完成</el-button>
          <el-button size="small" type="danger" v-if="getProductOrderActions({ status: row.status, paymentStatus: row.paymentStatus, pickupStatus: row.pickupStatus }).includes('cancel')" @click="handleCancel(row.id)" :disabled="!userStore.hasPermission('product:order:cancel')">取消</el-button>
          <el-button size="small" type="warning" v-if="getProductOrderActions({ status: row.status, paymentStatus: row.paymentStatus, pickupStatus: row.pickupStatus }).includes('out-of-stock')" @click="handleOutOfStock(row.id)" :disabled="!userStore.hasPermission('product:order:cancel')">缺货</el-button>
        </template>
      </el-table-column>
    </DataTableShell>

    <!-- Detail Drawer -->
    <DetailDrawer
      :visible="detailVisible"
      title="订单详情"
      width="600px"
      @close="handleDetailClose"
    >
      <template v-if="detailData">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="订单号">{{ detailData.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="状态">{{ PRODUCT_ORDER_STATUS[detailData.status as ProductOrderStatusType]?.label || detailData.status }}</el-descriptions-item>
          <el-descriptions-item label="联系人">{{ detailData.contactName }}</el-descriptions-item>
          <el-descriptions-item label="联系电话">{{ detailData.contactPhone }}</el-descriptions-item>
          <el-descriptions-item label="总金额">{{ Number(detailData.totalAmount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="支付状态">{{ PAYMENT_STATUS[detailData.paymentStatus as PaymentStatus]?.label || detailData.paymentStatus }}</el-descriptions-item>
          <el-descriptions-item label="备注" :span="2">{{ detailData.remark || '-' }}</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin-top: 16px">商品明细</h4>
        <el-table :data="detailData.items" border size="small">
          <el-table-column prop="productName" label="名称" />
          <el-table-column prop="price" label="单价" width="100">
            <template #default="{ row }">{{ Number(row.price).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="quantity" label="数量" width="80" />
          <el-table-column prop="totalAmount" label="小计" width="100">
            <template #default="{ row }">{{ Number(row.totalAmount).toFixed(2) }}</template>
          </el-table-column>
        </el-table>
      </template>
    </DetailDrawer>

    <!--
      订单操作弹窗：自 6913f7f 起，预约页用独立 el-dialog 替代 ActionConfirmDialog
      以规避其 emit('confirm') → pendingAction() 链路的时序缺陷（请求发不出）。
      本页沿用同一已验证模式，每个动作一个独立弹窗，直接调用对应 API。
    -->

    <!-- Confirm Dialog -->
    <el-dialog title="确认订单" v-model="confirmVisible" width="400px">
      <p style="margin:0;color:#606266;line-height:1.6;">确定确认此订单吗？确认后状态将变为"备货中"。</p>
      <template #footer>
        <el-button @click="confirmVisible = false">取消</el-button>
        <el-button type="success" @click="submitConfirm" :loading="confirmLoading">确认</el-button>
      </template>
    </el-dialog>

    <!-- Confirm Payment Dialog -->
    <el-dialog title="确认支付" v-model="confirmPaymentVisible" width="400px">
      <p style="margin:0;color:#606266;line-height:1.6;">确定确认此订单的支付吗？确认后视为已收款。</p>
      <template #footer>
        <el-button @click="confirmPaymentVisible = false">取消</el-button>
        <el-button type="primary" @click="submitConfirmPayment" :loading="confirmPaymentLoading">确认支付</el-button>
      </template>
    </el-dialog>

    <!-- Ready Dialog -->
    <el-dialog title="备货完成" v-model="readyVisible" width="400px">
      <p style="margin:0;color:#606266;line-height:1.6;">确定此订单已备货完成吗？确认后状态将变为"待自提"。</p>
      <template #footer>
        <el-button @click="readyVisible = false">取消</el-button>
        <el-button type="primary" @click="submitReady" :loading="readyLoading">确认</el-button>
      </template>
    </el-dialog>

    <!-- Complete Dialog -->
    <el-dialog title="完成订单" v-model="completeVisible" width="400px">
      <p style="margin:0;color:#606266;line-height:1.6;">确定完成此订单吗？完成后状态将变为"已完成"，不可再更改。</p>
      <template #footer>
        <el-button @click="completeVisible = false">取消</el-button>
        <el-button type="success" @click="submitComplete" :loading="completeLoading">确认完成</el-button>
      </template>
    </el-dialog>

    <!-- Cancel Dialog（需填写原因，留痕） -->
    <el-dialog title="取消订单" v-model="cancelVisible" width="400px">
      <el-form ref="cancelFormRef" :model="cancelForm" :rules="cancelRules" label-width="80px">
        <el-form-item label="取消原因" prop="reason">
          <el-input v-model="cancelForm.reason" type="textarea" :rows="3" placeholder="请输入取消原因（必填，将记录留痕）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="cancelVisible = false">取消</el-button>
        <el-button type="danger" @click="submitCancel" :loading="cancelLoading">确认取消</el-button>
      </template>
    </el-dialog>

    <!-- Out-of-Stock Dialog（需填写原因，留痕） -->
    <el-dialog title="缺货取消" v-model="outOfStockVisible" width="400px">
      <el-form ref="outOfStockFormRef" :model="outOfStockForm" :rules="outOfStockRules" label-width="80px">
        <el-form-item label="缺货原因" prop="reason">
          <el-input v-model="outOfStockForm.reason" type="textarea" :rows="3" placeholder="请输入缺货原因（必填，将记录留痕）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="outOfStockVisible = false">取消</el-button>
        <el-button type="warning" @click="submitOutOfStock" :loading="outOfStockLoading">确认缺货</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getProductOrderList, getProductOrderDetail, confirmProductOrder, readyProductOrder, confirmPaymentOrder, completeProductOrder, cancelProductOrder, outOfStockProductOrder } from '../../api/product-order'
import type { ProductOrder, ProductOrderDetail } from '../../api/product-order'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '../../store/user'
import { showSuccess, showConflict, showError } from '../../utils/feedback'
import { PRODUCT_ORDER_STATUS, PAYMENT_STATUS, getProductOrderActions } from '../../types/status'
import type { ProductOrderStatus as ProductOrderStatusType, PaymentStatus } from '../../types/status'
import FilterBar from '../../components/FilterBar.vue'
import DataTableShell from '../../components/DataTableShell.vue'
import DetailDrawer from '../../components/DetailDrawer.vue'
// ActionConfirmDialog 已不再用于商品订单页（改用独立 el-dialog 避免 emit 时序问题）

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<ProductOrder[]>([])
const total = ref(0)
const queryParams = reactive({ page: 1, size: 10, status: '' })

// ─── Detail Drawer ───
const detailVisible = ref(false)
const detailData = ref<ProductOrderDetail | null>(null)

const viewDetail = async (id: number) => {
  try {
    const res = await getProductOrderDetail(id)
    if (res.data) { detailData.value = res.data; detailVisible.value = true }
  } catch { /* handled by interceptor */ }
}

const handleDetailClose = () => {
  detailVisible.value = false
  detailData.value = null
}

// ─── 通用：捕获 409 冲突并刷新数据 ───
const handleActionError = async (error: unknown): Promise<boolean> => {
  if (error && typeof error === 'object' && 'response' in error) {
    const resp = (error as { response?: { status?: number } }).response
    if (resp?.status === 409) {
      showConflict()
      await fetchData()
      return true
    }
  }
  showError(error instanceof Error ? error.message : '操作失败')
  return false
}

// ─── Confirm ───
const confirmVisible = ref(false)
const confirmLoading = ref(false)
const confirmTargetId = ref(0)

const handleConfirm = (id: number) => {
  confirmTargetId.value = id
  confirmVisible.value = true
}

const submitConfirm = async () => {
  confirmLoading.value = true
  try {
    await confirmProductOrder(confirmTargetId.value)
    showSuccess('确认订单成功')
    confirmVisible.value = false
    await fetchData()
  } catch (error) {
    await handleActionError(error)
  } finally {
    confirmLoading.value = false
  }
}

// ─── Confirm Payment ───
const confirmPaymentVisible = ref(false)
const confirmPaymentLoading = ref(false)
const confirmPaymentTargetId = ref(0)

const handleConfirmPayment = (id: number) => {
  confirmPaymentTargetId.value = id
  confirmPaymentVisible.value = true
}

const submitConfirmPayment = async () => {
  confirmPaymentLoading.value = true
  try {
    await confirmPaymentOrder(confirmPaymentTargetId.value)
    showSuccess('确认支付成功')
    confirmPaymentVisible.value = false
    await fetchData()
  } catch (error) {
    await handleActionError(error)
  } finally {
    confirmPaymentLoading.value = false
  }
}

// ─── Ready ───
const readyVisible = ref(false)
const readyLoading = ref(false)
const readyTargetId = ref(0)

const handleReady = (id: number) => {
  readyTargetId.value = id
  readyVisible.value = true
}

const submitReady = async () => {
  readyLoading.value = true
  try {
    await readyProductOrder(readyTargetId.value)
    showSuccess('备货完成成功')
    readyVisible.value = false
    await fetchData()
  } catch (error) {
    await handleActionError(error)
  } finally {
    readyLoading.value = false
  }
}

// ─── Complete ───
const completeVisible = ref(false)
const completeLoading = ref(false)
const completeTargetId = ref(0)

const handleComplete = (id: number) => {
  completeTargetId.value = id
  completeVisible.value = true
}

const submitComplete = async () => {
  completeLoading.value = true
  try {
    await completeProductOrder(completeTargetId.value)
    showSuccess('完成订单成功')
    completeVisible.value = false
    await fetchData()
  } catch (error) {
    await handleActionError(error)
  } finally {
    completeLoading.value = false
  }
}

// ─── Cancel（需填写原因） ───
const cancelVisible = ref(false)
const cancelLoading = ref(false)
const cancelFormRef = ref<FormInstance>()
const cancelTargetId = ref(0)
const cancelForm = reactive({ reason: '' })
const cancelRules: FormRules = { reason: [{ required: true, message: '请输入取消原因', trigger: 'blur' }] }

const handleCancel = (id: number) => {
  cancelTargetId.value = id
  cancelForm.reason = ''
  cancelVisible.value = true
}

const submitCancel = async () => {
  if (!cancelFormRef.value) return
  await cancelFormRef.value.validate(async (valid) => {
    if (!valid) return
    cancelLoading.value = true
    try {
      await cancelProductOrder(cancelTargetId.value, { reason: cancelForm.reason })
      showSuccess('订单已取消')
      cancelVisible.value = false
      await fetchData()
    } catch (error) {
      await handleActionError(error)
    } finally {
      cancelLoading.value = false
    }
  })
}

// ─── Out-of-Stock（需填写原因） ───
const outOfStockVisible = ref(false)
const outOfStockLoading = ref(false)
const outOfStockFormRef = ref<FormInstance>()
const outOfStockTargetId = ref(0)
const outOfStockForm = reactive({ reason: '' })
const outOfStockRules: FormRules = { reason: [{ required: true, message: '请输入缺货原因', trigger: 'blur' }] }

const handleOutOfStock = (id: number) => {
  outOfStockTargetId.value = id
  outOfStockForm.reason = ''
  outOfStockVisible.value = true
}

const submitOutOfStock = async () => {
  if (!outOfStockFormRef.value) return
  await outOfStockFormRef.value.validate(async (valid) => {
    if (!valid) return
    outOfStockLoading.value = true
    try {
      await outOfStockProductOrder(outOfStockTargetId.value, { reason: outOfStockForm.reason })
      showSuccess('缺货取消成功')
      outOfStockVisible.value = false
      await fetchData()
    } catch (error) {
      await handleActionError(error)
    } finally {
      outOfStockLoading.value = false
    }
  })
}

// ─── Data Fetching ───
const fetchData = async () => {
  loading.value = true
  try {
    const res = await getProductOrderList(queryParams)
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
  queryParams.page = 1
  fetchData()
}

onMounted(() => { fetchData() })
</script>

<style scoped>
.pc-product-order {
  padding: 0;
}

.pc-product-order__title {
  margin: 0 0 var(--pc-spacing-lg) 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--pc-ink);
}
</style>
