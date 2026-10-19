<template>
  <el-dialog
    :model-value="modelValue"
    title="提交巡检记录"
    width="620px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <el-alert
      v-if="task"
      type="info"
      :closable="false"
      show-icon
      style="margin-bottom: 14px"
    >
      {{ task.taskNo }} · {{ task.name }}
      <span v-if="task.equipmentCode">｜设备：{{ task.equipmentCode }} {{ task.equipmentName }}</span>
    </el-alert>

    <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
      <el-form-item label="巡检结论" prop="result">
        <el-radio-group v-model="form.result">
          <el-radio value="NORMAL">正常</el-radio>
          <el-radio value="ABNORMAL">异常</el-radio>
        </el-radio-group>
      </el-form-item>

      <!-- 异常描述：结论为异常时必填，且是后端也会校验的业务规则 -->
      <el-form-item
        v-if="form.result === 'ABNORMAL'"
        label="异常描述"
        prop="abnormalDesc"
      >
        <el-input
          v-model="form.abnormalDesc"
          type="textarea"
          :rows="3"
          maxlength="500"
          show-word-limit
          placeholder="请描述异常现象、实测值与偏差范围，例如：出口压力表读数 2.80 MPa，与 DCS 显示 1.95 MPa 偏差 0.85 MPa"
        />
      </el-form-item>

      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="实测温度">
            <el-input-number v-model="form.temperature" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="实测压力">
            <el-input-number v-model="form.pressure" :min="0" :precision="2" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>

      <el-form-item label="现场照片">
        <el-upload
          :show-file-list="false"
          :before-upload="beforeUpload"
          :http-request="doUpload"
          accept="image/*"
        >
          <el-button :icon="Upload" :loading="uploading">上传照片</el-button>
        </el-upload>
        <div v-if="form.photoUrl" class="photo-preview">
          <el-image :src="form.photoUrl" fit="cover" style="width: 90px; height: 90px" />
          <el-button link type="danger" size="small" @click="form.photoUrl = ''">移除</el-button>
        </div>
      </el-form-item>

      <el-form-item label="备注">
        <el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" show-word-limit />
      </el-form-item>
    </el-form>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取消</el-button>
      <el-button type="primary" :loading="submitting" @click="onSubmit">提交</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Upload } from '@element-plus/icons-vue'
import { submitRecord, uploadPhoto } from '@/api/record'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  task: { type: Object, default: null }
})
const emit = defineEmits(['update:modelValue', 'submitted'])

const formRef = ref(null)
const submitting = ref(false)
const uploading = ref(false)

const emptyForm = () => ({
  taskId: null,
  equipmentId: null,
  result: 'NORMAL',
  abnormalDesc: '',
  temperature: null,
  pressure: null,
  photoUrl: '',
  remark: ''
})
const form = reactive(emptyForm())

const rules = {
  result: [{ required: true, message: '请选择巡检结论', trigger: 'change' }],
  abnormalDesc: [
    {
      validator: (rule, value, callback) => {
        if (form.result === 'ABNORMAL' && !value) {
          callback(new Error('结论为异常时必须填写异常描述'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

// 每次打开弹窗都重置表单，并把任务信息带进来
watch(
  () => props.modelValue,
  (visible) => {
    if (visible) {
      Object.assign(form, emptyForm())
      form.taskId = props.task?.id || null
      form.equipmentId = props.task?.equipmentId || null
      formRef.value?.clearValidate()
    }
  }
)

/** 前端先挡一道明显不合法的文件，减少无谓的上传流量 */
const beforeUpload = (file) => {
  if (file.size > 10 * 1024 * 1024) {
    ElMessage.error('图片不能超过 10 MB')
    return false
  }
  if (!file.type.startsWith('image/')) {
    ElMessage.error('只能上传图片')
    return false
  }
  return true
}

/** 用自定义上传，这样能拿到后端返回的相对路径塞进表单 */
const doUpload = async ({ file }) => {
  uploading.value = true
  try {
    const data = await uploadPhoto(file)
    form.photoUrl = data.relativeUrl
    ElMessage.success('上传成功')
  } finally {
    uploading.value = false
  }
}

const onSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  submitting.value = true
  try {
    await submitRecord({ ...form })
    ElMessage.success('提交成功')
    emit('update:modelValue', false)
    emit('submitted')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.photo-preview { display: flex; align-items: center; gap: 10px; margin-top: 8px; }
</style>
