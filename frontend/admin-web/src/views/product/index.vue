<template>
  <div class="pc-product">
    <div class="pc-product__header">
      <h2 class="pc-product__title">商品管理</h2>
      <el-button type="primary" @click="openCreateDialog" :disabled="!userStore.hasPermission('product:item:create')">新增商品</el-button>
    </div>

    <FilterBar @search="fetchData" @reset="handleReset">
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 130px">
          <el-option v-for="(v, k) in PRODUCT_STATUS" :key="k" :label="v.label" :value="k" />
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
      <el-table-column prop="price" label="价格" width="100">
        <template #default="{ row }">{{ Number(row.price).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="stock" label="库存" width="80" />
      <el-table-column prop="salesCount" label="销量" width="80" />
      <el-table-column prop="pickupOnly" label="仅自提" width="80">
        <template #default="{ row }">
          <el-tag size="small" :type="row.pickupOnly ? 'warning' : 'success'">{{ row.pickupOnly ? '是' : '否' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="PRODUCT_STATUS[row.status as ProductStatusType]?.color || 'info'">
            {{ PRODUCT_STATUS[row.status as ProductStatusType]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sort" label="排序" width="70" />
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="openEditDialog(row)" :disabled="!userStore.hasPermission('product:item:update')">编辑</el-button>
          <el-button size="small" @click="openStockDialog(row)" :disabled="!userStore.hasPermission('product:stock:update')">库存</el-button>
          <el-button
            size="small" type="danger"
            v-if="isProductOnSale(row.status)"
            @click="handleDisable(row.id)"
            :disabled="!userStore.hasPermission('product:item:disable')"
          >下架</el-button>
          <el-button
            size="small" type="success"
            v-if="!isProductOnSale(row.status)"
            @click="handleEnable(row.id)"
            :disabled="!userStore.hasPermission('product:item:enable')"
          >上架</el-button>
        </template>
      </el-table-column>
    </DataTableShell>

    <!-- Create/Edit Dialog -->
    <el-dialog :title="dialogTitle" v-model="dialogVisible" width="500px" @close="resetForm">
      <el-form :model="form" :rules="rules" ref="formRef" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="分类ID" prop="categoryId">
          <el-input-number v-model="form.categoryId" :min="1" />
        </el-form-item>
        <el-form-item label="价格" prop="price">
          <el-input-number v-model="form.price" :precision="2" :min="0" />
        </el-form-item>
        <el-form-item label="仅自提" prop="pickupOnly">
          <el-switch v-model="form.pickupOnly" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number v-model="form.sort" :min="0" />
        </el-form-item>
        <el-form-item label="封面图" prop="coverUrl">
          <div class="pc-product__cover-row">
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
        <el-form-item label="商品展示轮播图" prop="imageUrls">
          <div class="pc-product__image-list">
            <el-upload
              v-model:file-list="carouselUploadFileList"
              action=""
              accept="image/jpeg,image/png,image/gif,image/webp"
              multiple
              :show-file-list="false"
              :http-request="uploadCarouselImage"
              :before-upload="beforeUploadCarouselImage"
              :limit="PRODUCT_DETAIL_CAROUSEL_LIMIT"
              :on-exceed="handleCarouselImageExceed"
              :disabled="carouselImageCount >= PRODUCT_DETAIL_CAROUSEL_LIMIT"
            >
              <el-button type="primary" :disabled="carouselImageCount >= PRODUCT_DETAIL_CAROUSEL_LIMIT">
                导入照片
              </el-button>
              <template #tip>
                <div class="pc-product__upload-tip">
                  最多导入 {{ PRODUCT_DETAIL_CAROUSEL_LIMIT }} 张，支持 JPG/PNG/GIF/WebP，单张不超过 10MB；展示在用户端商品详情页顶部。
                </div>
              </template>
            </el-upload>
            <div v-for="(_, index) in form.imageUrls" :key="index" class="pc-product__image-row">
              <el-input v-model="form.imageUrls[index]" placeholder="商品展示轮播图URL，如 /uploads/product-detail.png" />
              <el-button @click="removeCarouselImageUrl(index)" :disabled="form.imageUrls.length <= 1">删除</el-button>
            </div>
            <el-button
              type="primary"
              text
              @click="addCarouselImageUrl"
              :disabled="carouselImageCount >= PRODUCT_DETAIL_CAROUSEL_LIMIT"
            >
              添加图片URL
            </el-button>
          </div>
        </el-form-item>
        <el-form-item label="商品介绍图片" prop="detailImageUrls">
          <div class="pc-product__image-list">
            <el-upload
              v-model:file-list="detailUploadFileList"
              action=""
              accept="image/jpeg,image/png,image/gif,image/webp"
              multiple
              :show-file-list="false"
              :http-request="uploadDetailImage"
              :before-upload="beforeUploadDetailImage"
              :limit="CATALOG_DETAIL_IMAGE_LIMIT"
              :on-exceed="handleDetailImageExceed"
              :disabled="detailImageCount >= CATALOG_DETAIL_IMAGE_LIMIT"
            >
              <el-button type="primary" :disabled="detailImageCount >= CATALOG_DETAIL_IMAGE_LIMIT">
                导入照片
              </el-button>
              <template #tip>
                <div class="pc-product__upload-tip">
                  最多导入 {{ CATALOG_DETAIL_IMAGE_LIMIT }} 张，支持 JPG/PNG/GIF/WebP，单张不超过 10MB；插入到用户端商品详情页的商品介绍区域。
                </div>
              </template>
            </el-upload>
            <div v-for="(_, index) in form.detailImageUrls" :key="index" class="pc-product__image-row">
              <el-input v-model="form.detailImageUrls[index]" placeholder="商品介绍图片URL，如 /uploads/product-intro.png" />
              <el-button @click="removeDetailImageUrl(index)" :disabled="form.detailImageUrls.length <= 1">删除</el-button>
            </div>
            <el-button
              type="primary"
              text
              @click="addDetailImageUrl"
              :disabled="detailImageCount >= CATALOG_DETAIL_IMAGE_LIMIT"
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

    <!-- Stock Dialog -->
    <el-dialog title="调整库存" v-model="stockDialogVisible" width="350px">
      <el-form :model="stockForm" ref="stockFormRef" label-width="60px">
        <el-form-item label="库存" prop="stock" :rules="[{ required: true, message: '请输入', trigger: 'blur' }]">
          <el-input-number v-model="stockForm.stock" :min="0" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="stockDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="submitStock" :loading="stockLoading">确认</el-button>
      </template>
    </el-dialog>

    <!-- Disable Confirm Dialog -->
    <ActionConfirmDialog
      :visible="disableDialogVisible"
      title="下架商品"
      message="确定要下架此商品吗？下架后将不再展示给用户。"
      :danger="true"
      @confirm="executeDisable"
      @cancel="disableDialogVisible = false"
    />

    <!-- Enable Confirm Dialog -->
    <ActionConfirmDialog
      :visible="enableDialogVisible"
      title="上架商品"
      message="确定要重新上架此商品吗？上架后将重新展示给用户。"
      :danger="false"
      @confirm="executeEnable"
      @cancel="enableDialogVisible = false"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { getProductList, createProduct, updateProduct, disableProduct, enableProduct, updateProductStock } from '../../api/product'
import type { Product, ProductCreateParams } from '../../api/product'
import { uploadCatalogImage } from '../../api/upload'
import type { FormInstance, FormRules, UploadRawFile, UploadRequestOptions, UploadUserFile } from 'element-plus'
import { useUserStore } from '../../store/user'
import { showSuccess, showError } from '../../utils/feedback'
import {
  CATALOG_DETAIL_IMAGE_LIMIT,
  MAX_UPLOAD_SIZE,
  PRODUCT_DETAIL_CAROUSEL_LIMIT,
  PRODUCT_STATUS,
  isProductOnSale,
} from '../../types/status'
import type { ProductStatus as ProductStatusType } from '../../types/status'
import FilterBar from '../../components/FilterBar.vue'
import DataTableShell from '../../components/DataTableShell.vue'
import ActionConfirmDialog from '../../components/ActionConfirmDialog.vue'

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<Product[]>([])
const total = ref(0)
const queryParams = reactive({ page: 1, size: 10, status: '' })

// ─── Dialog ───
const dialogVisible = ref(false)
const dialogTitle = ref('')
const submitLoading = ref(false)
const formRef = ref<FormInstance>()
const isEdit = ref(false)
const currentId = ref<number>()
const coverUploadFileList = ref<UploadUserFile[]>([])
const carouselUploadFileList = ref<UploadUserFile[]>([])
const detailUploadFileList = ref<UploadUserFile[]>([])

type ProductForm = ProductCreateParams & { imageUrls: string[]; detailImageUrls: string[] }

const defaultForm: ProductForm = {
  categoryId: 1,
  name: '',
  price: 0,
  pickupOnly: true,
  sort: 0,
  coverUrl: '',
  imageUrls: [''],
  detailImageUrls: [''],
  description: '',
}
const form = ref<ProductForm>({ ...defaultForm })
const carouselImageCount = computed(() => normalizeImageUrls(form.value.imageUrls).length)
const detailImageCount = computed(() => normalizeImageUrls(form.value.detailImageUrls).length)
const carouselImageLimitMessage = `商品展示轮播图最多导入 ${PRODUCT_DETAIL_CAROUSEL_LIMIT} 张`
const detailImageLimitMessage = `商品介绍图片最多导入 ${CATALOG_DETAIL_IMAGE_LIMIT} 张`
const rules: FormRules = {
  name: [{ required: true, message: '请输入名称', trigger: 'blur' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
  categoryId: [{ required: true, message: '请输入分类ID', trigger: 'blur' }],
  pickupOnly: [{ required: true, message: '请选择', trigger: 'change' }],
}

// ─── Stock ───
const stockDialogVisible = ref(false)
const stockLoading = ref(false)
const stockFormRef = ref<FormInstance>()
const stockTargetId = ref(0)
const stockForm = reactive({ stock: 0 })

const openStockDialog = (row: Product) => {
  stockTargetId.value = row.id
  stockForm.stock = row.stock ?? 0
  stockDialogVisible.value = true
}

const submitStock = async () => {
  if (!stockFormRef.value) return
  await stockFormRef.value.validate(async (valid) => {
    if (!valid) return
    stockLoading.value = true
    try {
      await updateProductStock(stockTargetId.value, { stock: stockForm.stock })
      showSuccess('库存已更新')
      stockDialogVisible.value = false
      await fetchData()
    } catch (error) {
      showError(error instanceof Error ? error.message : '操作失败')
    } finally {
      stockLoading.value = false
    }
  })
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
    await disableProduct(disableTargetId.value)
    showSuccess('商品已下架')
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
    await enableProduct(enableTargetId.value)
    showSuccess('商品已上架')
    await fetchData()
  } catch (error) {
    showError(error instanceof Error ? error.message : '操作失败')
  }
}

// ─── Data Loading ───
const fetchData = async () => {
  loading.value = true
  try {
    const res = await getProductList(queryParams)
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

// ─── CRUD ───
const openCreateDialog = () => {
  isEdit.value = false
  dialogTitle.value = '新增商品'
  form.value = createDefaultForm()
  dialogVisible.value = true
}

const openEditDialog = (row: Product) => {
  isEdit.value = true
  currentId.value = row.id
  dialogTitle.value = '编辑商品'
  form.value = {
    categoryId: row.categoryId,
    name: row.name,
    coverUrl: row.coverUrl ?? undefined,
    price: row.price,
    description: row.description ?? undefined,
    pickupOnly: row.pickupOnly,
    imageUrls: row.imageUrls?.length ? [...row.imageUrls] : [''],
    detailImageUrls: row.detailImageUrls?.length ? [...row.detailImageUrls] : [''],
    sort: row.sort ?? undefined,
  }
  dialogVisible.value = true
}

const createDefaultForm = (): ProductForm => ({
  ...defaultForm,
  imageUrls: [...defaultForm.imageUrls],
  detailImageUrls: [...defaultForm.detailImageUrls],
})

type ImageListField = 'imageUrls' | 'detailImageUrls'

const normalizeImageUrls = (urls: string[] | undefined) => {
  return (urls ?? []).map((url) => url.trim()).filter(Boolean)
}

const imageLimitMessage = (label: string) => {
  return label === '商品介绍图片' ? detailImageLimitMessage : carouselImageLimitMessage
}

const updateImageList = (field: ImageListField, urls: string[]) => {
  form.value = {
    ...form.value,
    [field]: urls.length > 0 ? urls : [''],
  }
}

const addImageUrl = (field: ImageListField, limit: number, label: string) => {
  if (normalizeImageUrls(form.value[field]).length >= limit) {
    showError(imageLimitMessage(label))
    return
  }
  updateImageList(field, [...form.value[field], ''])
}

const removeImageUrl = (field: ImageListField, index: number) => {
  const next = [...form.value[field]]
  next.splice(index, 1)
  updateImageList(field, next)
}

const appendImageUrl = (field: ImageListField, url: string, limit: number, label: string) => {
  const current = normalizeImageUrls(form.value[field])
  if (current.length >= limit) {
    showError(imageLimitMessage(label))
    return false
  }
  const next = current.includes(url) ? current : [...current, url]
  updateImageList(field, next)
  return true
}

const addCarouselImageUrl = () => addImageUrl('imageUrls', PRODUCT_DETAIL_CAROUSEL_LIMIT, '商品展示轮播图')
const removeCarouselImageUrl = (index: number) => removeImageUrl('imageUrls', index)
const appendCarouselImageUrl = (url: string) => {
  return appendImageUrl('imageUrls', url, PRODUCT_DETAIL_CAROUSEL_LIMIT, '商品展示轮播图')
}

const addDetailImageUrl = () => addImageUrl('detailImageUrls', CATALOG_DETAIL_IMAGE_LIMIT, '商品介绍图片')
const removeDetailImageUrl = (index: number) => removeImageUrl('detailImageUrls', index)
const appendDetailImageUrl = (url: string) => {
  return appendImageUrl('detailImageUrls', url, CATALOG_DETAIL_IMAGE_LIMIT, '商品介绍图片')
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

const validateUploadFile = (file: UploadRawFile, count: number, limit: number, label: string) => {
  if (!validateImageFile(file)) {
    return false
  }
  if (count >= limit) {
    showError(imageLimitMessage(label))
    return false
  }
  return true
}

const beforeUploadCoverImage = (file: UploadRawFile) => {
  return validateImageFile(file)
}

const beforeUploadCarouselImage = (file: UploadRawFile) => {
  return validateUploadFile(file, carouselImageCount.value, PRODUCT_DETAIL_CAROUSEL_LIMIT, '商品展示轮播图')
}

const beforeUploadDetailImage = (file: UploadRawFile) => {
  return validateUploadFile(file, detailImageCount.value, CATALOG_DETAIL_IMAGE_LIMIT, '商品介绍图片')
}

const uploadImage = async (
  options: UploadRequestOptions,
  appendUrl: (url: string) => boolean,
  clearUploadList: () => void,
  successMessage = '照片已导入',
) => {
  try {
    const res = await uploadCatalogImage(options.file)
    const url = res.data?.url
    if (!url) throw new Error('上传失败')
    if (appendUrl(url)) {
      options.onSuccess?.(res)
      showSuccess(successMessage)
    }
  } catch (error) {
    const normalizedError = error instanceof Error ? error : new Error('上传失败')
    options.onError?.(Object.assign(normalizedError, { status: 0, method: 'POST', url: '/v1/upload' }))
    showError(normalizedError.message)
  } finally {
    clearUploadList()
  }
}

const uploadCoverImage = async (options: UploadRequestOptions) => {
  await uploadImage(options, (url) => {
    form.value = {
      ...form.value,
      coverUrl: url,
    }
    return true
  }, () => {
    coverUploadFileList.value = []
  }, '封面图已导入')
}

const uploadCarouselImage = async (options: UploadRequestOptions) => {
  await uploadImage(options, appendCarouselImageUrl, () => {
    carouselUploadFileList.value = []
  })
}

const uploadDetailImage = async (options: UploadRequestOptions) => {
  await uploadImage(options, appendDetailImageUrl, () => {
    detailUploadFileList.value = []
  })
}

const handleCarouselImageExceed = () => {
  showError(carouselImageLimitMessage)
}

const handleDetailImageExceed = () => {
  showError(detailImageLimitMessage)
}

const validateImageLimit = () => {
  if (normalizeImageUrls(form.value.imageUrls).length > PRODUCT_DETAIL_CAROUSEL_LIMIT) {
    showError(carouselImageLimitMessage)
    return false
  }
  if (normalizeImageUrls(form.value.detailImageUrls).length > CATALOG_DETAIL_IMAGE_LIMIT) {
    showError(detailImageLimitMessage)
    return false
  }
  return true
}

const buildPayload = (): ProductCreateParams => ({
  ...form.value,
  imageUrls: normalizeImageUrls(form.value.imageUrls),
  detailImageUrls: normalizeImageUrls(form.value.detailImageUrls),
})

const submitForm = async () => {
  if (!formRef.value) return
  await formRef.value.validate(async (valid) => {
    if (!valid) return
    if (!validateImageLimit()) return
    submitLoading.value = true
    try {
      const payload = buildPayload()
      if (isEdit.value && currentId.value) {
        await updateProduct(currentId.value, payload)
        showSuccess('商品已更新')
      } else {
        await createProduct(payload)
        showSuccess('商品已创建')
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

const resetForm = () => {
  formRef.value?.resetFields()
  coverUploadFileList.value = []
  carouselUploadFileList.value = []
  detailUploadFileList.value = []
}

onMounted(() => {
  fetchData()
})
</script>

<style scoped>
.pc-product {
  padding: 0;
}

.pc-product__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: var(--pc-spacing-lg);
}

.pc-product__title {
  margin: 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--pc-ink);
}

.pc-product__image-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.pc-product__cover-row {
  display: flex;
  gap: 8px;
  width: 100%;
}

.pc-product__cover-row .el-input {
  flex: 1;
}

.pc-product__image-row {
  display: flex;
  gap: 8px;
}

.pc-product__upload-tip {
  color: var(--pc-text-secondary, #909399);
  font-size: 12px;
  line-height: 1.5;
}
</style>
