package com.petcare.community.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.community.dto.ReportPostRequest;
import com.petcare.community.entity.Post;
import com.petcare.community.entity.PostFavorite;
import com.petcare.community.entity.PostLike;
import com.petcare.community.entity.PostReport;
import com.petcare.community.mapper.PostFavoriteMapper;
import com.petcare.community.mapper.PostLikeMapper;
import com.petcare.community.mapper.PostMapper;
import com.petcare.community.mapper.PostReportMapper;
import com.petcare.notification.service.NotificationService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for community interactions: likes, favorites, and reports.
 * All operations are idempotent and update counts within the same transaction.
 */
@Service
public class CommunityInteractionService {

    private final PostMapper postMapper;
    private final PostLikeMapper likeMapper;
    private final PostFavoriteMapper favoriteMapper;
    private final PostReportMapper reportMapper;
    private final NotificationService notificationService;

    public CommunityInteractionService(PostMapper postMapper,
                                        PostLikeMapper likeMapper,
                                        PostFavoriteMapper favoriteMapper,
                                        PostReportMapper reportMapper,
                                        NotificationService notificationService) {
        this.postMapper = postMapper;
        this.likeMapper = likeMapper;
        this.favoriteMapper = favoriteMapper;
        this.reportMapper = reportMapper;
        this.notificationService = notificationService;
    }

    // ==================== Like Operations ====================

    /**
     * Likes a published post. Idempotent — duplicate likes do not increase count.
     *
     * @param currentUserId the user performing the like
     * @param postId        the post to like
     */
    @Transactional
    public void likePost(Long currentUserId, Long postId) {
        Post post = getPublishedPost(postId, true);

        boolean alreadyLiked = likeMapper.exists(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, postId)
                        .eq(PostLike::getUserId, currentUserId)
        );
        if (alreadyLiked) {
            return; // Idempotent: no error, no count change
        }

        PostLike like = new PostLike();
        like.setPostId(postId);
        like.setUserId(currentUserId);
        try {
            likeMapper.insert(like);
        } catch (DuplicateKeyException ex) {
            return;
        }

        post.setLikeCount(post.getLikeCount() + 1);
        postMapper.updateById(post);

