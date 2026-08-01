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
      <el-table-column prop="role" label="岗位" width="120">
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
          <el-button
            size="small" type="success"
            v-if="canEnableStaff(row.status)"
            @click="handleEnable(row.id)"
            :disabled="!userStore.hasPermission('staff:profile:enable')"
          >启用</el-button>
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
        <el-form-item label="岗位" prop="role">
          <el-input v-model="form.role" placeholder="如：店长、前台收银、实习美容师" />
        </el-form-item>
        <el-form-item label="服务技能" prop="skillCategoryIds">
          <el-select
            v-model="form.skillCategoryIds"
            multiple
            placeholder="选择该员工能提供的服务类别（可多选）"
            style="width: 100%"
          >
            <el-option
              v-for="cat in serviceCategories"
              :key="cat.id"
              :label="cat.name"
              :value="Number(cat.id)"
            />
          </el-select>
          <div class="pc-staff__hint">
            决定该员工出现在哪些服务的预约时段中。不选则该员工的排班不会出现在任何用户预约里。
          </div>
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
      <!-- 技能信息条：getAvailability 按 staff_skill 过滤员工，0 技能的员工排班再多也不会出现在用户预约里 -->
      <el-alert
        v-if="scheduleSkillLoaded"
        :title="scheduleSkillCount > 0
          ? `该员工当前已配置 ${scheduleSkillCount} 项服务技能，排班将出现在对应服务的用户预约时段中。`
          : '该员工尚未配置任何服务技能。新增排班后，用户端预约查询仍会过滤掉该员工——请先在「编辑」中为其选择服务技能。'"
        :type="scheduleSkillCount > 0 ? 'info' : 'warning'"
        :closable="false"
        show-icon
        style="margin-bottom: var(--pc-spacing-md);"
      />
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
              <el-date-picker
                v-model="scheduleForm.workDate"
                type="date"
                value-format="YYYY-MM-DD"
                placeholder="选择日期"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="开始" prop="startTime">
              <el-time-picker
                v-model="scheduleForm.startTime"
                value-format="HH:mm"
                format="HH:mm"
                placeholder="选择时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="结束" prop="endTime">
              <el-time-picker
                v-model="scheduleForm.endTime"
                value-format="HH:mm"
                format="HH:mm"
                placeholder="选择时间"
                style="width: 100%"
              />
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

    <!-- Enable Confirm Dialog -->
    <ActionConfirmDialog
      :visible="enableDialogVisible"
      title="启用员工"
      message="确定要启用此员工吗？启用后该员工可重新被分配新预约。"
      :danger="false"
      @confirm="executeEnable"
      @cancel="enableDialogVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getStaffList, createStaff, updateStaff, disableStaff, enableStaff, getStaffSchedules, createStaffSchedule, getStaffSkills } from '../../api/staff'
import type { StaffMember, StaffCreateParams, StaffSchedule, StaffScheduleCreateParams } from '../../api/staff'
import { STORE_ID } from '../../api/store'
import { getServiceCategories } from '../../api/service'
import type { FormInstance, FormRules } from 'element-plus'
import { useUserStore } from '../../store/user'
import { showSuccess, showError } from '../../utils/feedback'
import { STAFF_STATUS, STAFF_ROLE, SCHEDULE_STATUS, canDisableStaff, canEnableStaff } from '../../types/status'
import type { StaffStatus as StaffStatusType, StaffRole, ScheduleStatus } from '../../types/status'
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
  role: '',
  description: '',
  skillCategoryIds: [],
}
const form = ref<StaffCreateParams>({ ...defaultStaffForm })

// 服务分类（技能多选的数据源）
const serviceCategories = ref<{ id: string; name: string }[]>([])

const rules: FormRules = {
  name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
  role: [{ required: true, message: '请输入岗位', trigger: 'blur' }],
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

// ─── Enable Confirm Dialog ───
const enableDialogVisible = ref(false)
const enableTargetId = ref(0)

const handleEnable = (id: number) => {
  enableTargetId.value = id
  enableDialogVisible.value = true
}

const executeEnable = async () => {
  enableDialogVisible.value = false
  try {
    await enableStaff(enableTargetId.value)
    showSuccess('员工已启用')
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
// 技能信息条：决定排班弹窗里那条 el-alert 的文案/类型
const scheduleSkillCount = ref(0)
const scheduleSkillLoaded = ref(false)
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
  workDate: [{ required: true, message: '请选择日期', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
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

const openEditDialog = async (row: StaffMember) => {
  isEdit.value = true
  currentId.value = row.id
  dialogTitle.value = '编辑员工'
  // skillCategoryIds 设为 undefined：让后端 updateStaff 走"未提供技能 → 不更新"分支
  // （AdminManagementServiceImpl.updateStaff: if (request.skillCategoryIds() != null)）。
  // 若读取失败仍保持 undefined，避免把空数组当成"清空技能"提交。
  form.value = { storeId: row.storeId, name: row.name, phone: row.phone ?? undefined, role: row.role, description: row.description ?? undefined, skillCategoryIds: undefined }
  // 回填已有技能；失败时显式报错并保持 undefined（绝不静默吞错后用 [] 覆盖）
  try {
    const res = await getStaffSkills(row.id)
    if (res.data?.serviceCategoryIds) {
      form.value.skillCategoryIds = res.data.serviceCategoryIds.map(Number)
    } else {
      form.value.skillCategoryIds = []
    }
  } catch {
    showError('读取员工技能失败，请关闭后重试。本次保存不会改动技能（保留原值）。')
    form.value.skillCategoryIds = undefined
  }
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
  // 重置技能信息条状态，避免上一名员工的数据残留导致误导
  scheduleSkillCount.value = 0
  scheduleSkillLoaded.value = false
  scheduleDialogVisible.value = true
  // 并行加载排班与技能数；技能失败时降级为"未加载"，不阻塞排班操作
  await Promise.all([loadSchedules(), loadScheduleSkillCount(row.id)])
}

const loadScheduleSkillCount = async (staffId: number) => {
  try {
    const res = await getStaffSkills(staffId)
    scheduleSkillCount.value = res.data?.serviceCategoryIds?.length ?? 0
    scheduleSkillLoaded.value = true
  } catch {
    // 加载失败时不展示告警条（scheduleSkillLoaded 保持 false），避免误报"0 技能"
    scheduleSkillLoaded.value = false
  }
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

const loadServiceCategories = async () => {
  try {
    const res = await getServiceCategories()
    if (res.data) {
      serviceCategories.value = res.data.map(c => ({ id: String(c.id), name: c.name }))
    }
  } catch {
    // 加载失败不阻塞页面
  }
}

onMounted(() => {
  fetchData()
  loadServiceCategories()
})
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

.pc-staff__hint {
  margin-top: 4px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--el-text-color-secondary, #909399);
}
</style>
