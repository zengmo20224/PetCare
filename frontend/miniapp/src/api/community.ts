/**
 * Community / posts API.
 */

import { http } from './request'
import type { ApiResponse, PageResponse, PageParams } from '@/types/api'
import type { PostItem, PostDetail, CommentTreeNode, CommentFlatItem, TopicItem, TagItem } from '@/types/community'

/** List published posts (paginated, with optional topic, keyword, and tag filter) */
export function getPosts(params?: PageParams & { topicId?: string; keyword?: string; tag?: string }): Promise<ApiResponse<PageResponse<PostItem>>> {
  return http.get<PageResponse<PostItem>>('/api/v1/posts', params as Record<string, unknown>)
}

/** List all active topics (tags) */
export function getTopics(): Promise<ApiResponse<TopicItem[]>> {
  return http.get<TopicItem[]>('/api/v1/topics')
}

/** Search tags by keyword for autocomplete */
export function searchTags(keyword?: string, limit?: number): Promise<ApiResponse<TagItem[]>> {
  const params: Record<string, unknown> = {}
  if (keyword) params.keyword = keyword
  if (limit) params.limit = limit
  return http.get<TagItem[]>('/api/v1/tags', params)
}

/** List popular tags */
export function getPopularTags(limit?: number): Promise<ApiResponse<TagItem[]>> {
  const params: Record<string, unknown> = {}
  if (limit) params.limit = limit
  return http.get<TagItem[]>('/api/v1/tags/popular', params)
}

/** Get published post detail */
export function getPostDetail(id: string): Promise<ApiResponse<PostDetail>> {
  return http.get<PostDetail>(`/api/v1/posts/${id}`)
}

/** List published comments for a post (tree structure) */
export function getPostComments(postId: string): Promise<ApiResponse<CommentTreeNode[]>> {
  return http.get<CommentTreeNode[]>(`/api/v1/posts/${postId}/comments`)
}

/** List published comments for a post as a flat list (Douyin-style, no depth cap) */
export function getPostCommentsFlat(postId: string): Promise<ApiResponse<CommentFlatItem[]>> {
  return http.get<CommentFlatItem[]>(`/api/v1/posts/${postId}/comments/flat`)
}

/** Create a post (requires auth) */
export function createPost(data: {
  title: string
  content: string
  topicId?: string
  petId?: string
  tags?: string[]
  imageUrls?: string[]
}): Promise<ApiResponse<unknown>> {
  return http.post<unknown>('/api/v1/posts', data as any)
}

/** Create a comment (requires auth). parentId for replies. */
export function createComment(postId: string, content: string, parentId?: string): Promise<ApiResponse<unknown>> {
  const body: Record<string, unknown> = { content }
  if (parentId) body.parentId = parentId
  return http.post<unknown>(`/api/v1/posts/${postId}/comments`, body as any)
}

/** Delete a comment (requires auth, author only) */
export function deleteComment(postId: string, commentId: string): Promise<ApiResponse<void>> {
  return http.delete<void>(`/api/v1/posts/${postId}/comments/${commentId}`)
}

/** Like a comment (requires auth, idempotent) */
export function likeComment(postId: string, commentId: string): Promise<ApiResponse<void>> {
  return http.post<void>(`/api/v1/posts/${postId}/comments/${commentId}/like`)
}

/** Unlike a comment (requires auth, idempotent) */
export function unlikeComment(postId: string, commentId: string): Promise<ApiResponse<void>> {
  return http.delete<void>(`/api/v1/posts/${postId}/comments/${commentId}/like`)
}

/** List current user's posts (requires auth) */
export function getMyPosts(params?: PageParams): Promise<ApiResponse<PageResponse<PostItem>>> {
  return http.get<PageResponse<PostItem>>('/api/v1/user/community/posts', params as Record<string, unknown>)
}

/** List posts the current user has liked (requires auth) */
export function getMyLikedPosts(params?: PageParams): Promise<ApiResponse<PageResponse<PostItem>>> {
  return http.get<PageResponse<PostItem>>('/api/v1/user/community/liked-posts', params as Record<string, unknown>)
}

/** List posts the current user has favorited (requires auth) */
export function getMyFavoritedPosts(params?: PageParams): Promise<ApiResponse<PageResponse<PostItem>>> {
  return http.get<PageResponse<PostItem>>('/api/v1/user/community/favorited-posts', params as Record<string, unknown>)
}

/** Like a post (requires auth, idempotent) */
export function likePost(postId: string): Promise<ApiResponse<void>> {
  return http.post<void>(`/api/v1/posts/${postId}/like`)
}

/** Unlike a post (requires auth, idempotent) */
export function unlikePost(postId: string): Promise<ApiResponse<void>> {
  return http.delete<void>(`/api/v1/posts/${postId}/like`)
}

/** Favorite a post (requires auth, idempotent) */
export function favoritePost(postId: string): Promise<ApiResponse<void>> {
  return http.post<void>(`/api/v1/posts/${postId}/favorite`)
}

/** Unfavorite a post (requires auth, idempotent) */
export function unfavoritePost(postId: string): Promise<ApiResponse<void>> {
  return http.delete<void>(`/api/v1/posts/${postId}/favorite`)
}

/** Delete a post (requires auth, author only) */
export function deletePost(postId: string): Promise<ApiResponse<void>> {
  return http.delete<void>(`/api/v1/posts/${postId}`)
}
