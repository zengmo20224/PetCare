/**
 * Community-related types for the user-facing H5 app.
 * Backend source: com.petcare.community.dto.PublicPostSummaryResponse / PublicPostDetailResponse / PublicCommentResponse
 */

/** Topic (tag) as returned by topic list API */
export interface TopicItem {
  id: string
  name: string
  description: string | null
  sort: number
}

/** Post summary as returned by public list API */
export interface PostItem {
  id: string
  topicId: string
  title: string
  content: string
  viewCount: number
  likeCount: number
  commentCount: number
  favoriteCount: number
  publishTime: string
  createTime: string
  imageUrls: string[]
  tags: string[]
  authorName: string | null
  authorAvatar: string | null
}

/** Post detail as returned by public detail API */
export interface PostDetail {
  id: string
  topicId: string
  title: string
  content: string
  viewCount: number
  likeCount: number
  commentCount: number
  favoriteCount: number
  publishTime: string
  createTime: string
  imageUrls: string[]
  tags: string[]
  authorName: string | null
  authorAvatar: string | null
}

/** Public comment as returned by comment list API */
export interface CommentItem {
  id: string
  parentId: string | null
  content: string
  likeCount: number
  createTime: string
}

/** Comment tree node (top-level comment with nested replies) */
export interface CommentTreeNode {
  id: string
  parentId: string | null
  content: string
  likeCount: number
  createTime: string
  authorName: string | null
  authorAvatar: string | null
  replies: CommentTreeNode[]
}

/** Flat comment item (Douyin-style): author @ replyTo, no nesting */
export interface CommentFlatItem {
  id: string
  parentId: string | null
  content: string
  likeCount: number
  createTime: string
  authorName: string | null
  authorAvatar: string | null
  authorUserId: string | null
  replyToUserId: string | null
  replyToName: string | null
}

/** Tag as returned by tag search API */
export interface TagItem {
  id: string
  name: string
  usageCount: number
}