        // Notify post author
        notificationService.createNotification(post.getUserId(), currentUserId,
                "LIKE", postId, null, null);
    }

    /**
     * Removes a like from a published post. Idempotent — no error if not liked.
     */
    @Transactional
    public void unlikePost(Long currentUserId, Long postId) {
        Post post = getPublishedPost(postId, true);

        PostLike existing = likeMapper.selectOne(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, postId)
                        .eq(PostLike::getUserId, currentUserId)
        );
        if (existing == null) {
            return; // Idempotent: no error
        }

        likeMapper.deleteById(existing.getId());
        post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
        postMapper.updateById(post);
    }

    // ==================== Favorite Operations ====================

    /**
     * Favorites a published post. Idempotent — duplicate favorites do not increase count.
     */
    @Transactional
    public void favoritePost(Long currentUserId, Long postId) {
        Post post = getPublishedPost(postId, true);

        boolean alreadyFavorited = favoriteMapper.exists(
                new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getPostId, postId)
                        .eq(PostFavorite::getUserId, currentUserId)
        );
        if (alreadyFavorited) {
            return; // Idempotent: no error, no count change
        }

        PostFavorite favorite = new PostFavorite();
        favorite.setPostId(postId);
        favorite.setUserId(currentUserId);
        try {
            favoriteMapper.insert(favorite);
        } catch (DuplicateKeyException ex) {
            return;
        }

        post.setFavoriteCount(post.getFavoriteCount() + 1);
        postMapper.updateById(post);

        // Notify post author
        notificationService.createNotification(post.getUserId(), currentUserId,
                "FAVORITE", postId, null, null);
    }

    /**
     * Removes a favorite from a published post. Idempotent — no error if not favorited.
     */
    @Transactional
    public void unfavoritePost(Long currentUserId, Long postId) {
        Post post = getPublishedPost(postId, true);

        PostFavorite existing = favoriteMapper.selectOne(
                new LambdaQueryWrapper<PostFavorite>()
                        .eq(PostFavorite::getPostId, postId)
                        .eq(PostFavorite::getUserId, currentUserId)
        );
        if (existing == null) {
            return; // Idempotent: no error
        }

        favoriteMapper.deleteById(existing.getId());
        post.setFavoriteCount(Math.max(0, post.getFavoriteCount() - 1));
        postMapper.updateById(post);
    }

    // ==================== Report Operations ====================

    /**
     * Reports a post. Duplicate reports by the same user for the same post return 409.
     */
    @Transactional
    public void reportPost(Long currentUserId, Long postId, ReportPostRequest request) {
        // Lock the post row so duplicate reports for the same post serialize safely.
        Post post = postMapper.selectOne(
                new LambdaQueryWrapper<Post>()
                        .eq(Post::getId, postId)
                        .eq(Post::getDeleted, 0)
                        .last("FOR UPDATE")
        );
        if (post == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND, "帖子不存在");
        }

        // Check for duplicate report
        boolean alreadyReported = reportMapper.exists(
                new LambdaQueryWrapper<PostReport>()
                        .eq(PostReport::getPostId, postId)
                        .eq(PostReport::getReporterId, currentUserId)
        );
        if (alreadyReported) {
            throw new BusinessException(ErrorCode.COMMUNITY_DUPLICATE_REPORT, "你已经举报过该帖子");
        }

        PostReport report = new PostReport();
        report.setPostId(postId);
        report.setReporterId(currentUserId);
        report.setReasonType(request.reasonType());
        report.setReason(request.reason());
        report.setStatus("PENDING");
        try {
            reportMapper.insert(report);
        } catch (DuplicateKeyException ex) {
            throw new BusinessException(ErrorCode.COMMUNITY_DUPLICATE_REPORT, "你已经举报过该帖子");
        }
    }

    /**
     * M8.3：AI 文本审核产出的系统举报（docs/09 §5.3.2）。
     * <p>与用户举报的差异：reporterId=0（系统）、reason 带 {@code [AI审核]} 标记、
     * 不去重（每次审核独立成单）。<b>只产 PostReport 进人工队列，
     * 不改帖子状态</b>（B4：AI 建议不直接处置），处置由管理员走既有举报处理流程。</p>
     *
     * @param postId     被审核帖子 ID
     * @param reasonType SPAM / ABUSE / ILLEGAL / OTHER（ModerationAgent 分类映射）
     * @param reason     建议描述（含置信度，已脱敏）
     */
    @Transactional
    public void createSystemAiReport(Long postId, String reasonType, String reason) {
        PostReport report = new PostReport();
        report.setPostId(postId);
        report.setReporterId(0L); // 0 = 系统主体（区别于 user 表任何真实用户）
        report.setReasonType(reasonType);
        report.setReason(reason);
        report.setStatus("PENDING");
        reportMapper.insert(report);
    }

    /**
     * M8.3：分页查询 AI 审核产生的举报（reason 以 {@code [AI审核]} 前缀标记），
     * 供管理端"AI 审核建议"面板展示（docs/09 §5.3.2：审核结果仅管理端可见）。
     */
    public com.baomidou.mybatisplus.extension.plugins.pagination.Page<PostReport> listAiReports(
            String status, int page, int size) {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostReport> wrapper =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PostReport>()
                        .eq(PostReport::getReporterId, 0L)
                        .likeRight(PostReport::getReason, "[AI审核]");
        if (status != null && !status.isBlank()) {
            wrapper.eq(PostReport::getStatus, status);
        }
        wrapper.orderByDesc(PostReport::getCreateTime);
        return reportMapper.selectPage(
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), wrapper);
    }

    // ==================== Private Helpers ====================

    private Post getPublishedPost(Long postId, boolean forUpdate) {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getId, postId)
                .eq(Post::getStatus, "PUBLISHED")
                .eq(Post::getDeleted, 0);
        if (forUpdate) {
            wrapper.last("FOR UPDATE");
        }
        Post post = postMapper.selectOne(wrapper);
        if (post == null) {
            throw new BusinessException(ErrorCode.COMMUNITY_POST_NOT_FOUND, "帖子不存在或不可操作");
        }
        return post;
    }
}
