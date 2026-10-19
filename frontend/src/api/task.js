import service from '@/utils/request'

export const pageTasks = (params) => service.get('/tasks', { params })
export const getTask = (id) => service.get(`/tasks/${id}`)
export const createTask = (data) => service.post('/tasks', data)
export const updateTask = (id, data) => service.put(`/tasks/${id}`, data)
export const assignTask = (id, inspectorId) =>
  service.put(`/tasks/${id}/assign`, null, { params: { inspectorId } })
export const changeTaskStatus = (id, status) =>
  service.put(`/tasks/${id}/status`, null, { params: { status } })
export const deleteTask = (id) => service.delete(`/tasks/${id}`)
