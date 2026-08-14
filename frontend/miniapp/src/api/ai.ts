/**
 * AI conversation API. Requires authentication (user JWT).
 * D-004 修订（2026-07-21）：用户端 AI 客服已激活。
 * M8.5：新增 SSE 流式发送（H5 平台，POST + Authorization → chunk/done/error 事件）。
 */

import { http, getRequestBaseUrls } from './request'
import type { ApiResponse, PageResponse, PageParams } from '@/types/api'
import type {
  AiConversation,
  AiConversationCreateRequest,
  AiConversationType,
  AiMessage,
  AiMessageCreateRequest,
} from '@/types/ai'

/** Create a new AI conversation. Only CUSTOMER_SERVICE and PET_CHAT are allowed. */
export function createConversation(
  conversationType: AiConversationType,
  title?: string,
): Promise<ApiResponse<AiConversation>> {
  const payload: AiConversationCreateRequest = { conversationType, title }
  return http.post<AiConversation>('/api/v1/ai/conversations', payload as any)
}

/** List current user's conversations, paginated. */
export function listMyConversations(
  params?: PageParams,
): Promise<ApiResponse<PageResponse<AiConversation>>> {
  return http.get<PageResponse<AiConversation>>(
    '/api/v1/ai/conversations/my',
    params as Record<string, unknown>,
  )
}

/** Get conversation detail. Ownership verified server-side. */
export function getConversation(id: string): Promise<ApiResponse<AiConversation>> {
  return http.get<AiConversation>(`/api/v1/ai/conversations/${id}`)
}

/** Send a message and receive the AI assistant reply. */
export function sendMessage(
  conversationId: string,
  content: string,
): Promise<ApiResponse<AiMessage>> {
  const payload: AiMessageCreateRequest = { content }
  return http.post<AiMessage>(
    `/api/v1/ai/conversations/${conversationId}/messages`,
    payload as any,
  )
}

/** List messages in a conversation (oldest-first), paginated. */
export function listMessages(
  conversationId: string,
  params?: PageParams,
): Promise<ApiResponse<PageResponse<AiMessage>>> {
  return http.get<PageResponse<AiMessage>>(
    `/api/v1/ai/conversations/${conversationId}/messages`,
    params as Record<string, unknown>,
  )
}

/**
 * M8.3 社区发帖助手：生成帖子草稿（仅草稿，用户确认后自行发布）。
 * 服务端结合用户宠物档案个性化（agent-enabled 时）。
 */
export function generatePostDraft(payload: {
  event: string
  petName?: string
  petType?: string
  tone?: string
  originalText?: string
}): Promise<ApiResponse<{ isDraft: boolean; suggestedText: string }>> {
  return http.post<{ isDraft: boolean; suggestedText: string }>(
    '/api/v1/ai/post-assistant/generate',
    payload as any,
  )
}

/** SSE 流式回调契约。 */
export interface AiStreamHandlers {
  onChunk: (text: string) => void
  onDone: () => void
  onError: (message: string) => void
}

/** 当前平台是否支持 SSE 流式（编译期常量：H5 支持，小程序走同步降级）。 */
export const AI_STREAM_SUPPORTED: boolean =
  // #ifdef H5
  true
  // #endif
  // #ifndef H5
  false
  // #endif

/**
 * M8.5：SSE 流式发送消息（打字机效果）。仅 H5 平台实现
 * （fetch + ReadableStream；后端为 POST + Authorization 的 TEXT_EVENT_STREAM，
 * EventSource 不适用）。调用前用 {@link AI_STREAM_SUPPORTED} 判断，不支持走同步 sendMessage。
 *
 * 事件协议（AiConversationStreamController）：chunk=文本块、done=[DONE]、error=降级文案。
 */
export async function sendMessageStream(
  conversationId: string,
  content: string,
  handlers: AiStreamHandlers,
): Promise<void> {
  const base = getRequestBaseUrls()[0] || ''
  const token = typeof uni !== 'undefined' ? uni.getStorageSync('user_token') : ''
  let settled = false
  const finish = (fn: () => void) => {
    if (!settled) {
      settled = true
      fn()
    }
  }
  try {
    const res = await fetch(`${base}/api/v1/ai/conversations/${conversationId}/messages/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({ content }),
    })
    if (!res.ok || !res.body) {
      finish(() => handlers.onError(`请求失败（${res.status}）`))
      return
    }
    const reader = res.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    for (;;) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      // SSE 帧以空行分隔：event:xxx\ndata:yyy\n\n
      let sep = buffer.indexOf('\n\n')
      while (sep >= 0) {
        const frame = buffer.slice(0, sep)
        buffer = buffer.slice(sep + 2)
        handleSseFrame(frame, handlers)
        sep = buffer.indexOf('\n\n')
      }
    }
    finish(() => handlers.onDone())
  } catch {
    finish(() => handlers.onError('连接中断，请稍后重试'))
  }
}

/** 解析单帧 SSE（event/data 行；data 多行时按协议 join('\n')）。 */
function handleSseFrame(frame: string, handlers: AiStreamHandlers): void {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of frame.split('\n')) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).replace(/^ /, ''))
    }
  }
  const data = dataLines.join('\n')
  if (event === 'chunk') {
    if (data) handlers.onChunk(data)
  } else if (event === 'error') {
    handlers.onError(data || 'AI 暂时无法回复')
  }
  // done 事件由流结束时统一回调 onDone
}
