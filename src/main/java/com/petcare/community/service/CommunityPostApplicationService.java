package com.petcare.community.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.common.pagination.PageResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.domain.CommunityContentType;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.dto.CommentCreateRequest;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.dto.CommentResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.dto.PostCreateRequest;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.dto.PostDetailResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.dto.PostResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.dto.PublicCommentFlatResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.dto.PublicCommentResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.dto.PublicCommentTreeResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.dto.PublicPostDetailResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.dto.PublicPostSummaryResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.dto.TagResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.dto.TopicResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.entity.CommentLike;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.entity.CommunityTag;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.entity.Post;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.entity.PostComment;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.entity.PostImage;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.entity.PostTagRel;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.entity.Topic;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.entity.PostLike;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.entity.PostFavorite;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.mapper.CommentLikeMapper;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.mapper.CommunityTagMapper;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.mapper.PostCommentMapper;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.mapper.PostFavoriteMapper;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.mapper.PostImageMapper;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.mapper.PostLikeMapper;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.mapper.PostMapper;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.mapper.PostTagRelMapper;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.community.mapper.TopicMapper;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.moderation.dto.ContentReviewResult;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.moderation.service.ContentModerationService;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.notification.service.NotificationService;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.user.entity.Pet;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.user.entity.User;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.user.mapper.PetMapper;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.user.service.UserService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service for community post and comment operations.
 * Handles creation with moderation, listing, and detail retrieval.
 */
@Service
public class CommunityPostApplicationService {

    private final PostMapper postMapper;
    private final PostCommentMapper commentMapper;
    private final PostImageMapper imageMapper;
    private final TopicMapper topicMapper;
    private final PetMapper petMapper;
    private final ContentModerationService moderationService;
    private final CommunityTagMapper tagMapper;
    private final PostTagRelMapper postTagRelMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final PostLikeMapper postLikeMapper;
    private final PostFavoriteMapper postFavoriteMapper;
    private final NotificationService notificationService;
    private final UserService userService;
    /** M8.3：AI 文本审核钩子（可选，agent-enabled=false 时为 null，发帖/评论不受影响）。 */
    private final com.petcare.ai.service.AiModerationReviewService aiModerationReviewService;

    private static final int MAX_TAGS_PER_POST = 3;

    public CommunityPostApplicationService(PostMapper postMapper,
                                            PostCommentMapper commentMapper,
                                            PostImageMapper imageMapper,
                                            TopicMapper topicMapper,
                                            PetMapper petMapper,
                                            ContentModerationService moderationService,
                                            CommunityTagMapper tagMapper,
                                            PostTagRelMapper postTagRelMapper,
                                            CommentLikeMapper commentLikeMapper,
                                            PostLikeMapper postLikeMapper,
                                            PostFavoriteMapper postFavoriteMapper,
                                            NotificationService notificationService,
                                            UserService userService,
                                            org.springframework.beans.factory.ObjectProvider<com.petcare.ai.service.AiModerationReviewService> aiModerationReviewServiceProvider) {
        this.postMapper = postMapper;
        this.commentMapper = commentMapper;
        this.imageMapper = imageMapper;
        this.topicMapper = topicMapper;
        this.petMapper = petMapper;
        this.moderationService = moderationService;
        this.tagMapper = tagMapper;
        this.postTagRelMapper = postTagRelMapper;
        this.commentLikeMapper = commentLikeMapper;
        this.postLikeMapper = postLikeMapper;
        this.postFavoriteMapper = postFavoriteMapper;
        this.notificationService = notificationService;
        this.userService = userService;
        this.aiModerationReviewService = aiModerationReviewServiceProvider.getIfUnique();
    }

    // ==================== Topic Queries ====================

    /**
     * Lists all active topics ordered by sort asc, createTime desc.
     */
    public List<TopicResponse> listTopics() {
        List<Topic> topics = topicMapper.selectList(
                new LambdaQueryWrapper<Topic>()
                        .eq(Topic::getStatus, "ACTIVE")
                        .eq(Topic::getDeleted, 0)
                        .orderByAsc(Topic::getSort)
                        .orderByDesc(Topic::getCreateTime)
        );
        return topics.stream()
                .map(t -> new TopicResponse(t.getId(), t.getName(), t.getDescription(),
                        t.getSort(), t.getCreateTime()))
                .toList();
    }

