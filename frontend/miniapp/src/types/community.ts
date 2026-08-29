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
  /** 当前登录用户是否已点赞（后端按 token 回填，匿名请求为 false） */
  likedByMe?: boolean
  /** 当前登录用户是否已收藏（后端按 token 回填，匿名请求为 false） */
  favoritedByMe?: boolean
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
  /** 当前登录用户是否已点赞 */
  likedByMe: boolean
  /** 当前登录用户是否已收藏 */
  favoritedByMe: boolean
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
  /** 当前登录用户是否已点赞该评论 */
  likedByMe?: boolean
}

/** Tag as returned by tag search API */
export interface TagItem {
  id: string
  name: string
  usageCount: number
}
