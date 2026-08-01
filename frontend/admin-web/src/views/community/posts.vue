<template>
  <div class="pc-community-posts">
    <h2 class="pc-community-posts__title">帖子管理</h2>

    <FilterBar @search="fetchData" @reset="handleReset">
      <el-form-item label="状态">
        <el-select v-model="queryParams.status" placeholder="全部状态" clearable style="width: 140px">
          <el-option v-for="(v, k) in POST_STATUS" :key="k" :label="v.label" :value="k" />
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
      <el-table-column prop="title" label="标题" width="180" show-overflow-tooltip />
      <el-table-column prop="content" label="内容" show-overflow-tooltip />
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="POST_STATUS[row.status as PostStatusType]?.color || 'info'">
            {{ POST_STATUS[row.status as PostStatusType]?.label || row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="viewCount" label="浏览" width="70" />
      <el-table-column prop="likeCount" label="点赞" width="70" />
      <el-table-column prop="commentCount" label="评论" width="70" />
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="220" fixed="right">
        <template #default="{ row }">
          <el-button size="small" v-if="row.status === 'PENDING_REVIEW'" type="success" @click="handleApprove(row.id)" :disabled="!userStore.hasPermission('community:post:approve')">通过</el-button>
          <el-button size="small" v-if="row.status === 'PENDING_REVIEW'" type="danger" @click="handleReject(row.id)" :disabled="!userStore.hasPermission('community:post:reject')">拒绝</el-button>
          <el-button size="small" v-if="['PUBLISHED','PENDING_REVIEW'].includes(row.status)" @click="handleHide(row.id)" :disabled="!userStore.hasPermission('community:post:hide')">隐藏</el-button>
          <el-button size="small" type="danger" v-if="row.status !== 'DELETED'" @click="handleDelete(row.id)" :disabled="!userStore.hasPermission('community:post:delete')">删除</el-button>
        </template>
      </el-table-column>
    </DataTableShell>

    <!--
      帖子操作弹窗：自 6913f7f 起，预约页用独立 el-dialog 替代 ActionConfirmDialog
      以规避其 emit('confirm') → pendingAction() 链路的时序缺陷（请求发不出）。
      本页沿用同一已验证模式，每个动作一个独立弹窗，直接调用对应 API。
    -->

    <!-- Approve Dialog -->
    <el-dialog title="审核通过" v-model="approveVisible" width="400px">
      <p style="margin:0;color:#606266;line-height:1.6;">确定通过此帖子吗？通过后帖子将公开可见。</p>
      <template #footer>
        <el-button @click="approveVisible = false">取消</el-button>
        <el-button type="success" @click="submitApprove" :loading="approveLoading">确认通过</el-button>
      </template>
    </el-dialog>

    <!-- Reject Dialog -->
    <el-dialog title="拒绝帖子" v-model="rejectVisible" width="400px">
      <p style="margin:0;color:#606266;line-height:1.6;">确定拒绝此帖子吗？拒绝后帖子将不会公开。</p>
      <template #footer>
        <el-button @click="rejectVisible = false">取消</el-button>
        <el-button type="danger" @click="submitReject" :loading="rejectLoading">确认拒绝</el-button>
      </template>
    </el-dialog>

    <!-- Hide Dialog -->
    <el-dialog title="隐藏帖子" v-model="hideVisible" width="400px">
      <p style="margin:0;color:#606266;line-height:1.6;">确定隐藏此帖子吗？隐藏后用户将不再看到该帖子。</p>
      <template #footer>
        <el-button @click="hideVisible = false">取消</el-button>
        <el-button type="primary" @click="submitHide" :loading="hideLoading">确认隐藏</el-button>
      </template>
    </el-dialog>

    <!-- Delete Dialog -->
    <el-dialog title="删除帖子" v-model="deleteVisible" width="400px">
      <p style="margin:0;color:#606266;line-height:1.6;">确定删除此帖子吗？此操作不可恢复。</p>
      <template #footer>
        <el-button @click="deleteVisible = false">取消</el-button>
        <el-button type="danger" @click="submitDelete" :loading="deleteLoading">确认删除</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { getPostList, approvePost, rejectPost, hidePost, deletePost } from '../../api/community'
import type { Post } from '../../api/community'
import { useUserStore } from '../../store/user'
import { showSuccess, showError } from '../../utils/feedback'
import { POST_STATUS } from '../../types/status'
import type { PostStatus as PostStatusType } from '../../types/status'
import FilterBar from '../../components/FilterBar.vue'
import DataTableShell from '../../components/DataTableShell.vue'
// ActionConfirmDialog 已不再用于帖子页（改用独立 el-dialog 避免 emit 时序问题）

const userStore = useUserStore()
const loading = ref(false)
const tableData = ref<Post[]>([])
const total = ref(0)
const queryParams = reactive({ page: 1, size: 10, status: '' })

// ─── Approve ───
const approveVisible = ref(false)
const approveLoading = ref(false)
const approveTargetId = ref(0)

const handleApprove = (id: number) => {
  approveTargetId.value = id
  approveVisible.value = true
}

const submitApprove = async () => {
  approveLoading.value = true
  try {
    await approvePost(approveTargetId.value)
    showSuccess('帖子已通过')
    approveVisible.value = false
    await fetchData()
  } catch (error) {
    showError(error instanceof Error ? error.message : '操作失败')
  } finally {
    approveLoading.value = false
  }
}

// ─── Reject ───
const rejectVisible = ref(false)
const rejectLoading = ref(false)
const rejectTargetId = ref(0)

const handleReject = (id: number) => {
  rejectTargetId.value = id
  rejectVisible.value = true
}

const submitReject = async () => {
  rejectLoading.value = true
  try {
    await rejectPost(rejectTargetId.value)
    showSuccess('帖子已拒绝')
    rejectVisible.value = false
    await fetchData()
  } catch (error) {
    showError(error instanceof Error ? error.message : '操作失败')
  } finally {
    rejectLoading.value = false
  }
}

// ─── Hide ───
const hideVisible = ref(false)
const hideLoading = ref(false)
const hideTargetId = ref(0)

const handleHide = (id: number) => {
  hideTargetId.value = id
  hideVisible.value = true
}

const submitHide = async () => {
  hideLoading.value = true
  try {
    await hidePost(hideTargetId.value)
    showSuccess('帖子已隐藏')
    hideVisible.value = false
    await fetchData()
  } catch (error) {
    showError(error instanceof Error ? error.message : '操作失败')
  } finally {
    hideLoading.value = false
  }
}

// ─── Delete ───
const deleteVisible = ref(false)
const deleteLoading = ref(false)
const deleteTargetId = ref(0)

const handleDelete = (id: number) => {
  deleteTargetId.value = id
  deleteVisible.value = true
}

const submitDelete = async () => {
  deleteLoading.value = true
  try {
    await deletePost(deleteTargetId.value)
    showSuccess('帖子已删除')
    deleteVisible.value = false
    await fetchData()
  } catch (error) {
    showError(error instanceof Error ? error.message : '操作失败')
  } finally {
    deleteLoading.value = false
  }
}

// ─── Data Fetching ───
const fetchData = async () => {
  loading.value = true
  try {
    const res = await getPostList(queryParams)
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
.pc-community-posts {
  padding: 0;
}

.pc-community-posts__title {
  margin: 0 0 var(--pc-spacing-lg) 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--pc-ink);
}
</style>
