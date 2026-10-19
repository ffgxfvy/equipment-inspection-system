import { defineStore } from 'pinia'
import { login as loginApi } from '@/api/auth'

const TOKEN_KEY = 'inspection_token'
const USER_KEY = 'inspection_user'

/**
 * 登录状态。
 *
 * token 存在 localStorage：刷新页面不用重新登录。
 * 注意 localStorage 可被 XSS 读取——更安全的做法是存 HttpOnly Cookie，
 * 但那需要后端配合改认证方式，本项目取"实现简单"这一侧。
 */
export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    user: JSON.parse(localStorage.getItem(USER_KEY) || 'null')
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.user?.role === 'ADMIN',
    displayName: (state) => state.user?.realName || state.user?.username || '未登录'
  },

  actions: {
    async login(form) {
      const data = await loginApi(form)
      this.token = data.token
      this.user = {
        userId: data.userId,
        username: data.username,
        realName: data.realName,
        role: data.role
      }
      localStorage.setItem(TOKEN_KEY, this.token)
      localStorage.setItem(USER_KEY, JSON.stringify(this.user))
      return data
    },

    logout() {
      this.token = ''
      this.user = null
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
    }
  }
})
