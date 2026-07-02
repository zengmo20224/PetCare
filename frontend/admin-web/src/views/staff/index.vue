<template>
  <div class="pc-staff">
    <div class="pc-staff__header">
      <h2 class="pc-staff__title">员工管理</h2>
      <el-button type="primary" @click="openCreateDialog" :disabled="!userStore.hasPermission('staff:profile:create')">新增员工</el-button>
    </div>

    <FilterBar @search="fetchData" @reset="handleReset">
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option v-for="(v, k) in STAFF_STATUS" :key="k" :label="v.label" :value="k" />
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
      <el-table-column prop="name" label="姓名" width="120" />
      <el-table-column prop="phone" label="电话" width="130" />
      <el-table-column prop="role" label="角色" width="120">
        <template #default="{ row }">
          <el-tag :type="STAFF_ROLE[row.role as StaffRole]?.color || 'info'">
            {{ STAFF_ROLE[row.role as StaffRole]?.label || row.role }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="STAFF_STATUS[row.status as StaffStatusType]?.color || 'info'">
            {{ STAFF_STATUS[row.status as StaffStatusType]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="description" label="描述" show-overflow-tooltip />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEditDialog(row)" :disabled="!userStore.hasPermission('staff:profile:update')">编辑</el-button>
          <el-button size="small" @click="openScheduleDialog(row)" :disabled="!userStore.hasPermission('staff:schedule:read')">排班</el-button>
          <el-button
            size="small" type="danger"
            v-if="canDisableStaff(row.status)"
            @click="handleDisable(row.id)"
            :disabled="!userStore.hasPermission('staff:profile:disable')"
          >禁用</el-button>
        </template>
      </el-table-column>
    </DataTableShell>

    <!-- Create/Edit Dialog -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="500px" @close="resetForm">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="员工姓名" />
        </el-form-item>
        <el-form-item label="电话" prop="phone">
          <el-input v-model="form.phone" placeholder="联系电话" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="form.role" style="width: 100%">
            <el-option v-for="(v, k) in STAFF_ROLE" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitForm" :loading="submitLoading">确认</el-button>
      </template>
    </el-dialog>

    <!-- Schedule Dialog -->
    <el-dialog :title="`排班管理 - ${scheduleStaffName}`" v-model="scheduleDialogVisible" width="700px">
      <div class="pc-staff__schedule-actions">
        <el-button type="primary" size="small" @click="openScheduleForm" :disabled="!userStore.hasPermission('staff:schedule:manage')">新增排班</el-button>
      </div>
      <el-table :data="scheduleData" border v-loading="scheduleLoading" size="small">
        <el-table-column prop="workDate" label="日期" width="120" />
        <el-table-column prop="startTime" label="开始" width="80" />
        <el-table-column prop="endTime" label="结束" width="80" />
        <el-table-column prop="status" label="状态" width="90">
          <template #default="{ row }">
            <el-tag :type="SCHEDULE_STATUS[row.status as ScheduleStatus]?.color || 'info'" size="small">
              {{ SCHEDULE_STATUS[row.status as ScheduleStatus]?.label || row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
      </el-table>
      <!-- Inline schedule form -->
      <el-form v-if="showScheduleForm" :model="scheduleForm" :rules="scheduleRules" ref="scheduleFormRef" label-width="80px" class="pc-staff__schedule-form">
        <el-row :gutter="16">
          <el-col :span="8">
            <el-form-item label="日期" prop="workDate">
              <el-input v-model="scheduleForm.workDate" placeholder="YYYY-MM-DD" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开始" prop="startTime">
              <el-input v-model="scheduleForm.startTime" placeholder="HH:mm" />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="结束" prop="endTime">
              <el-input v-model="scheduleForm.endTime" placeholder="HH:mm" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="状态" prop="status">
          <el-select v-model="scheduleForm.status" style="width: 200px">
            <el-option v-for="(v, k) in SCHEDULE_STATUS" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="scheduleForm.remark" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="submitSchedule" :loading="scheduleSubmitting">保存</el-button>
          <el-button @click="showScheduleForm = false">取消</el-button>
        </el-form-item>
      </el-form>
    </el-dialog>

    <!-- Disable Confirm Dialog -->
    <ActionConfirmDialog
      :visible="disableDialogVisible"
      title="禁用员工"
      message="确定要禁用此员工吗？禁用后该员工将无法被分配新预约。"
      :danger="true"
      @confirm="executeDisable"
      @cancel="disableDialogVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getStaffList, createStaff, updateStaff, disableStaff, getStaffSchedules, createStaffSchedule } from '../../api/staff'
import type { StaffMember, StaffCreateParams, StaffSchedule, StaffScheduleCreateParams } from '../../api/staff'
import { STORE_ID } from '../../api/store'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '../../store/user'
import { showSuccess, showError } from '../../utils/feedback'
import { STAFF_ROLE, STAFF_STATUS, SCHEDULE_STATUS, canDisableStaff } from '../../types/status'
import type { StaffRole, StaffStatus as StaffStatusType, ScheduleStatus } from '../../types/status'
import FilterBar from '../../components/FilterBar.vue'
import DataTableShell from '../../components/DataTableShell.vue'
import ActionConfirmDialog from '../../components/ActionConfirmDialog.vue'

const userStore = useUserStore()

const loading = ref(false)
const tableData = ref<StaffMember[]>([])
const total = ref(0)
const queryParams = reactive({ page: 1, size: 10, status: '' })

// ─── Staff Form ───
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const isEdit = ref(false)
const currentId = ref<number | undefined>()

const defaultStaffForm: StaffCreateParams = {
  storeId: STORE_ID,
  name: '',
  phone: '',
  role: 'GROOMER',
  description: '',
}
const form = ref<StaffCreateParams>({ ...defaultStaffForm })

const rules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请选择角色', trigger: 'change' }],
  storeId: [{ required: true, message: '门店ID必填', trigger: 'blur' }],
}

// ─── Disable Confirm Dialog ───
const disableDialogVisible = ref(false)
const disableTargetId = ref(0)

const handleDisable = (id: number) => {
  disableTargetId.value = id
  disableDialogVisible.value = true
}

const executeDisable = async () => {
  disableDialogVisible.value = false
  try {
    await disableStaff(disableTargetId.value)
    showSuccess('员工已禁用')
    await fetchData()
  } catch (error) {
    showError(error instanceof Error ? error.message : '操作失败')
  }
}

// ─── Schedule Dialog ───
const scheduleDialogVisible = ref(false)
const scheduleStaffId = ref(0)
const scheduleStaffName = ref('')
const scheduleData = ref<StaffSchedule[]>([])
const scheduleLoading = ref(false)
const showScheduleForm = ref(false)
const scheduleFormRef = ref<FormInstance>()
const scheduleSubmitting = ref(false)
const scheduleForm = ref<StaffScheduleCreateParams>({
  storeId: STORE_ID,
  workDate: '',
  startTime: '',
  endTime: '',
  status: 'AVAILABLE',
  remark: '',
})

const scheduleRules: FormRules = {
  workDate: [{ required: true, message: '请输入日期', trigger: 'blur' }],
  startTime: [{ required: true, message: '请输入开始时间', trigger: 'blur' }],
  endTime: [{ required: true, message: '请输入结束时间', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  storeId: [{ required: true, message: '门店ID必填', trigger: 'blur' }],
}

// ─── Data Loading ───
const fetchData = async () => {
  loading.value = true
  try {
    const res = await getStaffList(queryParams)
    if (res.data) {
      tableData.value = res.data.items
      total.value = res.data.total
    }
  } catch { /* handled */ } finally {
    loading.value = false
  }
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

// ─── Staff CRUD ───
const openCreateDialog = () => {
  isEdit.value = false
  dialogTitle.value = '新增员工'
  form.value = { ...defaultStaffForm }
  dialogVisible.value = true
}

const openEditDialog = (row: StaffMember) => {
  isEdit.value = true
  currentId.value = row.id
  dialogTitle.value = '编辑员工'
  form.value = { storeId: row.storeId, name: row.name, phone: row.phone ?? undefined, role: row.role, description: row.description ?? undefined }
  dialogVisible.value = true
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (isEdit.value && currentId.value) {
        await updateStaff(currentId.value, form.value)
        showSuccess('员工信息已更新')
      } else {
        await createStaff(form.value)
        showSuccess('员工已创建')
      }
      dialogVisible.value = false
      await fetchData()
    } catch (error) {
      showError(error instanceof Error ? error.message : '操作失败')
    } finally {
      submitLoading.value = false
    }
  })
}

const resetForm = () => { formRef.value?.resetFields() }

// ─── Schedule ───
const openScheduleDialog = async (row: StaffMember) => {
  scheduleStaffId.value = row.id
  scheduleStaffName.value = row.name
  showScheduleForm.value = false
  scheduleDialogVisible.value = true
  await loadSchedules()
}

const loadSchedules = async () => {
  scheduleLoading.value = true
  try {
    const res = await getStaffSchedules(scheduleStaffId.value, { page: 1, size: 50 })
    if (res.data) { scheduleData.value = res.data.items }
  } catch { /* handled */ } finally { scheduleLoading.value = false }
}

const openScheduleForm = () => {
  scheduleForm.value = { storeId: STORE_ID, workDate: '', startTime: '', endTime: '', status: 'AVAILABLE', remark: '' }
  showScheduleForm.value = true
}

const submitSchedule = async () => {
  if (!scheduleFormRef.value) return
  await scheduleFormRef.value.validate(async (valid) => {
    if (!valid) return
    scheduleSubmitting.value = true
    try {
      await createStaffSchedule(scheduleStaffId.value, scheduleForm.value)
      showSuccess('排班已添加')
      showScheduleForm.value = false
      await loadSchedules()
    } catch (error) {
      showError(error instanceof Error ? error.message : '排班保存失败')
    } finally {
      scheduleSubmitting.value = false
    }
  })
}

onMounted(() => { fetchData() })
</script>

<style scoped>
.pc-staff {
  padding: 0;
}

.pc-staff__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--pc-spacing-lg);
}

.pc-staff__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--pc-ink);
}

.pc-staff__schedule-actions {
  margin-bottom: var(--pc-spacing-md);
}

.pc-staff__schedule-form {
  margin-top: var(--pc-spacing-md);
  border-top: 1px solid var(--pc-line);
  padding-top: var(--pc-spacing-md);
}
</style>
