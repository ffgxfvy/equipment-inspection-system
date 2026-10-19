import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

/**
 * 路由表。
 *
 * 除了 /login，其余页面都挂在 Layout 下（带侧边栏与顶栏）。
 * meta.title 用于侧边栏与面包屑，meta.roles 为空表示所有登录用户可见。
 */
const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: () => import('@/layout/index.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/Dashboard.vue'),
        meta: { title: '首页看板', icon: 'DataLine' }
      },
      {
        path: 'equipment',
        name: 'Equipment',
        component: () => import('@/views/Equipment.vue'),
        meta: { title: '设备台账', icon: 'Files' }
      },
      {
        path: 'task',
        name: 'Task',
        component: () => import('@/views/Task.vue'),
        meta: { title: '巡检任务', icon: 'Calendar' }
      },
      {
        path: 'record',
        name: 'Record',
        component: () => import('@/views/Record.vue'),
        meta: { title: '巡检记录', icon: 'Document' }
      }
    ]
  },
  {
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 全局前置守卫：未登录一律回登录页
router.beforeEach((to) => {
  const userStore = useUserStore()
  document.title = to.meta.title
    ? `${to.meta.title} - 工业设备巡检管理系统`
    : '工业设备巡检管理系统'

  if (to.meta.public) {
    // 已登录还去登录页，直接送回首页
    return userStore.isLoggedIn ? '/dashboard' : true
  }
  if (!userStore.isLoggedIn) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  return true
})

export default router
