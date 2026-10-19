import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { useUserStore } from '@/store/user'

/**
 * 统一的 axios 封装。
 *
 * 后端所有接口都返回 { code, message, data } 结构，所以这里做三件事：
 *   1. 请求拦截：自动带上 token；
 *   2. 响应拦截：code === 200 才把 data 交给业务层，否则统一弹提示；
 *   3. 401 时清理登录态并跳回登录页。
 *
 * 这样各个页面里不需要写 try/catch 和"如果失败就提示"的重复代码。
 */
const service = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 15000
})

service.interceptors.request.use(
  (config) => {
    const userStore = useUserStore()
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

service.interceptors.response.use(
  (response) => {
    const body = response.data

    // 文件下载等场景返回的不是标准结构，直接透传
    if (body === null || typeof body !== 'object' || body.code === undefined) {
      return body
    }

    if (body.code === 200) {
      return body.data
    }

    if (body.code === 401) {
      const userStore = useUserStore()
      userStore.logout()
      ElMessage.error(body.message || '登录已过期，请重新登录')
      router.push('/login')
      return Promise.reject(new Error(body.message))
    }

    ElMessage.error(body.message || '请求失败')
    return Promise.reject(new Error(body.message || '请求失败'))
  },
  (error) => {
    const message = error.response
      ? `请求失败（${error.response.status}）`
      : '网络异常，请检查后端服务是否已启动'
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default service
