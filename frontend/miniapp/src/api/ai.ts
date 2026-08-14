/**
 * AI conversation API. Requires authentication (user JWT).
 * D-004 修订（2026-07-21）：用户端 AI 客服已激活。
 */

import { http } from './request'
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
