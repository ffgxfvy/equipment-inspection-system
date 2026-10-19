<template>
  <div class="page-card">
    <div class="toolbar">
      <el-select
        v-model="query.result"
        placeholder="巡检结论"
        style="width: 130px"
        clearable
      >
        <el-option label="正常" value="NORMAL" />
        <el-option label="异常" value="ABNORMAL" />
      </el-select>
      <el-select
        v-model="query.handleStatus"
        placeholder="处理状态"
        style="width: 130px"
        clearable
      >
        <el-option label="待处理" value="UNHANDLED" />
        <el-option label="处理中" value="HANDLING" />
        <el-option label="已处理" value="HANDLED" />
      </el-select>
      <el-date-picker
        v-model="dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="开始日期"
        end-placeholder="结束日期"
        style="width: 240px"
      />
      <el-button type="primary" :icon="Search" @click="search">查询</el-button>
      <el-button :icon="Refresh" @click="reset">重置</el-button>
    </div>

    <el-table v-loading="loading" :data="rows" stripe border size="small">
      <el-table-column label="设备" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">
          <span v-if="row.equipmentCode">{{ row.equipmentCode }} {{ row.equipmentName }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="inspectorName" label="巡检员" width="90" />
      <el-table-column label="巡检时间" width="150">
        <template #default="{ row }">{{ formatTime(row.inspectTime) }}</template>
      </el-table-column>
      <el-table-column prop="temperature" label="温度" width="80" align="right">
        <template #default="{ row }">{{ row.temperature ?? '-' }}</template>
      </el-table-column>
      <el-table-column prop="pressure" label="压力" width="80" align="right">
        <template #default="{ row }">{{ row.pressure ?? '-' }}</template>
      </el-table-column>
      <el-table-column label="结论" width="80">
        <template #default="{ row }">
          <el-tag :type="row.result === 'ABNORMAL' ? 'danger' : 'success'" size="small">
            {{ row.result === 'ABNORMAL' ? '异常' : '正常' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="abnormalDesc" label="异常描述/备注" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <span :class="{ 'abnormal-text': row.result === 'ABNORMAL' }">
            {{ row.abnormalDesc || row.remark || '-' }}
          </span>
        </template>
      </el-table-column>
      <el-table-column label="处理状态" width="100">
        <template #default="{ row }">
          <el-tag v-if="row.result === 'ABNORMAL'" :type="handleTagType(row.handleStatus)" size="small">
            {{ handleLabel(row.handleStatus) }}
          </el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="照片" width="70" align="center">
        <template #default="{ row }">
          <el-image
            v-if="row.photoUrl"
            :src="row.photoUrl"
            :preview-src-list="[row.photoUrl]"
            preview-teleported
            fit="cover"
            style="width: 36px; height: 36px; border-radius: 4px"
          />
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button
            v-if="row.result === 'ABNORMAL' && row.handleStatus !== 'HANDLED'"
            link type="primary" size="small"
            @click="openHandle(row)"
          >处理</el-button>
          <el-button link type="danger" size="small" @click="onDelete(row)">删除</el-button>
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

    <!-- 异常处理弹窗 -->
    <el-dialog v-model="handleVisible" title="处理异常" width="520px">
      <el-descriptions v-if="current" :column="1" border size="small" style="margin-bottom: 14px">
        <el-descriptions-item label="设备">
          {{ current.equipmentCode }} {{ current.equipmentName }}
        </el-descriptions-item>
        <el-descriptions-item label="异常描述">{{ current.abnormalDesc }}</el-descriptions-item>
      </el-descriptions>

      <el-form ref="handleFormRef" :model="handleForm" :rules="handleRules" label-width="90px">
        <el-form-item label="处理状态" prop="handleStatus">
          <el-radio-group v-model="handleForm.handleStatus">
            <el-radio value="HANDLING">处理中</el-radio>
            <el-radio value="HANDLED">已处理</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理说明" prop="handleRemark">
          <el-input
            v-model="handleForm.handleRemark"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :placeholder="handleForm.handleStatus === 'HANDLED'
              ? '必填：说明处理措施与结果'
              : '选填：说明当前处理进度'"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="handleVisible = false">取消</el-button>
        <el-button type="primary" @click="onHandle">确认</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Refresh, Search } from '@element-plus/icons-vue'
import { deleteRecord, handleRecord, pageRecords } from '@/api/record'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const dateRange = ref([])

const query = reactive({
  result: '', handleStatus: '',
  pageNum: 1, pageSize: 10
})

const handleVisible = ref(false)
const handleFormRef = ref(null)
const current = ref(null)
const handleForm = reactive({ handleStatus: 'HANDLED', handleRemark: '' })

const handleRules = {
  handleStatus: [{ required: true, message: '请选择处理状态', trigger: 'change' }],
  handleRemark: [
    {
      validator: (rule, value, callback) => {
        if (handleForm.handleStatus === 'HANDLED' && !value) {
          callback(new Error('标记为已处理时必须填写处理说明'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

const handleMeta = {
  UNHANDLED: { label: '待处理', type: 'danger' },
  HANDLING: { label: '处理中', type: 'warning' },
  HANDLED: { label: '已处理', type: 'success' }
}

const handleLabel = (s) => handleMeta[s]?.label || s
const handleTagType = (s) => handleMeta[s]?.type || 'info'
const formatTime = (t) => (t ? String(t).replace('T', ' ').slice(0, 16) : '-')

const load = async () => {
  loading.value = true
  try {
    const params = { ...query }
    if (dateRange.value?.length === 2) {
      params.startDate = dateRange.value[0]
      params.endDate = dateRange.value[1]
    }
    const data = await pageRecords(params)
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
  Object.assign(query, { result: '', handleStatus: '', pageNum: 1, pageSize: 10 })
  dateRange.value = []
  load()
}

const openHandle = (row) => {
  current.value = row
  handleForm.handleStatus = row.handleStatus === 'UNHANDLED' ? 'HANDLING' : 'HANDLED'
  handleForm.handleRemark = ''
  handleVisible.value = true
  handleFormRef.value?.clearValidate()
}

const onHandle = async () => {
  const valid = await handleFormRef.value.validate().catch(() => false)
  if (!valid) return

  await handleRecord(current.value.id, { ...handleForm })
  ElMessage.success('处理成功')
  handleVisible.value = false
  load()
}

const onDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确认删除该巡检记录？', '删除确认', { type: 'warning' })
  } catch {
    return
  }
  await deleteRecord(row.id)
  ElMessage.success('删除成功')
  load()
}

onMounted(load)
</script>

<style scoped>
.muted { color: #c0c4cc; }
.abnormal-text { color: #f56c6c; }
</style>
