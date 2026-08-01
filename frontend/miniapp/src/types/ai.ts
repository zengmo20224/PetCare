/**
 * AI conversation types matching backend DTOs.
 * All ids are strings — backend serializes snowflake ids via SnowflakeIdSerializer.
 */

export type AiConversationType = 'CUSTOMER_SERVICE' | 'PET_CHAT'

export interface AiConversation {
  id: string
  conversationType: AiConversationType
  title?: string
  createTime?: string
}

export interface AiMessage {
  id: string
  conversationId: string
  /** "user" | "assistant" */
  role: 'user' | 'assistant'
  content: string
  createTime?: string
}

export interface AiConversationCreateRequest {
  conversationType: AiConversationType
  title?: string
}

export interface AiMessageCreateRequest {
  content: string
}
