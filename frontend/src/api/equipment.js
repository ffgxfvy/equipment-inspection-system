import service from '@/utils/request'

export const pageEquipments = (params) => service.get('/equipments', { params })
export const getEquipment = (id) => service.get(`/equipments/${id}`)
export const getCategories = () => service.get('/equipments/categories')
export const getEquipmentRecords = (id, limit = 20) =>
  service.get(`/equipments/${id}/records`, { params: { limit } })
export const createEquipment = (data) => service.post('/equipments', data)
export const updateEquipment = (id, data) => service.put(`/equipments/${id}`, data)

/**
 * 导入设备台账。
 *
 * 用 FormData 传 multipart，字段名必须是 file（与后端 @RequestParam("file") 对应）。
 * 注意不要手动设置 Content-Type：浏览器会自动补上带 boundary 的 multipart 头，
 * 手动写死会导致后端解析失败。
 */
export const importEquipments = (file) => {
  const formData = new FormData()
  formData.append('file', file)
  return service.post('/equipments/import', formData)
}
export const deleteEquipment = (id) => service.delete(`/equipments/${id}`)

/**
 * 导出设备台账为 Excel。
 *
 * 两个坑这里都处理了：
 *  1. responseType 必须是 'blob' —— 否则 axios 会按 JSON 解析二进制内容，文件必然损坏；
 *  2. 后端出错时返回的是 JSON，但被 responseType:blob 包成了 Blob 对象，
 *     直接下载会得到一个内容是错误信息的"假 Excel"。所以这里判断 MIME 类型，
 *     是 JSON 就读出来抛错，让用户看到真正的原因（比如"没有权限"）。
 */
export const exportEquipments = async (params) => {
  const blob = await service.get('/equipments/export', { params, responseType: 'blob' })

  const type = blob?.type || ''
  if (type.includes('application/json') || type.includes('text/plain')) {
    const text = await blob.text()
    let message = '导出失败'
    try {
      message = JSON.parse(text).message || message
    } catch {
      // 不是 JSON 就保留默认提示
    }
    throw new Error(message)
  }
  return blob
}
