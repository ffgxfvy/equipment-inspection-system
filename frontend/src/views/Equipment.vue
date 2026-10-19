<template>
  <div class="page-card">
    <!-- 查询条件 -->
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="设备编号 / 名称"
        style="width: 200px"
        clearable
        @keyup.enter="search"
      />
      <el-select v-model="query.category" placeholder="类目" style="width: 130px" clearable>
        <el-option v-for="c in categories" :key="c" :label="c" :value="c" />
      </el-select>
      <el-select v-model="query.status" placeholder="状态" style="width: 120px" clearable>
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-checkbox v-model="query.overdueOnly">仅看到期未巡检</el-checkbox>
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
      <el-button :icon="Refresh" @click="reset">重置</el-button>

      <div class="spacer" />
      <!-- 导出的是「当前筛选结果」，文案里说明清楚，避免用户以为导的是全表 -->
      <el-button :icon="Download" :loading="exporting" @click="onExport">
        导出 Excel
      </el-button>
      <el-upload
        v-if="userStore.isAdmin"
        :show-file-list="false"
        accept=".xlsx"
        :before-upload="beforeImport"
        :http-request="onImport"
        style="display: inline-block; margin-left: 10px"
      >
        <el-button :icon="Upload" :loading="importing">导入 Excel</el-button>
      </el-upload>
      <el-button v-if="userStore.isAdmin" type="primary" :icon="Plus" @click="openForm()">
        新增设备
      </el-button>
    </div>

    <!-- 列表 -->
    <el-table v-loading="loading" :data="rows" stripe border size="small">
      <el-table-column prop="code" label="设备编号" width="110" />
      <el-table-column prop="name" label="设备名称" min-width="150" show-overflow-tooltip />
      <el-table-column prop="category" label="类目" width="90" />
      <el-table-column prop="location" label="安装位置" min-width="170" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="巡检周期" width="90" align="center">
        <template #default="{ row }">{{ row.inspectCycleDays }} 天</template>
      </el-table-column>
      <el-table-column label="下次巡检" width="150">
        <template #default="{ row }">
          <span :class="{ overdue: isOverdue(row) }">{{ formatTime(row.nextInspectTime) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="lastTemperature" label="温度" width="80" align="right">
        <template #default="{ row }">{{ row.lastTemperature ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="lastPressure" label="压力" width="80" align="right">
        <template #default="{ row }">{{ row.lastPressure ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="openHistory(row)">历史</el-button>
          <el-button
            v-if="userStore.isAdmin"
            link
            type="primary"
            size="small"
            @click="openForm(row)"
          >编辑</el-button>
          <el-button
            v-if="userStore.isAdmin"
            link
            type="danger"
            size="small"
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

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="formVisible" :title="form.id ? '编辑设备' : '新增设备'" width="620px">
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="设备编号" prop="code">
              <el-input v-model="form.code" placeholder="如 EQ-1016" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="设备名称" prop="name">
              <el-input v-model="form.name" placeholder="如 出口压力表" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="类目" prop="category">
              <el-input v-model="form.category" placeholder="压力表 / 温度计 / 阀门..." />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="状态" prop="status">
              <el-select v-model="form.status" style="width: 100%">
                <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="规格型号">
              <el-input v-model="form.model" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="生产厂商">
              <el-input v-model="form.manufacturer" />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="安装位置" prop="location">
              <el-input v-model="form.location" placeholder="如 一号压缩机房 1# 机组出口" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="巡检周期" prop="inspectCycleDays">
              <el-input-number v-model="form.inspectCycleDays" :min="1" :max="365" style="width: 100%" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="投运日期">
              <el-date-picker
                v-model="form.installDate"
                type="date"
                value-format="YYYY-MM-DD"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="24">
            <el-form-item label="备注">
              <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <template #footer>
        <el-button @click="formVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="onSubmit">保存</el-button>
      </template>
    </el-dialog>

    <!-- 导入结果 -->
    <el-dialog v-model="importVisible" title="导入结果" width="640px">
      <template v-if="importResult">
        <el-descriptions :column="3" border size="small">
          <el-descriptions-item label="解析行数">{{ importResult.total }}</el-descriptions-item>
          <el-descriptions-item label="成功">
            <span style="color: #67c23a; font-weight: 600">{{ importResult.successCount }}</span>
          </el-descriptions-item>
          <el-descriptions-item label="失败">
            <span :style="{ color: importResult.failCount ? '#f56c6c' : '#909399', fontWeight: 600 }">
              {{ importResult.failCount }}
            </span>
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="importResult.failCount" style="margin-top: 14px">
          <p style="margin: 0 0 8px; color: #f56c6c; font-size: 13px">
            以下行未导入，请修改后单独重新导入：
          </p>
          <el-scrollbar max-height="260px">
            <div
              v-for="(err, i) in importResult.errors"
              :key="i"
              class="import-error"
            >{{ err }}</div>
          </el-scrollbar>
        </div>
        <el-alert
          v-else
          type="success"
          :closable="false"
          show-icon
          style="margin-top: 14px"
          title="全部导入成功"
        />
      </template>
      <template #footer>
        <el-button type="primary" @click="importVisible = false">知道了</el-button>
      </template>
    </el-dialog>

    <!-- 历史记录抽屉 -->
    <el-drawer v-model="historyVisible" :title="`巡检历史 - ${historyTitle}`" size="620px">
      <el-timeline v-if="history.length">
        <el-timeline-item
          v-for="item in history"
          :key="item.id"
          :timestamp="formatTime(item.inspectTime)"
          :type="item.result === 'ABNORMAL' ? 'danger' : 'success'"
        >
          <div class="history-item">
            <el-tag :type="item.result === 'ABNORMAL' ? 'danger' : 'success'" size="small">
              {{ item.result === 'ABNORMAL' ? '异常' : '正常' }}
            </el-tag>
            <span class="inspector">{{ item.inspectorName }}</span>
            <span v-if="item.temperature !== null && item.temperature !== undefined">
              温度 {{ item.temperature }}
            </span>
            <span v-if="item.pressure !== null && item.pressure !== undefined">
              压力 {{ item.pressure }}
            </span>
          </div>
          <div v-if="item.abnormalDesc" class="abnormal-desc">{{ item.abnormalDesc }}</div>
          <div v-if="item.handleRemark" class="handle-remark">处理：{{ item.handleRemark }}</div>
        </el-timeline-item>
      </el-timeline>
      <el-empty v-else description="暂无巡检记录" />
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Download, Plus, Refresh, Search, Upload } from '@element-plus/icons-vue'
import {
  createEquipment, deleteEquipment, exportEquipments, getCategories,
  getEquipmentRecords, importEquipments, pageEquipments, updateEquipment
} from '@/api/equipment'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

const statusOptions = [
  { label: '运行', value: 'RUNNING' },
  { label: '停机', value: 'STOPPED' },
  { label: '故障', value: 'FAULT' },
  { label: '检修中', value: 'MAINTENANCE' }
]

const statusMeta = {
  RUNNING: { label: '运行', type: 'success' },
  STOPPED: { label: '停机', type: 'info' },
  FAULT: { label: '故障', type: 'danger' },
  MAINTENANCE: { label: '检修中', type: 'warning' }
}

const loading = ref(false)
const submitting = ref(false)
const exporting = ref(false)
const importing = ref(false)
const importResult = ref(null)
const importVisible = ref(false)
const rows = ref([])
const total = ref(0)
const categories = ref([])

const query = reactive({
  keyword: '', category: '', status: '', overdueOnly: false,
  pageNum: 1, pageSize: 10
})

const formVisible = ref(false)
const formRef = ref(null)
const emptyForm = () => ({
  id: null, code: '', name: '', category: '', model: '', manufacturer: '',
  location: '', status: 'RUNNING', inspectCycleDays: 30, installDate: '', remark: ''
})
const form = reactive(emptyForm())

const formRules = {
  code: [{ required: true, message: '请输入设备编号', trigger: 'blur' }],
  name: [{ required: true, message: '请输入设备名称', trigger: 'blur' }],
  category: [{ required: true, message: '请输入类目', trigger: 'blur' }],
  location: [{ required: true, message: '请输入安装位置', trigger: 'blur' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  inspectCycleDays: [{ required: true, message: '请输入巡检周期', trigger: 'blur' }]
}

const historyVisible = ref(false)
const history = ref([])
const historyTitle = ref('')

const statusLabel = (s) => statusMeta[s]?.label || s
const statusTagType = (s) => statusMeta[s]?.type || 'info'
const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '-')
const isOverdue = (row) =>
  !!row.nextInspectTime &&
  new Date(String(row.nextInspectTime).replace(' ', 'T')) < new Date() &&
  row.status !== 'STOPPED'

const load = async () => {
  loading.value = true
  try {
    const data = await pageEquipments(query)
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
  Object.assign(query, {
    keyword: '', category: '', status: '', overdueOnly: false, pageNum: 1, pageSize: 10
  })
  load()
}

const openForm = (row) => {
  Object.assign(form, emptyForm())
  if (row) Object.assign(form, row)
  formVisible.value = true
  formRef.value?.clearValidate()
}

const onSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    if (form.id) {
      await updateEquipment(form.id, { ...form })
      ElMessage.success('修改成功')
    } else {
      await createEquipment({ ...form })
      ElMessage.success('新增成功')
    }
    formVisible.value = false
    load()
    loadCategories()
  } finally {
    submitting.value = false
  }
}

/** 导出当前筛选条件下的设备台账 */
const onExport = async () => {
  exporting.value = true
  try {
    const blob = await exportEquipments(query)

    // 用 createObjectURL 生成本地下载链接——纯前端触发下载的标准做法，
    // 不需要后端做重定向，也不会污染浏览器历史。
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    const stamp = new Date().toISOString().slice(0, 10).replace(/-/g, '')
    link.href = url
    link.download = `设备台账_${stamp}.xlsx`
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)

    // 必须释放，否则每导出一次就泄漏一块内存（长会话下越来越卡）
    URL.revokeObjectURL(url)
    ElMessage.success('导出成功，请查看浏览器下载目录')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  } finally {
    exporting.value = false
  }
}

