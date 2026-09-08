import axios from 'axios'
import { ElMessage } from 'element-plus'

const api = axios.create({ baseURL: '/api/v1', timeout: 15000 })

api.interceptors.request.use((cfg) => {
  const t = localStorage.getItem('aioa_admin_token')
  if (t) cfg.headers.Authorization = `Bearer ${t}`
  return cfg
})

api.interceptors.response.use(
  (r) => r.data,
  (err) => {
    if (err?.response?.status === 401) {
      ElMessage.error('未登录或登录已过期')
      localStorage.removeItem('aioa_admin_token')
      location.hash = '#/login'
    } else {
      ElMessage.error(err?.response?.data?.message || err.message || '请求失败')
    }
    return Promise.reject(err)
  }
)

export interface R<T> { code: number; message: string; data?: T }

export const AuthAPI = {
  sso: (username: string, password: string, tenantCode = 'default') =>
    api.post<any, R<any>>('/auth/sso', { username, password, tenantCode })
}

export const AgentAPI = {
  list: () => api.get<any, R<any[]>>('/agents'),
}

export const TodoAPI = {
  pending: () => api.get<any, R<any[]>>('/todos?type=pending'),
  applied: () => api.get<any, R<any[]>>('/todos?type=applied'),
  count: () => api.get<any, R<{ pending: number }>>('/todos/count'),
}

export const ApprovalAPI = {
  listPending: () => api.get<any, R<any[]>>('/approvals/pending'),
  approve: (id: number, opinion: string) =>
    api.post<any, R<any>>(`/approvals/${id}/approve`, { opinion }),
  reject: (id: number, opinion: string) =>
    api.post<any, R<any>>(`/approvals/${id}/reject`, { opinion }),
}

export const LedgerAPI = {
  quota: () => api.get<any, R<any>>('/ledger/quota'),
  bills: (page = 1, size = 50) =>
    api.get<any, R<any[]>>(`/ledger/bills?page=${page}&size=${size}`),
  logs: (page = 1, size = 50) =>
    api.get<any, R<any[]>>(`/ledger/logs?page=${page}&size=${size}`),
}

export const SessionAPI = {
  list: (page = 1, size = 20) =>
    api.get<any, R<any[]>>(`/sessions?page=${page}&size=${size}`),
}

export default api