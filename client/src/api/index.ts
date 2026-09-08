/**
 * 后端 API 客户端
 * BaseURL 走 vite proxy：/api → http://localhost:8080
 */
import { useUserStore } from '@/stores/user';

const BASE = '/api/v1';

export interface R<T> {
  code: number;
  message: string;
  data?: T;
  traceId?: string;
}

export interface LoginRequest {
  username: string;
  password: string;
  tenantCode?: string;
}

export interface LoginResponse {
  token: string;
  tokenType: string;
  expiresInSeconds: number;
  user: {
    id: string;
    username: string;
    displayName: string;
    avatar: string | null;
    tenantId: number;
  };
}

export interface AgentVo {
  id: string;
  slug: string;
  name: string;
  avatar: string | null;
  description: string | null;
}

export interface SessionVo {
  id: string;
  title: string;
  agentId: number | null;
  status: number;
  lastMessageAt: string | null;
  createdAt: string;
}

export interface CitationVo {
  id: string;
  sourceType: number;
  title: string;
  url: string | null;
  snippet: string | null;
}

export interface MessageVo {
  id: string;
  role: number;
  content: string;
  tokenOutput: number | null;
  createdAt: string;
  citations: CitationVo[];
}

export interface QuotaVo {
  totalQuota: number;
  usedQuota: number;
  remainingQuota: number;
  memberLevel: number;
  preferredModel: string;
}

export interface MemberPlanVo {
  currentLevel: number;
  currentLevelName: string;
  tokenPacks: Array<{ code: string; name: string; tokens: number; price: number }>;
  memberPlans: Array<{ code: string; name: string; level: number; tokens: number; price: number; benefits: string[] }>;
}

export interface RechargeOrderVo {
  id: string;
  type: number;
  typeName: string;
  productName: string;
  tokenAmount: number;
  amount: number;
  status: number;
  statusName: string;
  createdAt: string;
}

async function request<T>(path: string, options: UniApp.RequestOptions = {}): Promise<R<T>> {
  const userStore = useUserStore();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(options.header as Record<string, string> | undefined)
  };
  if (userStore.token) {
    headers['Authorization'] = `Bearer ${userStore.token}`;
  }
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE + path,
      method: (options.method as 'GET' | 'POST' | 'PUT' | 'DELETE') || 'GET',
      data: options.data,
      header: headers,
      success: (res) => {
        // 401 自动跳登录
        if (res.statusCode === 401) {
          userStore.logout();
          reject(new Error('未登录或登录已过期'));
          return;
        }
        resolve(res.data as R<T>);
      },
      fail: (err) => reject(new Error(err.errMsg || 'network error'))
    });
  });
}

export const authApi = {
  sso: (req: LoginRequest) => request<LoginResponse>('/auth/sso', {
    method: 'POST',
    data: req
  })
};

export const agentApi = {
  list: () => request<AgentVo[]>('/agents')
};

export const sessionApi = {
  create: (data: { agentId?: number; title?: string }) => request<SessionVo>('/sessions', {
    method: 'POST',
    data
  }),
  list: (page = 1, size = 20) => request<SessionVo[]>('/sessions?page=' + page + '&size=' + size),
  detail: (id: string) => request<SessionVo>('/sessions/' + id),
  rename: (id: string, title: string) => request<SessionVo>('/sessions/' + id + '/rename', {
    method: 'PUT',
    data: { title }
  }),
  remove: (id: string) => request<boolean>('/sessions/' + id, { method: 'DELETE' })
};

export const messageApi = {
  list: (sessionId: string) => request<MessageVo[]>('/messages/session/' + sessionId)
};

export interface BillVo {
  id: string;
  sessionId: number;
  messageId: number | null;
  model: string;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  costAmount: number;
  createdAt: string;
}

export interface LogVo {
  time: string;
  action: string;
  status: string;
  statusText: string;
}

export const ledgerApi = {
  quota: () => request<QuotaVo>('/ledger/quota'),
  bills: (page = 1, size = 50) => request<BillVo[]>('/ledger/bills?page=' + page + '&size=' + size),
  logs: (page = 1, size = 50) => request<LogVo[]>('/ledger/logs?page=' + page + '&size=' + size),
  feedback: (type: number, content: string, messageId?: string) => request<{ id: string; status: string }>('/ledger/feedback', {
    method: 'POST',
    data: { type, content, messageId }
  })
};

export const membershipApi = {
  plans: () => request<MemberPlanVo>('/membership/plans'),
  recharge: (productCode: string) => request<RechargeOrderVo>('/membership/recharge', {
    method: 'POST',
    data: { productCode }
  }),
  upgrade: (productCode: string) => request<RechargeOrderVo>('/membership/upgrade', {
    method: 'POST',
    data: { productCode }
  }),
  switchModel: (model: string) => request<{ model: string; isFree: boolean }>('/membership/model', {
    method: 'POST',
    data: { model }
  }),
  orders: (page = 1, size = 20) => request<RechargeOrderVo[]>('/membership/orders?page=' + page + '&size=' + size)
};

/**
 * SSE 流式聊天
 * 使用 fetch + ReadableStream 读取 SSE 事件
 */
export async function streamChat(
  sessionId: string,
  content: string,
  onEvent: (event: {
    type: string;
    content?: string;
    tool_name?: string;
    result?: string;
    tokens_used?: number;
    input_tokens?: number;
    output_tokens?: number;
    message?: string;
    reason?: string;
    ticket_id?: number;
    citations?: Array<{ sourceType: number; title: string; url?: string; snippet?: string }>;
  }) => void
): Promise<void> {
  const userStore = useUserStore();
  const resp = await fetch(`/api/v1/sessions/${sessionId}/chat`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${userStore.token}`,
    },
    body: JSON.stringify({ content }),
  });

  if (!resp.ok) {
    throw new Error(`HTTP ${resp.status}`);
  }

  const reader = resp.body!.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });

    const lines = buffer.split('\n');
    buffer = lines.pop() || '';

    for (const line of lines) {
      if (line.startsWith('data:')) {
        const json = line.slice(5).trim();
        if (!json || json === '[DONE]') continue;
        try {
          const event = JSON.parse(json);
          onEvent(event);
        } catch { /* ignore parse errors */ }
      }
    }
  }
}

export const skillApi = {
  generate: (data: { skillName: string; topic: string; docType?: string; wordCount?: string }) =>
    request<{ result: string; tokens: number }>('/skills/generate', {
      method: 'POST',
      data
    })
};

export interface TodoVo {
  id: string;
  source: 'approval_ticket' | 'skill_form';
  status: number;
  statusText: string;
  title: string;
  summary: string;
  sourceLabel: string;
  icon: string;
  iconBg: string;
  applicantName: string | null;
  sessionId: number | null;
  sessionIdStr: string | null;
  toolId: number | null;
  submittedAt: string;
}

export const todoApi = {
  list: (type: 'pending' | 'applied' | 'all' = 'pending') =>
    request<TodoVo[]>(`/todos?type=${type}`),
  count: () => request<{ pending: number }>('/todos/count')
};