/** 上传前挡一道：只接受 .xlsx，单文件不超过 5 MB */
const beforeImport = (file) => {
  if (!file.name.toLowerCase().endsWith('.xlsx')) {
    ElMessage.error('只支持 .xlsx 格式（Excel 2007 及以上）')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('文件不能超过 5 MB')
    return false
  }
  return true
}

/** 自定义上传：拿到后端返回的导入报告后弹窗展示 */
const onImport = async ({ file }) => {
  importing.value = true
  try {
    const result = await importEquipments(file)
    importResult.value = result
    importVisible.value = true
    if (result.failCount === 0) {
      ElMessage.success(`导入完成：成功 ${result.successCount} 行`)
    } else {
      ElMessage.warning(`导入完成：成功 ${result.successCount} 行，失败 ${result.failCount} 行`)
    }
    load()
    loadCategories()
  } catch (e) {
    ElMessage.error(e.message || '导入失败')
  } finally {
    importing.value = false
  }
}

const onDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认删除设备「${row.code} ${row.name}」？该操作为逻辑删除，数据仍保留在数据库中。`,
      '删除确认', { type: 'warning' }
    )
  } catch {
    return
  }
  await deleteEquipment(row.id)
  ElMessage.success('删除成功')
  load()
}

const openHistory = async (row) => {
  historyTitle.value = `${row.code} ${row.name}`
  history.value = await getEquipmentRecords(row.id, 30)
  historyVisible.value = true
}

const loadCategories = async () => {
  categories.value = await getCategories()
}

onMounted(() => {
  load()
  loadCategories()
})
</script>

<style scoped>
.overdue { color: #f56c6c; font-weight: 600; }

.import-error {
  padding: 6px 10px;
  margin-bottom: 4px;
  font-size: 13px;
  color: #f56c6c;
  background: #fef0f0;
  border-radius: 4px;
  line-height: 1.5;
}
.history-item { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; font-size: 13px; }
.inspector { color: #909399; }
.abnormal-desc { margin-top: 6px; color: #f56c6c; font-size: 13px; }
.handle-remark { margin-top: 4px; color: #67c23a; font-size: 13px; }
</style>