    /**
     * Gets a single active topic by ID.
     */
    public TopicResponse getTopic(Long id) {
        Topic topic = topicMapper.selectOne(
                new LambdaQueryWrapper<Topic>()
                        .eq(Topic::getId, id)
                        .eq(Topic::getStatus, "ACTIVE")
                        .eq(Topic::getDeleted, 0)
        );
        if (topic == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_TOPIC_NOT_FOUND, "话题不存在");
        }
        return new TopicResponse(topic.getId(), topic.getName(), topic.getDescription(),
                topic.getSort(), topic.getCreateTime());
    }

    // ==================== Post Operations ====================

    /**
     * Creates a new post with content moderation.
     * User identity comes from currentUserId parameter, not request body.
     */
    @Transactional
    public PostResponse createPost(Long currentUserId, PostCreateRequest request) {
        // Validate topic if provided
        if (request.topicId() != null) {
            Topic topic = topicMapper.selectOne(
                    new LambdaQueryWrapper<Topic>()
                            .eq(Topic::getId, request.topicId())
                            .eq(Topic::getStatus, "ACTIVE")
                            .eq(Topic::getDeleted, 0)
            );
            if (topic == null) {
                throw new BusinessException(ErrorCode.COMMUNITY_TOPIC_NOT_FOUND, "话题不存在或已禁用");
            }
        }
        validatePetOwnership(currentUserId, request.petId());

        Post post = new Post();
        post.setId(IdWorker.getId());
        post.setUserId(currentUserId);
        post.setPetId(request.petId());
        post.setTopicId(request.topicId());
        post.setTitle(request.title());
        post.setContent(request.content());
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setFavoriteCount(0);

        // Run content moderation
        String textToCheck = request.title() + " " + request.content();
        ContentReviewResult modResult = moderationService.moderateAndRecord(
                CommunityContentType.POST, post.getId(), currentUserId, textToCheck);

        post.setStatus(modResult.contentStatus());
        post.setRiskLevel(modResult.riskLevel());

        if ("PUBLISHED".equals(modResult.contentStatus())) {
            post.setPublishTime(LocalDateTime.now());
        }

        postMapper.insert(post);

        // Process tags (max 3)
        List<String> tagNames = request.tags();
        if (tagNames != null && !tagNames.isEmpty()) {
            linkTags(post.getId(), tagNames);
        }

        // Process images (max 9)
        List<String> imageUrls = request.imageUrls();
        if (imageUrls != null && !imageUrls.isEmpty()) {
            savePostImages(post.getId(), imageUrls);
        }

        // M8.3：AI 文本审核异步钩子（agent-enabled 时 Bean 存在；只产 PostReport 不改状态 B4）
        if (aiModerationReviewService != null) {
            aiModerationReviewService.reviewPostAsync(post.getId(), textToCheck);
        }

        return toPostResponse(post);
    }

    /**
     * Deletes a post (logical delete). Only the post author can delete.
     * Also logical deletes associated comments, images, and tag relations.
     */
    @Transactional
    public void deletePost(Long currentUserId, Long postId) {
        Post post = postMapper.selectOne(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getId, postId)
                        .eq(Post::getDeleted, 0)
        );
        if (post == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND, "帖子不存在");
        }
        if (!post.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己的帖子");
        }

        // Logical delete the post (deleteById triggers @TableLogic)
        postMapper.deleteById(postId);

        // Logical delete associated comments
        List<PostComment> comments = commentMapper.selectList(
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getPostId, postId)
        );
        for (PostComment c : comments) {
            commentMapper.deleteById(c.getId());
        }

        // Delete associated images
        imageMapper.delete(
                new LambdaQueryWrapper<PostImage>()
                        .eq(PostImage::getPostId, postId)
        );

        // Delete tag relations
        postTagRelMapper.delete(
                new LambdaQueryWrapper<PostTagRel>()
                        .eq(PostTagRel::getPostId, postId)
        );
    }

    /**
     * Lists published posts with pagination. Only shows PUBLISHED and non-deleted posts.
     */
    public PageResponse<PostResponse> listPosts(Long topicId, int page, int size) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, "PUBLISHED")
                .eq(Post::getDeleted, 0);

        if (topicId != null) {
            wrapper.eq(Post::getTopicId, topicId);
        }
        wrapper.orderByDesc(Post::getPublishTime);

        Page<Post> pageResult = postMapper.selectPage(new Page<>(page, size), wrapper);
        List<PostResponse> items = pageResult.getRecords().stream()
                .map(this::toPostResponse)
                .toList();
        return PageResponse.of(items, pageResult.getTotal(), page, size);
    }

    /**
     * Gets post detail. Users can only see PUBLISHED posts or their own non-deleted posts.
     */
    public PostDetailResponse getPostDetail(Long currentUserId, Long postId) {
        Post post = postMapper.selectOne(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getId, postId)
                        .eq(Post::getDeleted, 0)
        );
        if (post == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND, "帖子不存在");
        }

        // Check visibility: published posts visible to all; non-published only to author
        if (!"PUBLISHED".equals(post.getStatus())) {
            if (currentUserId == null || !currentUserId.equals(post.getUserId())) {
                throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_VISIBLE, "帖子不可查看");
            }
        }

        List<String> imageUrls = imageMapper.selectList(
                new LambdaQueryWrapper<PostImage>()
                        .eq(PostImage::getPostId, postId)
                        .orderByAsc(PostImage::getSort)
        ).stream().map(PostImage::getImageUrl).toList();

        return new PostDetailResponse(
                post.getId(), post.getUserId(), post.getPetId(), post.getTopicId(),
                post.getTitle(), post.getContent(), post.getStatus(),
                post.getViewCount(), post.getLikeCount(), post.getCommentCount(),
                post.getFavoriteCount(), post.getPublishTime(), post.getCreateTime(),
                imageUrls
        );
    }

    // ==================== Comment Operations ====================

    /**
     * Creates a comment on a published post with content moderation.
     */
    @Transactional
    public CommentResponse createComment(Long currentUserId, Long postId,
                                          CommentCreateRequest request) {
        // Verify post exists and is published
        // B3 修复：行锁读——commentCount 读-改-写在并发评论下会丢失更新
        // （范式对齐 CommunityInteractionService.getPublishedPost）
        Post post = postMapper.selectOne(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getId, postId)
                        .eq(Post::getStatus, "PUBLISHED")
                        .eq(Post::getDeleted, 0)
                        .last("FOR UPDATE")
        );
        if (post == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND, "帖子不存在或不可评论");
        }

        // Validate parent comment if provided
        if (request.parentId() != null) {
            PostComment parent = commentMapper.selectOne(
                    new LambdaQueryWrapper<PostComment>()
                            .eq(PostComment::getId, request.parentId())
                            .eq(PostComment::getPostId, postId)
                            .eq(PostComment::getDeleted, 0)
            );
            if (parent == null) {
                throw new BusinessException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND, "父评论不存在");
            }
        }

        PostComment comment = new PostComment();
        comment.setId(IdWorker.getId());
        comment.setPostId(postId);
        comment.setUserId(currentUserId);
        comment.setParentId(request.parentId());
        comment.setContent(request.content());
        comment.setLikeCount(0);

        // Run content moderation
        ContentReviewResult modResult = moderationService.moderateAndRecord(
                CommunityContentType.COMMENT, comment.getId(), currentUserId, request.content());

        comment.setStatus(modResult.contentStatus());
        comment.setRiskLevel(modResult.riskLevel());

        commentMapper.insert(comment);

        // Only increment comment count if published
        if ("PUBLISHED".equals(modResult.contentStatus())) {
            post.setCommentCount(post.getCommentCount() + 1);
            postMapper.updateById(post);

            // Notify post author
            String snippet = request.content().length() > 50
                    ? request.content().substring(0, 50) + "..."
                    : request.content();
            notificationService.createNotification(post.getUserId(), currentUserId,
                    "COMMENT", postId, comment.getId(), snippet);
        }

        // M8.3：AI 文本审核异步钩子（帖+评论共用，只产 PostRecord 不改状态 B4）
        if (aiModerationReviewService != null) {
            aiModerationReviewService.reviewCommentAsync(postId, comment.getId(), request.content());
        }

        return toCommentResponse(comment);
    }

    /**
     * Lists published comments for a post.
     */
    public PageResponse<CommentResponse> listComments(Long postId, int page, int size) {
        // Verify post exists
        Long postCount = postMapper.selectCount(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getId, postId)
                        .eq(Post::getDeleted, 0)
        );
        if (postCount == 0) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND, "帖子不存在");
        }

        LambdaQueryWrapper<PostComment> wrapper = new LambdaQueryWrapper<PostComment>()
                .eq(PostComment::getPostId, postId)
                .eq(PostComment::getStatus, "PUBLISHED")
                .eq(PostComment::getDeleted, 0)
                .orderByAsc(PostComment::getCreateTime);

        Page<PostComment> pageResult = commentMapper.selectPage(new Page<>(page, size), wrapper);
        List<CommentResponse> items = pageResult.getRecords().stream()
                .map(this::toCommentResponse)
                .toList();
        return PageResponse.of(items, pageResult.getTotal(), page, size);
    }

    // ==================== Public (anonymous) Reads ====================

    /**
     * Lists published posts for anonymous readers. Only PUBLISHED, non-deleted posts
     * are returned, without private identifiers or internal status.
     */
    public PageResponse<PublicPostSummaryResponse> listPublicPosts(Long topicId, String keyword, String tag, int page, int size, Long viewerId) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getStatus, "PUBLISHED")
                .eq(Post::getDeleted, 0);

        if (topicId != null) {
            wrapper.eq(Post::getTopicId, topicId);
        }
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            wrapper.and(w -> w.like(Post::getTitle, SqlLikeUtils.escape(trimmed)).or().like(Post::getContent, SqlLikeUtils.escape(trimmed)));
        }

        // Filter by tag name: find tag ID, then post IDs from post_tag_rel
        if (tag != null && !tag.isBlank()) {
            String trimmedTag = tag.trim();
            CommunityTag tagEntity = tagMapper.selectOne(
                    new LambdaQueryWrapper<CommunityTag>()
                            .eq(CommunityTag::getName, trimmedTag)
                            .eq(CommunityTag::getDeleted, 0)
            );
            if (tagEntity == null) {
                // No such tag → empty result
                return PageResponse.of(Collections.emptyList(), 0L, page, size);
            }
            List<PostTagRel> rels = postTagRelMapper.selectList(
                    new LambdaQueryWrapper<PostTagRel>()
                            .eq(PostTagRel::getTagId, tagEntity.getId())
            );
            if (rels.isEmpty()) {
                return PageResponse.of(Collections.emptyList(), 0L, page, size);
            }
            List<Long> postIds = rels.stream().map(PostTagRel::getPostId).toList();
            wrapper.in(Post::getId, postIds);
        }

        wrapper.orderByDesc(Post::getPublishTime);

        Page<Post> pageResult = postMapper.selectPage(new Page<>(page, size), wrapper);
        List<Post> posts = pageResult.getRecords();

        // Batch load tags for all posts in this page
        Map<Long, List<String>> tagsByPostId = loadTagsForPosts(posts);

        // Batch load author info
        Map<Long, AuthorInfo> authorByUserId = loadAuthorInfo(
                posts.stream().map(Post::getUserId).toList()
        );

        // Batch load cover images
        Map<Long, List<String>> imagesByPostId = loadImageUrls(
                posts.stream().map(Post::getId).toList()
        );

        // Batch load the viewer's like/favorite state for this page (single query each)
        final Set<Long> likedIds;
        final Set<Long> favoritedIds;
        if (viewerId != null && !posts.isEmpty()) {
            List<Long> pagePostIds = posts.stream().map(Post::getId).toList();
            likedIds = postLikeMapper.selectList(
                    new LambdaQueryWrapper<PostLike>()
                            .eq(PostLike::getUserId, viewerId)
                            .in(PostLike::getPostId, pagePostIds)
            ).stream().map(PostLike::getPostId).collect(Collectors.toSet());
            favoritedIds = postFavoriteMapper.selectList(
                    new LambdaQueryWrapper<PostFavorite>()
                            .eq(PostFavorite::getUserId, viewerId)
                            .in(PostFavorite::getPostId, pagePostIds)
            ).stream().map(PostFavorite::getPostId).collect(Collectors.toSet());
        } else {
            likedIds = Set.of();
            favoritedIds = Set.of();
        }

        List<PublicPostSummaryResponse> items = posts.stream()
                .map(p -> {
                    AuthorInfo author = authorByUserId.get(p.getUserId());
                    return toPublicPostSummary(p,
                            tagsByPostId.getOrDefault(p.getId(), Collections.emptyList()),
                            author != null ? author.name() : null,
                            author != null ? author.avatar() : null,
                            imagesByPostId.getOrDefault(p.getId(), Collections.emptyList()),
                            likedIds.contains(p.getId()),
                            favoritedIds.contains(p.getId()));
                })
                .toList();
        return PageResponse.of(items, pageResult.getTotal(), page, size);
    }

    /**
     * Gets a published post detail for anonymous readers. Non-published, deleted or
     * non-existent posts all resolve to COMMUNITY_POST_NOT_FOUND (404) so existence
     * and audit status are never leaked.
     */
    public PublicPostDetailResponse getPublicPostDetail(Long postId, Long viewerId) {
        Post post = postMapper.selectOne(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getId, postId)
                        .eq(Post::getStatus, "PUBLISHED")
                        .eq(Post::getDeleted, 0)
        );
        if (post == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND, "帖子不存在");
        }

        List<String> imageUrls = imageMapper.selectList(
                new LambdaQueryWrapper<PostImage>()
                        .eq(PostImage::getPostId, postId)
                        .orderByAsc(PostImage::getSort)
        ).stream().map(PostImage::getImageUrl).toList();

        List<String> tags = loadTagsForPost(postId);

        // Load author info
        AuthorInfo author = loadAuthorInfo(List.of(post.getUserId())).get(post.getUserId());

        return new PublicPostDetailResponse(
                post.getId(), post.getTopicId(),
                post.getTitle(), post.getContent(),
                post.getViewCount(), post.getLikeCount(), post.getCommentCount(),
                post.getFavoriteCount(), post.getPublishTime(), post.getCreateTime(),
                imageUrls,
                tags,
                author != null ? author.name() : null,
                author != null ? author.avatar() : null,
                viewerId != null && postLikeMapper.exists(
                        new LambdaQueryWrapper<PostLike>()
                                .eq(PostLike::getPostId, postId)
                                .eq(PostLike::getUserId, viewerId)
                ),
                viewerId != null && postFavoriteMapper.exists(
                        new LambdaQueryWrapper<PostFavorite>()
                                .eq(PostFavorite::getPostId, postId)
                                .eq(PostFavorite::getUserId, viewerId)
                )
        );
    }

    /**
     * Lists published comments under a published post for anonymous readers.
     * Returns a tree structure: top-level comments with nested replies.
     */
    public List<PublicCommentTreeResponse> listPublicCommentsTree(Long postId) {
        Long postCount = postMapper.selectCount(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getId, postId)
                        .eq(Post::getStatus, "PUBLISHED")
                        .eq(Post::getDeleted, 0)
        );
        if (postCount == 0) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND, "帖子不存在");
        }

        // Load all published, non-deleted comments for this post
        List<PostComment> allComments = commentMapper.selectList(
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getPostId, postId)
                        .eq(PostComment::getStatus, "PUBLISHED")
                        .eq(PostComment::getDeleted, 0)
                        .orderByAsc(PostComment::getCreateTime)
        );

        // Partition into top-level and replies
        List<PostComment> topLevel = allComments.stream()
                .filter(c -> c.getParentId() == null)
                .toList();

        Map<Long, List<PostComment>> repliesByParent = allComments.stream()
                .filter(c -> c.getParentId() != null)
                .collect(Collectors.groupingBy(PostComment::getParentId));

        // Batch load author info for all commenters
        Map<Long, AuthorInfo> authorByUserId = loadAuthorInfo(
                allComments.stream().map(PostComment::getUserId).toList()
        );

        return topLevel.stream()
                .map(c -> toCommentTree(c, repliesByParent, authorByUserId))
                .toList();
    }

    /**
     * Lists published comments under a published post as a flat list
     * (Douyin-style). Each comment carries author info plus an optional
     * {@code replyToName} (the author of the parent comment), so the UI can
     * render "{@code authorName @ replyToName}" without a nested tree.
     * <p>
     * Unlike {@link #listPublicCommentsTree(Long)}, this has no depth cap: a
     * reply to a reply is a first-class list item with its parent's author as
     * {@code replyToName}.
     */
    public List<PublicCommentFlatResponse> listPublicCommentsFlat(Long postId, Long viewerId) {
        Long postCount = postMapper.selectCount(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getId, postId)
                        .eq(Post::getStatus, "PUBLISHED")
                        .eq(Post::getDeleted, 0)
        );
        if (postCount == 0) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND, "帖子不存在");
        }

        List<PostComment> allComments = commentMapper.selectList(
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getPostId, postId)
                        .eq(PostComment::getStatus, "PUBLISHED")
                        .eq(PostComment::getDeleted, 0)
                        .orderByAsc(PostComment::getCreateTime)
        );

        if (allComments.isEmpty()) {
            return Collections.emptyList();
        }

        // Index comments by id so we can resolve parent author info
        Map<Long, PostComment> commentById = new HashMap<>();
        for (PostComment c : allComments) {
            commentById.put(c.getId(), c);
        }

        // Batch load author info for all commenters + all parent-comment authors
        List<Long> userIds = new ArrayList<>();
        for (PostComment c : allComments) {
            userIds.add(c.getUserId());
            if (c.getParentId() != null) {
                PostComment parent = commentById.get(c.getParentId());
                if (parent != null) {
                    userIds.add(parent.getUserId());
                }
            }
        }
        Map<Long, AuthorInfo> authorByUserId = loadAuthorInfo(userIds);

        // Batch load the viewer's comment-like state (single query)
        final Set<Long> likedIds;
        if (viewerId != null) {
            List<Long> pageCommentIds = allComments.stream().map(PostComment::getId).toList();
            likedIds = commentLikeMapper.selectList(
                    new LambdaQueryWrapper<CommentLike>()
                            .eq(CommentLike::getUserId, viewerId)
                            .in(CommentLike::getCommentId, pageCommentIds)
            ).stream().map(CommentLike::getCommentId).collect(Collectors.toSet());
        } else {
            likedIds = Set.of();
        }

        return allComments.stream()
                .map(c -> {
                    AuthorInfo author = authorByUserId.get(c.getUserId());
                    Long replyToUserId = null;
                    String replyToName = null;
                    if (c.getParentId() != null) {
                        PostComment parent = commentById.get(c.getParentId());
                        if (parent != null) {
                            replyToUserId = parent.getUserId();
                            AuthorInfo parentAuthor = authorByUserId.get(parent.getUserId());
                            replyToName = parentAuthor != null ? parentAuthor.name() : null;
                        }
                    }
                    return new PublicCommentFlatResponse(
                            c.getId(), c.getParentId(), c.getContent(),
                            c.getLikeCount(), c.getCreateTime(),
                            author != null ? author.name() : null,
                            author != null ? author.avatar() : null,
                            c.getUserId(),
                            replyToUserId, replyToName,
                            likedIds.contains(c.getId())
                    );
                })
                .toList();
    }

    /**
     * Deletes a comment (logical delete). Only the comment author can delete.
     * Decrement post comment count.
     */
    @Transactional
    public void deleteComment(Long currentUserId, Long commentId) {
        PostComment comment = commentMapper.selectOne(
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getId, commentId)
                        .eq(PostComment::getDeleted, 0)
        );
        if (comment == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND, "评论不存在");
        }
        if (!comment.getUserId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能删除自己的评论");
        }

        // Logical delete this comment (deleteById triggers @TableLogic)
        commentMapper.deleteById(commentId);

        // Also logical delete all child replies (if this is a top-level comment)
        List<PostComment> children = commentMapper.selectList(
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getParentId, commentId)
        );
        for (PostComment child : children) {
            commentMapper.deleteById(child.getId());
        }

        // Decrement post comment count (count this comment + its replies)
        // B3 修复：行锁读，与 createComment 的计数路径互斥
        int removedCount = 1 + children.size();
        Post post = postMapper.selectOne(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getId, comment.getPostId())
                        .last("FOR UPDATE")
        );
        if (post != null) {
            post.setCommentCount(Math.max(0, post.getCommentCount() - removedCount));
            postMapper.updateById(post);
        }
    }

    /**
     * Likes a comment. Idempotent.
     */
    @Transactional
    public void likeComment(Long currentUserId, Long commentId) {
        // B3 修复：行锁读——likeCount 读-改-写在并发点赞下会丢失更新
        PostComment comment = commentMapper.selectOne(
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getId, commentId)
                        .eq(PostComment::getStatus, "PUBLISHED")
                        .eq(PostComment::getDeleted, 0)
                        .last("FOR UPDATE")
        );
        if (comment == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND, "评论不存在");
        }

        boolean alreadyLiked = commentLikeMapper.exists(
                new LambdaQueryWrapper<CommentLike>()
                        .eq(CommentLike::getCommentId, commentId)
                        .eq(CommentLike::getUserId, currentUserId)
        );
        if (alreadyLiked) {
            return;
        }

        CommentLike like = new CommentLike();
        like.setCommentId(commentId);
        like.setUserId(currentUserId);
        try {
            commentLikeMapper.insert(like);
        } catch (org.springframework.dao.DuplicateKeyException ex) {
            return;
        }

        comment.setLikeCount(comment.getLikeCount() + 1);
        commentMapper.updateById(comment);
    }

    /**
     * Removes a like from a comment. Idempotent.
     */
    @Transactional
    public void unlikeComment(Long currentUserId, Long commentId) {
        // B3 修复：行锁读（与 likeComment 对称）
        PostComment comment = commentMapper.selectOne(
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getId, commentId)
                        .eq(PostComment::getDeleted, 0)
                        .last("FOR UPDATE")
        );
        if (comment == null) {
            return;
        }

        CommentLike existing = commentLikeMapper.selectOne(
                new LambdaQueryWrapper<CommentLike>()
                        .eq(CommentLike::getCommentId, commentId)
                        .eq(CommentLike::getUserId, currentUserId)
        );
        if (existing == null) {
            return;
        }

        commentLikeMapper.deleteById(existing.getId());
        comment.setLikeCount(Math.max(0, comment.getLikeCount() - 1));
        commentMapper.updateById(comment);
    }

    // ==================== Personal Community Center ====================

    /**
     * Lists current user's posts (all statuses, non-deleted). Paginated.
     */
    public PageResponse<PublicPostSummaryResponse> listMyPosts(Long userId, int page, int size) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getUserId, userId)
                .eq(Post::getDeleted, 0)
                .orderByDesc(Post::getCreateTime);

        Page<Post> pageResult = postMapper.selectPage(new Page<>(page, size), wrapper);
        List<Post> posts = pageResult.getRecords();

        Map<Long, List<String>> tagsByPostId = loadTagsForPosts(posts);
        Map<Long, AuthorInfo> authorByUserId = loadAuthorInfo(
                posts.stream().map(Post::getUserId).toList()
        );
        Map<Long, List<String>> imagesByPostId = loadImageUrls(
                posts.stream().map(Post::getId).toList()
        );

        Set<Long> myLikedIds = postLikeMapper.selectList(
                new LambdaQueryWrapper<PostLike>().eq(PostLike::getUserId, userId)
        ).stream().map(PostLike::getPostId).collect(Collectors.toSet());
        Set<Long> myFavoritedIds = postFavoriteMapper.selectList(
                new LambdaQueryWrapper<PostFavorite>().eq(PostFavorite::getUserId, userId)
        ).stream().map(PostFavorite::getPostId).collect(Collectors.toSet());

        List<PublicPostSummaryResponse> items = posts.stream()
                .map(p -> {
                    AuthorInfo author = authorByUserId.get(p.getUserId());
                    return toPublicPostSummary(p,
                            tagsByPostId.getOrDefault(p.getId(), Collections.emptyList()),
                            author != null ? author.name() : null,
                            author != null ? author.avatar() : null,
                            imagesByPostId.getOrDefault(p.getId(), Collections.emptyList()),
                            myLikedIds.contains(p.getId()),
                            myFavoritedIds.contains(p.getId()));
                })
                .toList();
        return PageResponse.of(items, pageResult.getTotal(), page, size);
    }

    /**
     * Lists posts the current user has liked. Only PUBLISHED posts are returned.
     */
    public PageResponse<PublicPostSummaryResponse> listMyLikedPosts(Long userId, int page, int size) {
        List<PostLike> likes = postLikeMapper.selectList(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getUserId, userId)
        );

        if (likes.isEmpty()) {
            return PageResponse.empty(page, size);
        }

        List<Long> likedPostIds = likes.stream().map(PostLike::getPostId).toList();

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .in(Post::getId, likedPostIds)
                .eq(Post::getStatus, "PUBLISHED")
                .eq(Post::getDeleted, 0)
                .orderByDesc(Post::getPublishTime);

        Page<Post> pageResult = postMapper.selectPage(new Page<>(page, size), wrapper);
        List<Post> posts = pageResult.getRecords();

        Map<Long, List<String>> tagsByPostId = loadTagsForPosts(posts);
        Map<Long, AuthorInfo> authorByUserId = loadAuthorInfo(
                posts.stream().map(Post::getUserId).toList()
        );
        Map<Long, List<String>> imagesByPostId = loadImageUrls(
                posts.stream().map(Post::getId).toList()
        );

        Set<Long> myFavoritedIds = postFavoriteMapper.selectList(
                new LambdaQueryWrapper<PostFavorite>().eq(PostFavorite::getUserId, userId)
        ).stream().map(PostFavorite::getPostId).collect(Collectors.toSet());

        List<PublicPostSummaryResponse> items = posts.stream()
                .map(p -> {
                    AuthorInfo author = authorByUserId.get(p.getUserId());
                    return toPublicPostSummary(p,
                            tagsByPostId.getOrDefault(p.getId(), Collections.emptyList()),
                            author != null ? author.name() : null,
                            author != null ? author.avatar() : null,
                            imagesByPostId.getOrDefault(p.getId(), Collections.emptyList()),
                            true,
                            myFavoritedIds.contains(p.getId()));
                })
                .toList();
        return PageResponse.of(items, pageResult.getTotal(), page, size);
    }

    /**
     * Lists posts the current user has favorited. Only PUBLISHED posts are returned.
     */
    public PageResponse<PublicPostSummaryResponse> listMyFavoritedPosts(Long userId, int page, int size) {
        List<PostFavorite> favorites = postFavoriteMapper.selectList(
                new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getUserId, userId)
        );

        if (favorites.isEmpty()) {
            return PageResponse.empty(page, size);
        }

        List<Long> favoritedPostIds = favorites.stream().map(PostFavorite::getPostId).toList();

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .in(Post::getId, favoritedPostIds)
                .eq(Post::getStatus, "PUBLISHED")
                .eq(Post::getDeleted, 0)
                .orderByDesc(Post::getPublishTime);

        Page<Post> pageResult = postMapper.selectPage(new Page<>(page, size), wrapper);
        List<Post> posts = pageResult.getRecords();

        Map<Long, List<String>> tagsByPostId = loadTagsForPosts(posts);
        Map<Long, AuthorInfo> authorByUserId = loadAuthorInfo(
                posts.stream().map(Post::getUserId).toList()
        );
        Map<Long, List<String>> imagesByPostId = loadImageUrls(
                posts.stream().map(Post::getId).toList()
        );

        Set<Long> myLikedIds = postLikeMapper.selectList(
                new LambdaQueryWrapper<PostLike>().eq(PostLike::getUserId, userId)
        ).stream().map(PostLike::getPostId).collect(Collectors.toSet());

        List<PublicPostSummaryResponse> items = posts.stream()
                .map(p -> {
                    AuthorInfo author = authorByUserId.get(p.getUserId());
                    return toPublicPostSummary(p,
                            tagsByPostId.getOrDefault(p.getId(), Collections.emptyList()),
                            author != null ? author.name() : null,
                            author != null ? author.avatar() : null,
                            imagesByPostId.getOrDefault(p.getId(), Collections.emptyList()),
                            myLikedIds.contains(p.getId()),
                            true);
                })
                .toList();
        return PageResponse.of(items, pageResult.getTotal(), page, size);
    }

    // ==================== Private Helpers ====================

    private PostResponse toPostResponse(Post post) {
        return new PostResponse(
                post.getId(), post.getUserId(), post.getPetId(), post.getTopicId(),
                post.getTitle(), post.getContent(), post.getStatus(),
                post.getViewCount(), post.getLikeCount(), post.getCommentCount(),
                post.getFavoriteCount(), post.getPublishTime(), post.getCreateTime()
        );
    }

    private CommentResponse toCommentResponse(PostComment comment) {
        return new CommentResponse(
                comment.getId(), comment.getPostId(), comment.getUserId(),
                comment.getParentId(), comment.getContent(), comment.getStatus(),
                comment.getLikeCount(), comment.getCreateTime()
        );
    }

    private PublicPostSummaryResponse toPublicPostSummary(Post post, List<String> tags,
                                                            String authorName, String authorAvatar,
                                                            List<String> imageUrls,
                                                            boolean likedByMe, boolean favoritedByMe) {
        return new PublicPostSummaryResponse(
                post.getId(), post.getTopicId(),
                post.getTitle(), post.getContent(),
                post.getViewCount(), post.getLikeCount(), post.getCommentCount(),
                post.getFavoriteCount(), post.getPublishTime(), post.getCreateTime(),
                imageUrls,
                tags,
                authorName,
                authorAvatar,
                likedByMe,
                favoritedByMe
        );
    }

    private PublicCommentResponse toPublicCommentResponse(PostComment comment) {
        return new PublicCommentResponse(
                comment.getId(), comment.getParentId(),
                comment.getContent(), comment.getLikeCount(), comment.getCreateTime()
        );
    }

    private PublicCommentTreeResponse toCommentTree(PostComment comment,
                                                     Map<Long, List<PostComment>> repliesByParent,
                                                     Map<Long, AuthorInfo> authorByUserId) {
        List<PostComment> childComments = repliesByParent.getOrDefault(comment.getId(), Collections.emptyList());
        List<PublicCommentTreeResponse> replies = childComments.stream()
                .map(child -> toCommentTree(child, repliesByParent, authorByUserId))
                .toList();
        AuthorInfo author = authorByUserId.get(comment.getUserId());
        return new PublicCommentTreeResponse(
                comment.getId(), comment.getParentId(),
                comment.getContent(), comment.getLikeCount(), comment.getCreateTime(),
                author != null ? author.name() : null,
                author != null ? author.avatar() : null,
                replies
        );
    }

    private void validatePetOwnership(Long currentUserId, Long petId) {
        if (petId == null) {
            return;
        }

        Pet pet = petMapper.selectById(petId);
        if (pet == null || pet.getDeleted() != null && pet.getDeleted() == 1) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "关联宠物不存在");
        }
        if (!currentUserId.equals(pet.getUserId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能关联其他用户的宠物");
        }
    }

    // ==================== User Helpers ====================

    /** Simple record to hold public author info. */
    private record AuthorInfo(String name, String avatar) {}

    /**
     * Batch loads author info (nickname + avatar) for a set of user IDs.
     */
    private Map<Long, AuthorInfo> loadAuthorInfo(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> distinctIds = userIds.stream().distinct().toList();
        return userService.listByIds(distinctIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> new AuthorInfo(u.getNickname(), u.getAvatarUrl())
                ));
    }

    // ==================== Image Helpers ====================

    /**
     * Saves post images to post_image table. Max 9 per post.
     */
    private void savePostImages(Long postId, List<String> imageUrls) {
        List<String> validUrls = imageUrls.stream()
                .filter(u -> u != null && !u.isBlank())
                .limit(9)
                .toList();

        for (int i = 0; i < validUrls.size(); i++) {
            PostImage img = new PostImage();
            img.setId(IdWorker.getId());
            img.setPostId(postId);
            img.setImageUrl(validUrls.get(i));
            img.setSort(i);
            imageMapper.insert(img);
        }
    }

    /**
     * Batch loads all image URLs (capped at 6 per post, sorted by sort order) for multiple posts.
     */
    private Map<Long, List<String>> loadImageUrls(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PostImage> images = imageMapper.selectList(
                new LambdaQueryWrapper<PostImage>()
                        .in(PostImage::getPostId, postIds)
                        .orderByAsc(PostImage::getSort)
        );
        return images.stream()
                .collect(Collectors.groupingBy(
                        PostImage::getPostId,
                        Collectors.mapping(PostImage::getImageUrl, Collectors.toList())
                ))
                .entrySet().stream()
                .collect(Collectors.toMap(
                        java.util.Map.Entry::getKey,
                        e -> e.getValue().stream().limit(6).toList()
                ));
    }

    // ==================== Tag Helpers ====================

    /**
     * Links tags to a post: find-or-create each tag, save relations, increment usage count.
     * Enforces max 3 tags; extra tags are silently ignored.
     */
    private void linkTags(Long postId, List<String> tagNames) {
        // Normalize: trim, lowercase, deduplicate, max 3
        List<String> normalized = tagNames.stream()
                .filter(n -> n != null && !n.isBlank())
                .map(String::trim)
                .map(n -> n.startsWith("#") ? n.substring(1) : n)
                .map(String::toLowerCase)
                .distinct()
                .limit(MAX_TAGS_PER_POST)
                .toList();

        if (normalized.isEmpty()) {
            return;
        }

        for (String tagName : normalized) {
            CommunityTag tag = tagMapper.selectOne(
                    new LambdaQueryWrapper<CommunityTag>()
                            .eq(CommunityTag::getName, tagName)
                            .eq(CommunityTag::getDeleted, 0)
            );

            if (tag == null) {
                tag = new CommunityTag();
                tag.setId(IdWorker.getId());
                tag.setName(tagName);
                tag.setUsageCount(1);
                tagMapper.insert(tag);
            } else {
                tag.setUsageCount(tag.getUsageCount() + 1);
                tagMapper.updateById(tag);
            }

            PostTagRel rel = new PostTagRel();
            rel.setId(IdWorker.getId());
            rel.setPostId(postId);
            rel.setTagId(tag.getId());
            postTagRelMapper.insert(rel);
        }
    }

    /**
     * Loads tag names for a single post.
     */
    private List<String> loadTagsForPost(Long postId) {
        List<PostTagRel> rels = postTagRelMapper.selectList(
                new LambdaQueryWrapper<PostTagRel>()
                        .eq(PostTagRel::getPostId, postId)
        );
        if (rels.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> tagIds = rels.stream().map(PostTagRel::getTagId).toList();
        List<CommunityTag> tags = tagMapper.selectBatchIds(tagIds);
        return tags.stream()
                .filter(t -> t.getDeleted() == null || t.getDeleted() == 0)
                .map(CommunityTag::getName)
                .toList();
    }

    /**
     * Batch loads tag names for multiple posts. Returns a map of postId → tag names.
     */
    private Map<Long, List<String>> loadTagsForPosts(List<Post> posts) {
        if (posts.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> postIds = posts.stream().map(Post::getId).toList();

        List<PostTagRel> rels = postTagRelMapper.selectList(
                new LambdaQueryWrapper<PostTagRel>()
                        .in(PostTagRel::getPostId, postIds)
        );
        if (rels.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> tagIds = rels.stream().map(PostTagRel::getTagId).distinct().toList();
        Map<Long, String> tagNameById = tagMapper.selectBatchIds(tagIds).stream()
                .filter(t -> t.getDeleted() == null || t.getDeleted() == 0)
                .collect(Collectors.toMap(CommunityTag::getId, CommunityTag::getName));

        return rels.stream()
                .filter(r -> tagNameById.containsKey(r.getTagId()))
                .collect(Collectors.groupingBy(
                        PostTagRel::getPostId,
                        Collectors.mapping(r -> tagNameById.get(r.getTagId()), Collectors.toList())
                ));
    }

    /**
     * Searches tags by keyword for autocomplete, ordered by usage count desc.
     */
    public List<TagResponse> searchTags(String keyword, int limit) {
        LambdaQueryWrapper<CommunityTag> wrapper = new LambdaQueryWrapper<CommunityTag>()
                .eq(CommunityTag::getDeleted, 0)
                .orderByDesc(CommunityTag::getUsageCount)
                .last("LIMIT " + Math.min(limit, 50));

        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            String normalized = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
            wrapper.like(CommunityTag::getName, SqlLikeUtils.escape(normalized));
        }

        return tagMapper.selectList(wrapper).stream()
                .map(t -> new TagResponse(t.getId(), t.getName(), t.getUsageCount()))
                .toList();
    }

    /**
     * Lists popular tags ordered by usage count desc.
     */
    public List<TagResponse> popularTags(int limit) {
        LambdaQueryWrapper<CommunityTag> wrapper = new LambdaQueryWrapper<CommunityTag>()
                .eq(CommunityTag::getDeleted, 0)
                .orderByDesc(CommunityTag::getUsageCount)
                .last("LIMIT " + Math.min(limit, 50));

        return tagMapper.selectList(wrapper).stream()
                .map(t -> new TagResponse(t.getId(), t.getName(), t.getUsageCount()))
                .toList();
    }
}
