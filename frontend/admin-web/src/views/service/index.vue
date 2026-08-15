<template>
  <div class="pc-service">
    <div class="pc-service__header">
      <h2 class="pc-service__title">服务项目管理</h2>
      <el-button type="primary" @click="openCreateDialog" :disabled="!userStore.hasPermission('service:item:create')">新增服务</el-button>
    </div>

    <FilterBar @search="fetchData" @reset="handleReset">
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option v-for="(v, k) in SERVICE_STATUS" :key="k" :label="v.label" :value="k" />
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
      <el-table-column prop="name" label="名称" width="160" />
      <el-table-column prop="serviceMode" label="服务模式" width="120">
        <template #default="{ row }">
          <el-tag :type="SERVICE_MODE[row.serviceMode as ServiceMode]?.color || 'info'">
            {{ SERVICE_MODE[row.serviceMode as ServiceMode]?.label || row.serviceMode }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="price" label="价格 (元)" width="100">
        <template #default="{ row }">
          {{ Number(row.price).toFixed(2) }}
        </template>
      </el-table-column>
      <el-table-column prop="durationMinutes" label="时长 (分钟)" width="110" />
      <el-table-column prop="petType" label="宠物类型" width="90">
        <template #default="{ row }">
          {{ PET_TYPE[row.petType as PetType] || row.petType || '-' }}
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="SERVICE_STATUS[row.status as ServiceStatus]?.color || 'info'">
            {{ SERVICE_STATUS[row.status as ServiceStatus]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="70" />
      <el-table-column label="操作" width="240" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEditDialog(row)" :disabled="!userStore.hasPermission('service:item:update')">编辑</el-button>
          <el-button
            size="small" type="danger"
            v-if="isServiceOnSale(row.status)"
            @click="handleDisable(row.id)"
            :disabled="!userStore.hasPermission('service:item:disable')"
          >禁用</el-button>
          <el-button
            size="small" type="success"
            v-if="!isServiceOnSale(row.status)"
            @click="handleEnable(row.id)"
            :disabled="!userStore.hasPermission('service:item:enable')"
          >启用</el-button>
        </template>
      </el-table-column>
    </DataTableShell>

    <!-- Create/Edit Dialog -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="600px" @close="resetDialog">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="120px">
        <el-form-item label="服务名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入服务名称" />
        </el-form-item>
        <el-form-item label="服务分类" prop="categoryId">
          <el-select v-model="form.categoryId" placeholder="请选择服务分类" style="width: 100%">
            <el-option
              v-for="category in serviceCategories"
              :key="category.id"
              :label="category.name"
              :value="category.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="服务模式" prop="serviceMode">
          <el-select v-model="form.serviceMode" style="width: 100%">
            <el-option v-for="(v, k) in SERVICE_MODE" :key="k" :label="v.label" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格 (元)" prop="price">
          <el-input-number v-model="form.price" :precision="2" :step="1" :min="0" />
        </el-form-item>
        <el-form-item label="时长 (分钟)" prop="durationMinutes">
          <el-input-number v-model="form.durationMinutes" :step="15" :min="1" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="需要地址" prop="needAddress">
              <el-switch v-model="form.needAddress" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="需要宠物" prop="needPet">
              <el-switch v-model="form.needPet" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="宠物类型" prop="petType">
          <el-select v-model="form.petType" clearable style="width: 100%">
            <el-option v-for="(v, k) in PET_TYPE" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="宠物体型" prop="petSize">
          <el-select v-model="form.petSize" clearable style="width: 100%">
            <el-option v-for="(v, k) in PET_SIZE" :key="k" :label="v" :value="k" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="封面图" prop="coverUrl">
          <div class="pc-service__cover-row">
            <el-input v-model="form.coverUrl" placeholder="图片URL，如 /uploads/xxx.png" />
            <el-upload
              v-model:file-list="coverUploadFileList"
              action=""
              accept="image/jpeg,image/png,image/gif,image/webp"
              :show-file-list="false"
              :http-request="uploadCoverImage"
              :before-upload="beforeUploadCoverImage"
            >
              <el-button type="primary">导入封面图</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="详情图片" prop="imageUrls">
          <div class="pc-service__image-list">
            <el-upload
              v-model:file-list="uploadFileList"
              action=""
              accept="image/jpeg,image/png,image/gif,image/webp"
              multiple
              :show-file-list="false"
              :http-request="uploadDetailImage"
              :before-upload="beforeUploadImage"
              :limit="CATALOG_DETAIL_IMAGE_LIMIT"
              :on-exceed="handleImageExceed"
              :disabled="catalogImageCount >= CATALOG_DETAIL_IMAGE_LIMIT"
            >
              <el-button type="primary" :disabled="catalogImageCount >= CATALOG_DETAIL_IMAGE_LIMIT">
                导入照片
              </el-button>
              <template #tip>
                <div class="pc-service__upload-tip">
                  最多导入 {{ CATALOG_DETAIL_IMAGE_LIMIT }} 张，支持 JPG/PNG/GIF/WebP，单张不超过 10MB。
                </div>
              </template>
            </el-upload>
            <div v-for="(_, index) in form.imageUrls" :key="index" class="pc-service__image-row">
              <el-input v-model="form.imageUrls[index]" placeholder="详情图片URL，如 /uploads/detail.png" />
              <el-button @click="removeImageUrl(index)" :disabled="form.imageUrls.length <= 1">删除</el-button>
            </div>
            <el-button
              type="primary"
              text
              @click="addImageUrl"
              :disabled="catalogImageCount >= CATALOG_DETAIL_IMAGE_LIMIT"
            >
              添加图片URL
            </el-button>
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

    <!-- Disable Confirm Dialog -->
    <ActionConfirmDialog
      :visible="disableDialogVisible"
      title="禁用服务项目"
      message="确定要禁用此服务项目吗？禁用后将不再展示给用户。"
      :danger="true"
      @confirm="executeDisable"
      @cancel="disableDialogVisible = false"
    />

    <!-- Enable Confirm Dialog -->
    <ActionConfirmDialog
      :visible="enableDialogVisible"
      title="启用服务项目"
      message="确定要启用此服务项目吗？启用后将重新展示给用户并可被预约。"
      :danger="false"
      @confirm="executeEnable"
      @cancel="enableDialogVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { getServiceItems, getServiceCategories, createServiceItem, updateServiceItem, disableServiceItem, enableServiceItem } from '../../api/service'
import type { ServiceItem, ServiceItemCreateParams, ServiceCategory } from '../../api/service'
import { uploadCatalogImage } from '../../api/upload'
import type { FormInstance, FormRules, UploadRawFile, UploadRequestOptions, UploadUserFile } from 'element-plus'
import { useUserStore } from '../../store/user'
import { showSuccess, showError } from '../../utils/feedback'
import {
  CATALOG_DETAIL_IMAGE_LIMIT,
  MAX_UPLOAD_SIZE,
  SERVICE_MODE,
  SERVICE_STATUS,
  PET_TYPE,
  PET_SIZE,
  isServiceOnSale,
} from '../../types/status'
import type { ServiceMode, ServiceStatus, PetType } from '../../types/status'
import FilterBar from '../../components/FilterBar.vue'
import DataTableShell from '../../components/DataTableShell.vue'
import ActionConfirmDialog from '../../components/ActionConfirmDialog.vue'

const userStore = useUserStore()

const loading = ref(false)
const tableData = ref<ServiceItem[]>([])
const total = ref(0)
const queryParams = reactive({ page: 1, size: 10, status: '' })

// ─── Service Categories (active only, from backend) ───
const serviceCategories = ref<ServiceCategory[]>([])

// ─── Dialog ───
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const isEdit = ref(false)
const currentId = ref<number | undefined>(undefined)
const coverUploadFileList = ref<UploadUserFile[]>([])
const uploadFileList = ref<UploadUserFile[]>([])

type ServiceItemForm = Omit<ServiceItemCreateParams, 'categoryId'> & {
  categoryId: number | undefined
  imageUrls: string[]
}

const defaultForm: ServiceItemForm = {
  name: '',
  categoryId: undefined,
  serviceMode: 'STORE',
  price: 0,
  durationMinutes: 30,
  needAddress: false,
  needPet: true,
  petType: 'ALL',
  petSize: 'ALL',
  coverUrl: '',
  imageUrls: [''],
  description: '',
  sort: 0,
}

const form = ref<ServiceItemForm>({ ...defaultForm })
const catalogImageCount = computed(() => normalizeImageUrls(form.value.imageUrls).length)

const rules: FormRules = {
  name: [{ required: true, message: '请输入服务名称', trigger: 'blur' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
  durationMinutes: [{ required: true, message: '请输入时长', trigger: 'blur' }],
  serviceMode: [{ required: true, message: '请选择服务模式', trigger: 'change' }],
  categoryId: [{ required: true, message: '请选择服务分类', trigger: 'change' }],
  needAddress: [{ required: true, message: '请选择', trigger: 'change' }],
  needPet: [{ required: true, message: '请选择', trigger: 'change' }],
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
    await disableServiceItem(disableTargetId.value)
    showSuccess('服务项目已禁用')
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
    await enableServiceItem(enableTargetId.value)
    showSuccess('服务项目已启用')
    await fetchData()
  } catch (error) {
    showError(error instanceof Error ? error.message : '操作失败')
  }
}

// ─── Data Loading ───
const fetchData = async () => {
  loading.value = true
  try {
    const res = await getServiceItems(queryParams)
    if (res.data) {
      tableData.value = res.data.items
      total.value = res.data.total
    }
  } catch { /* handled */ } finally {
    loading.value = false
  }
}

const fetchServiceCategories = async () => {
  try {
    const res = await getServiceCategories()
    serviceCategories.value = res.data ?? []
  } catch { /* handled by interceptor */ }
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

// ─── CRUD ───
const openCreateDialog = () => {
  isEdit.value = false
  dialogTitle.value = '新增服务项目'
  form.value = createDefaultForm()
  // Default to the first active category so the form is submittable as-is.
  if (serviceCategories.value.length > 0) {
    form.value.categoryId = serviceCategories.value[0].id
  }
  dialogVisible.value = true
}

const openEditDialog = (row: ServiceItem) => {
  isEdit.value = true
  currentId.value = row.id
  dialogTitle.value = '编辑服务项目'
  form.value = {
    categoryId: row.categoryId,
    name: row.name,
    serviceMode: row.serviceMode,
    price: row.price,
    durationMinutes: row.durationMinutes,
    petType: row.petType ?? undefined,
    petSize: row.petSize ?? undefined,
    needAddress: row.needAddress,
    needPet: row.needPet,
    description: row.description ?? undefined,
    coverUrl: row.coverUrl ?? undefined,
    imageUrls: row.imageUrls?.length ? [...row.imageUrls] : [''],
    sort: row.sort ?? undefined,
  }
  dialogVisible.value = true
}

const createDefaultForm = (): ServiceItemForm => ({
  ...defaultForm,
  imageUrls: [...defaultForm.imageUrls],
})

const normalizeImageUrls = (urls: string[]) => {
  return urls.map((url) => url.trim()).filter(Boolean)
}

const addImageUrl = () => {
  if (catalogImageCount.value >= CATALOG_DETAIL_IMAGE_LIMIT) {
    showError(`详情图片最多导入 ${CATALOG_DETAIL_IMAGE_LIMIT} 张`)
    return
  }
  form.value = {
    ...form.value,
    imageUrls: [...form.value.imageUrls, ''],
  }
}

const removeImageUrl = (index: number) => {
  const next = [...form.value.imageUrls]
  next.splice(index, 1)
  form.value = {
    ...form.value,
    imageUrls: next.length > 0 ? next : [''],
  }
}

const appendImageUrl = (url: string) => {
  const current = normalizeImageUrls(form.value.imageUrls)
  if (current.length >= CATALOG_DETAIL_IMAGE_LIMIT) {
    showError(`详情图片最多导入 ${CATALOG_DETAIL_IMAGE_LIMIT} 张`)
    return false
  }
  const next = current.includes(url) ? current : [...current, url]
  form.value = {
    ...form.value,
    imageUrls: next.length > 0 ? next : [''],
  }
  return true
}

const validateImageFile = (file: UploadRawFile) => {
  const allowedTypes = new Set(['image/jpeg', 'image/png', 'image/gif', 'image/webp'])
  if (!allowedTypes.has(file.type)) {
    showError('只支持 JPG/PNG/GIF/WebP 格式')
    return false
  }
  if (file.size > MAX_UPLOAD_SIZE) {
    showError('单张图片不能超过 10MB')
    return false
  }
  return true
}

const beforeUploadCoverImage = (file: UploadRawFile) => {
  return validateImageFile(file)
}

const beforeUploadImage = (file: UploadRawFile) => {
  if (!validateImageFile(file)) {
    return false
  }
  if (catalogImageCount.value >= CATALOG_DETAIL_IMAGE_LIMIT) {
    showError(`详情图片最多导入 ${CATALOG_DETAIL_IMAGE_LIMIT} 张`)
    return false
  }
  return true
}

const uploadCoverImage = async (options: UploadRequestOptions) => {
  try {
    const res = await uploadCatalogImage(options.file)
    const url = res.data?.url
    if (!url) throw new Error('上传失败')
    form.value = {
      ...form.value,
      coverUrl: url,
    }
    options.onSuccess?.(res)
    showSuccess('封面图已导入')
  } catch (error) {
    const normalizedError = error instanceof Error ? error : new Error('上传失败')
    options.onError?.(Object.assign(normalizedError, { status: 0, method: 'POST', url: '/v1/upload' }))
    showError(normalizedError.message)
  } finally {
    coverUploadFileList.value = []
  }
}

const uploadDetailImage = async (options: UploadRequestOptions) => {
  try {
    const res = await uploadCatalogImage(options.file)
    const url = res.data?.url
    if (!url) throw new Error('上传失败')
    if (appendImageUrl(url)) {
      options.onSuccess?.(res)
      showSuccess('照片已导入')
    }
  } catch (error) {
    const normalizedError = error instanceof Error ? error : new Error('上传失败')
    options.onError?.(Object.assign(normalizedError, { status: 0, method: 'POST', url: '/v1/upload' }))
    showError(normalizedError.message)
  } finally {
    uploadFileList.value = []
  }
}

const handleImageExceed = () => {
  showError(`详情图片最多导入 ${CATALOG_DETAIL_IMAGE_LIMIT} 张`)
}

const validateImageLimit = () => {
  if (normalizeImageUrls(form.value.imageUrls).length > CATALOG_DETAIL_IMAGE_LIMIT) {
    showError(`详情图片最多导入 ${CATALOG_DETAIL_IMAGE_LIMIT} 张`)
    return false
  }
  return true
}

const buildPayload = (): ServiceItemCreateParams => {
  // formRef.validate 已在 submitForm 上游强制校验 categoryId 必填，
  // 到此处 categoryId 必为 number，断言收紧类型避免 vue-tsc 报错。
  if (form.value.categoryId === undefined) {
    throw new Error('服务分类未选择')
  }
  return {
    ...form.value,
    categoryId: form.value.categoryId,
    imageUrls: normalizeImageUrls(form.value.imageUrls),
  }
}

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!validateImageLimit()) return
    submitLoading.value = true
    try {
      const payload = buildPayload()
      if (isEdit.value && currentId.value) {
        await updateServiceItem(currentId.value, payload)
        showSuccess('服务项目已更新')
      } else {
        await createServiceItem(payload)
        showSuccess('服务项目已创建')
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

const resetDialog = () => {
  formRef.value?.resetFields()
  coverUploadFileList.value = []
  uploadFileList.value = []
}

onMounted(() => {
  fetchServiceCategories()
  fetchData()
})
</script>

<style scoped>
.pc-service {
  padding: 0;
}

.pc-service__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--pc-spacing-lg);
}

.pc-service__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--pc-ink);
}

.pc-service__image-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.pc-service__cover-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.pc-service__cover-row .el-input {
  flex: 1;
}

.pc-service__image-row {
  display: flex;
  gap: 8px;
}

.pc-service__upload-tip {
  color: var(--pc-text-secondary, #909399);
  font-size: 12px;
  line-height: 1.5;
}
</style>
