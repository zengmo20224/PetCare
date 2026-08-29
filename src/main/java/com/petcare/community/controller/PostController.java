package com.petcare.community.controller;

import com.petcare.common.api.ApiResponse;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.pagination.PageResponse;
import com.petcare.common.security.SecurityContextHelper;
import com.petcare.community.dto.CommentCreateRequest;
import com.petcare.community.dto.CommentResponse;
import com.petcare.community.dto.PostCreateRequest;
import com.petcare.community.dto.PostResponse;
import com.petcare.community.dto.PublicCommentFlatResponse;
import com.petcare.community.dto.PublicCommentResponse;
import com.petcare.community.dto.PublicCommentTreeResponse;
import com.petcare.community.dto.PublicPostDetailResponse;
import com.petcare.community.dto.PublicPostSummaryResponse;
import com.petcare.community.dto.ReportPostRequest;
import com.petcare.community.service.CommunityInteractionService;
import com.petcare.community.service.CommunityPostApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * User-facing post and comment endpoints.
 *
 * Note: User JWT is not yet implemented. User mutation endpoints will return 401
 * until user authentication is added.
 */
@RestController
@RequestMapping("/api/v1/posts")
public class PostController {

    private final CommunityPostApplicationService postService;
    private final CommunityInteractionService interactionService;

    public PostController(CommunityPostApplicationService postService,
                          CommunityInteractionService interactionService) {
        this.postService = postService;
        this.interactionService = interactionService;
    }

    /**
     * List published posts with optional topic filter and pagination.
     * Returns the public summary view (no private identifiers or internal status).
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PublicPostSummaryResponse>>> listPosts(
            @RequestParam(required = false) Long topicId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String tag,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Viewer-aware: carries the caller's like/favorite state when a valid token is present.
        Long viewerId = SecurityContextHelper.getCurrentUserId().orElse(null);
        PageResponse<PublicPostSummaryResponse> result =
                postService.listPublicPosts(topicId, keyword, tag, page, size, viewerId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * Get published post detail. Only PUBLISHED posts are visible; everything else
     * resolves to a safe 404 so existence and audit status are never leaked.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PublicPostDetailResponse>> getPostDetail(@PathVariable Long id) {
        Long viewerId = SecurityContextHelper.getCurrentUserId().orElse(null);
        PublicPostDetailResponse result = postService.getPublicPostDetail(id, viewerId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * Create a new post. User identity comes from security context, not request body.
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> createPost(
            @Valid @RequestBody PostCreateRequest request) {
        Long currentUserId = resolveCurrentUserId();
        PostResponse result = postService.createPost(currentUserId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    /**
     * List published comments for a published post in tree structure.
     * Returns top-level comments with nested replies.
     */
    @GetMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<List<PublicCommentTreeResponse>>> listComments(
            @PathVariable Long postId) {
        List<PublicCommentTreeResponse> result = postService.listPublicCommentsTree(postId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * List published comments for a published post as a flat list
     * (Douyin-style). Each item carries author info and an optional
     * {@code replyToName} so the UI can render "author @ replyTo" without a
     * nested tree. Unlike the tree endpoint, replies-to-replies are visible.
     */
    @GetMapping("/{postId}/comments/flat")
    public ResponseEntity<ApiResponse<List<PublicCommentFlatResponse>>> listCommentsFlat(
            @PathVariable Long postId) {
        Long viewerId = SecurityContextHelper.getCurrentUserId().orElse(null);
        List<PublicCommentFlatResponse> result = postService.listPublicCommentsFlat(postId, viewerId);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * Create a comment on a post. User identity from security context.
     */
    @PostMapping("/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> createComment(
            @PathVariable Long postId,
            @Valid @RequestBody CommentCreateRequest request) {
        Long currentUserId = resolveCurrentUserId();
        CommentResponse result = postService.createComment(currentUserId, postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result));
    }

    /**
     * Delete a post. Only the post author can delete.
     */
    @DeleteMapping("/{postId}")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable Long postId) {
        Long currentUserId = resolveCurrentUserId();
        postService.deletePost(currentUserId, postId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Like a post. Idempotent.
     */
    @PostMapping("/{postId}/like")
    public ResponseEntity<ApiResponse<Void>> likePost(@PathVariable Long postId) {
        Long currentUserId = resolveCurrentUserId();
        interactionService.likePost(currentUserId, postId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Remove like from a post. Idempotent.
     */
    @DeleteMapping("/{postId}/like")
    public ResponseEntity<ApiResponse<Void>> unlikePost(@PathVariable Long postId) {
        Long currentUserId = resolveCurrentUserId();
        interactionService.unlikePost(currentUserId, postId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Favorite a post. Idempotent.
     */
    @PostMapping("/{postId}/favorite")
    public ResponseEntity<ApiResponse<Void>> favoritePost(@PathVariable Long postId) {
        Long currentUserId = resolveCurrentUserId();
        interactionService.favoritePost(currentUserId, postId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Remove favorite from a post. Idempotent.
     */
    @DeleteMapping("/{postId}/favorite")
    public ResponseEntity<ApiResponse<Void>> unfavoritePost(@PathVariable Long postId) {
        Long currentUserId = resolveCurrentUserId();
        interactionService.unfavoritePost(currentUserId, postId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Delete a comment. Only the comment author can delete.
     */
    @DeleteMapping("/{postId}/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        Long currentUserId = resolveCurrentUserId();
        postService.deleteComment(currentUserId, commentId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Like a comment. Idempotent.
     */
    @PostMapping("/{postId}/comments/{commentId}/like")
    public ResponseEntity<ApiResponse<Void>> likeComment(
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        Long currentUserId = resolveCurrentUserId();
        postService.likeComment(currentUserId, commentId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Remove like from a comment. Idempotent.
     */
    @DeleteMapping("/{postId}/comments/{commentId}/like")
    public ResponseEntity<ApiResponse<Void>> unlikeComment(
            @PathVariable Long postId,
            @PathVariable Long commentId) {
        Long currentUserId = resolveCurrentUserId();
        postService.unlikeComment(currentUserId, commentId);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * Report a post.
     */
    @PostMapping("/{postId}/reports")
    public ResponseEntity<ApiResponse<Void>> reportPost(
            @PathVariable Long postId,
            @Valid @RequestBody ReportPostRequest request) {
        Long currentUserId = resolveCurrentUserId();
        interactionService.reportPost(currentUserId, postId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(null));
    }

    /**
     * Resolves current user ID from the security context.
     */
    private Long resolveCurrentUserId() {
        return com.petcare.common.security.SecurityContextHelper.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "请先登录"));
    }
}
