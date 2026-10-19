<template>
  <div class="page-card">
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="任务编号 / 名称"
        style="width: 190px"
        clearable
        @keyup.enter="search"
      />
      <el-select v-model="query.status" placeholder="状态" style="width: 120px" clearable>
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-select v-model="query.priority" placeholder="优先级" style="width: 120px" clearable>
        <el-option v-for="p in priorityOptions" :key="p.value" :label="p.label" :value="p.value" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="计划开始"
        end-placeholder="计划结束"
        style="width: 240px"
      />
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
      <el-button :icon="Refresh" @click="reset">重置</el-button>

      <div class="spacer" />
      <el-button v-if="userStore.isAdmin" type="primary" :icon="Plus" @click="openForm()">
        新建任务
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe border size="small">
      <el-table-column prop="taskNo" label="任务编号" width="150" />
      <el-table-column prop="name" label="任务名称" min-width="180" show-overflow-tooltip />
      <el-table-column label="设备" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.equipmentCode">{{ row.equipmentCode }} {{ row.equipmentName }}</span>
          <span v-else class="muted">-</span>
        </template>
      </el-table-column>
      <el-table-column label="巡检员" width="90">
        <template #default="{ row }">
          <span :class="{ muted: !row.inspectorName }">{{ row.inspectorName || '未分派' }}</span>
        </template>
      </el-table-column>
      <el-table-column label="计划时间" width="150">
        <template #default="{ row }">
          <span :class="{ overdue: isOverdue(row) }">{{ formatTime(row.planTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="优先级" width="90" align="center">
        <template #default="{ row }">
          <el-tag :type="priorityTagType(row.priority)" size="small" effect="plain">
            {{ priorityLabel(row.priority) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="230" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="userStore.isAdmin && isOpen(row)"
            link type="primary" size="small"
            @click="openAssign(row)"
          >分派</el-button>
          <el-button
            v-if="isOpen(row)"
            link type="success" size="small"
            @click="openRecord(row)"
          >提交记录</el-button>
          <el-button
            v-if="isOpen(row)"
            link type="warning" size="small"
            @click="onChangeStatus(row)"
          >流转状态</el-button>
          <el-button
            v-if="userStore.isAdmin"
            link type="danger" size="small"
            @click="onDelete(row)"
          >删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="query.pageNum"
        v-model:page-size="query.pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @size-change="load"
        @current-change="load"
      />
    </div>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑任务' : '新建任务'" width="560px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-form-item label="任务名称" prop="name">
          <el-input v-model="form.name" placeholder="如 一号机组出口仪表月度巡检" />
        </el-form-item>
        <el-form-item label="设备" prop="equipmentId">
          <el-select
            v-model="form.equipmentId"
            filterable
            remote
            :remote-method="searchEquipment"
            :loading="equipmentLoading"
            placeholder="输入设备编号或名称搜索"
            style="width: 100%"
          >
            <el-option
              v-for="e in equipmentOptions"
              :key="e.id"
              :label="`${e.code} ${e.name}（${e.location}）`"
              :value="e.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="巡检员">
          <el-select v-model="form.inspectorId" clearable placeholder="可稍后分派" style="width: 100%">
            <el-option
              v-for="u in inspectors"
              :key="u.id"
              :label="`${u.realName}（${u.username}）`"
              :value="u.id"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="计划时间" prop="planTime">
          <el-date-picker
            v-model="form.planTime"
            type="datetime"
            value-format="YYYY-MM-DDTHH:mm:ss"
            placeholder="选择计划巡检时间"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-radio-group v-model="form.priority">
            <el-radio v-for="p in priorityOptions" :key="p.value" :value="p.value">
              {{ p.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 分派 -->
    <el-dialog v-model="assignVisible" title="分派巡检员" width="420px">
      <el-select v-model="assignInspectorId" placeholder="选择巡检员" style="width: 100%">
        <el-option
          v-for="u in inspectors"
          :key="u.id"
          :label="`${u.realName}（${u.username}）`"
          :value="u.id"
        />
      </el-select>
      <template #footer>
        <el-button @click="assignVisible = false">取消</el-button>
        <el-button type="primary" @click="onAssign">确认分派</el-button>
      </template>
    </el-dialog>

    <!-- 提交巡检记录 -->
    <RecordFormDialog v-model="recordVisible" :task="currentTask" @submitted="load" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh, Search } from '@element-plus/icons-vue'
import {
  assignTask, changeTaskStatus, createTask, deleteTask, pageTasks, updateTask
} from '@/api/task'
import { pageEquipments } from '@/api/equipment'
import { listInspectors } from '@/api/common'
import { useUserStore } from '@/store/user'
import RecordFormDialog from '@/components/RecordFormDialog.vue'

const userStore = useUserStore()

const statusOptions = [
  { label: '待执行', value: 'PENDING' },
  { label: '执行中', value: 'DOING' },
  { label: '已完成', value: 'DONE' },
  { label: '已取消', value: 'CANCELLED' }
]
const priorityOptions = [
  { label: '低', value: 'LOW' },
  { label: '普通', value: 'NORMAL' },
  { label: '高', value: 'HIGH' },
  { label: '紧急', value: 'URGENT' }
]

const statusMeta = {
  PENDING: { label: '待执行', type: 'warning' },
  DOING: { label: '执行中', type: 'primary' },
  DONE: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' }
}
const priorityMeta = {
  LOW: { label: '低', type: 'info' },
  NORMAL: { label: '普通', type: 'primary' },
  HIGH: { label: '高', type: 'warning' },
  URGENT: { label: '紧急', type: 'danger' }
}

const loading = ref(false)
const submitting = ref(false)
const rows = ref([])
const total = ref(0)
const inspectors = ref([])
const dateRange = ref([])

const query = reactive({
  keyword: '', status: '', priority: '',
  pageNum: 1, pageSize: 10
})

const formVisible = ref(false)
const formRef = ref(null)
const emptyForm = () => ({
  id: null, name: '', equipmentId: null, inspectorId: null,
  planTime: '', priority: 'NORMAL', remark: ''
})
const form = reactive(emptyForm())

const formRules = {
  name: [{ required: true, message: '请输入任务名称', trigger: 'blur' }],
  equipmentId: [{ required: true, message: '请选择设备', trigger: 'change' }],
  planTime: [{ required: true, message: '请选择计划时间', trigger: 'change' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }]
}

const equipmentOptions = ref([])
const equipmentLoading = ref(false)
const assignVisible = ref(false)
const assignInspectorId = ref(null)
const currentTask = ref(null)
const recordVisible = ref(false)

const statusLabel = (s) => statusMeta[s]?.label || s
const statusTagType = (s) => statusMeta[s]?.type || 'info'
const priorityLabel = (p) => priorityMeta[p]?.label || p
const priorityTagType = (p) => priorityMeta[p]?.type || 'info'
const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '-')
const isOpen = (row) => ['PENDING', 'DOING'].includes(row.status)
const isOverdue = (row) =>
  isOpen(row) && row.planTime && new Date(String(row.planTime).replace(' ', 'T')) < new Date()

const load = async () => {
  loading.value = true
  try {
    const params = { ...query }
    if (dateRange.value?.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const data = await pageTasks(params)
    rows.value = data.records || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

const search = () => {
  query.pageNum = 1
  load()
}

const reset = () => {
  Object.assign(query, { keyword: '', status: '', priority: '', pageNum: 1, pageSize: 10 })
  dateRange.value = []
  load()
}

const searchEquipment = async (keyword) => {
  if (!keyword) return
  equipmentLoading.value = true
  try {
    const data = await pageEquipments({ keyword, pageNum: 1, pageSize: 20 })
    equipmentOptions.value = data.records || []
  } finally {
    equipmentLoading.value = false
  }
}

const openForm = async (row) => {
  Object.assign(form, emptyForm())
  if (row) {
    Object.assign(form, row)
    // 编辑时把当前设备放进下拉选项，否则回显会是空白
    equipmentOptions.value = await pageEquipments({ keyword: row.equipmentCode, pageNum: 1, pageSize: 5 })
        .then((d) => d.records || [])
  }
  formVisible.value = true
  formRef.value?.clearValidate()
}

const onSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (form.id) {
      await updateTask(form.id, { ...form })
      ElMessage.success('修改成功')
    } else {
      await createTask({ ...form })
      ElMessage.success('创建成功')
    }
    formVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

const openAssign = (row) => {
  currentTask.value = row
  assignInspectorId.value = row.inspectorId
  assignVisible.value = true
}

const onAssign = async () => {
  if (!assignInspectorId.value) {
    ElMessage.warning('请选择巡检员')
    return
  }
  await assignTask(currentTask.value.id, assignInspectorId.value)
  ElMessage.success('分派成功')
  assignVisible.value = false
  load()
}

/**
 * 状态流转：先按后端允许的规则算出可选目标，再让用户选。
 * 把合法性判断放在前端只是为了让交互更顺，后端仍会再校验一次。
 */
const onChangeStatus = async (row) => {
  const allowed = {
    PENDING: ['DOING', 'DONE', 'CANCELLED'],
    DOING: ['DONE', 'CANCELLED']
  }[row.status] || []

  if (!allowed.length) {
    ElMessage.info('当前状态已结束，不能再流转')
    return
  }

  try {
    const { value } = await ElMessageBox.prompt(
      `当前状态：${statusLabel(row.status)}。可选目标：${allowed.join(' / ')}`,
      '流转任务状态',
      {
        inputPlaceholder: allowed.join(' 或 '),
        inputValidator: (v) => allowed.includes(v) || `只能填 ${allowed.join(' / ')}`,
        inputValue: allowed[0]
      }
    )
    await changeTaskStatus(row.id, value)
    ElMessage.success('状态已更新')
    load()
  } catch {
    // 取消
  }
}

const onDelete = async (row) => {
  try {
    await ElMessageBox.confirm(`确认删除任务「${row.taskNo}」？`, '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await deleteTask(row.id)
  ElMessage.success('删除成功')
  load()
}

const openRecord = (row) => {
  currentTask.value = row
  recordVisible.value = true
}

onMounted(async () => {
  load()
  if (userStore.isAdmin) {
    inspectors.value = await listInspectors()
  }
})
</script>

<style scoped>
.muted { color: #c0c4cc; }
.overdue { color: #f56c6c; font-weight: 600; }
</style>
