<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="brand">
        <el-icon :size="34" color="#409eff"><Monitor /></el-icon>
        <h2>工业设备巡检管理系统</h2>
        <p class="subtitle">Equipment Inspection Management System</p>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        size="large"
        @keyup.enter="onSubmit"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" clearable />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            placeholder="密码"
            :prefix-icon="Lock"
            show-password
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" @click="onSubmit">
            登 录
          </el-button>
        </el-form-item>
      </el-form>

      <el-divider>演示账号</el-divider>
      <div class="demo-accounts">
        <el-tag
          v-for="acc in demoAccounts"
          :key="acc.username"
          class="demo-tag"
          @click="fill(acc)"
        >
          {{ acc.label }}
        </el-tag>
      </div>
      <p class="hint">点击标签可自动填入</p>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref(null)
const loading = ref(false)
const form = reactive({ username: 'admin', password: 'admin123' })

const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

const demoAccounts = [
  { label: '管理员 admin', username: 'admin', password: 'admin123' },
  { label: '巡检员 张三', username: 'zhangsan', password: '123456' },
  { label: '巡检员 李四', username: 'lisi', password: '123456' }
]

const fill = (acc) => {
  form.username = acc.username
  form.password = acc.password
}

const onSubmit = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    await userStore.login({ ...form })
    ElMessage.success('登录成功')
    router.push(route.query.redirect || '/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1e3c72 0%, #2a5298 100%);
}

.login-card {
  width: 400px;
  padding: 8px 12px;
  border-radius: 10px;
}

.brand { text-align: center; margin-bottom: 22px; }
.brand h2 { margin: 10px 0 4px; font-size: 19px; }
.subtitle { margin: 0; color: #909399; font-size: 12px; letter-spacing: 0.5px; }

.submit-btn { width: 100%; }

.demo-accounts { display: flex; gap: 8px; justify-content: center; flex-wrap: wrap; }
.demo-tag { cursor: pointer; }

.hint { text-align: center; color: #c0c4cc; font-size: 12px; margin: 10px 0 0; }
</style>
