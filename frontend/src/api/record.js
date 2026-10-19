import service from '@/utils/request'

export const pageRecords = (params) => service.get('/records', { params })
export const getRecord = (id) => service.get(`/records/${id}`)
export const submitRecord = (data) => service.post('/records', data)
export const handleRecord = (id, data) => service.put(`/records/${id}/handle`, data)
export const deleteRecord = (id) => service.delete(`/records/${id}`)

/** 上传现场照片，返回 { relativeUrl } */
export const uploadPhoto = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return service.post('/files/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}
