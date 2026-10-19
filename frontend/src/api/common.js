import service from '@/utils/request'

export const getDashboard = () => service.get('/dashboard')
export const listInspectors = () => service.get('/users/inspectors')
