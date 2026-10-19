import service from '@/utils/request'

export const login = (data) => service.post('/auth/login', data)
