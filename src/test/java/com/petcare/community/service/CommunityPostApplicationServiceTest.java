package com.petcare.community.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.pagination.PageResponse;
import com.petcare.community.domain.CommunityContentType;
import com.petcare.community.dto.CommentCreateRequest;
import com.petcare.community.dto.CommentResponse;
import com.petcare.community.dto.PostCreateRequest;
import com.petcare.community.dto.PostDetailResponse;
import com.petcare.community.dto.PostResponse;
import com.petcare.community.dto.PublicCommentFlatResponse;
import com.petcare.community.dto.TopicResponse;
import com.petcare.community.entity.Post;
import com.petcare.community.entity.PostComment;
import com.petcare.community.entity.PostImage;
import com.petcare.community.entity.Topic;
import com.petcare.community.mapper.PostCommentMapper;
import com.petcare.community.mapper.PostImageMapper;
import com.petcare.community.mapper.PostMapper;
import com.petcare.community.mapper.TopicMapper;
import com.petcare.moderation.domain.MatchedSensitiveWord;
import com.petcare.moderation.dto.ContentReviewResult;
import com.petcare.moderation.service.ContentModerationService;
import com.petcare.notification.service.NotificationService;
import com.petcare.user.entity.Pet;
import com.petcare.user.entity.User;
import com.petcare.user.mapper.PetMapper;
import com.petcare.user.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for CommunityPostApplicationService.
 * Uses Mockito to mock mapper and moderation dependencies — no Spring context.
 */
@ExtendWith(MockitoExtension.class)
class CommunityPostApplicationServiceTest {

    @Mock
    private PostMapper postMapper;

    @Mock
    private PostCommentMapper commentMapper;

    @Mock
    private PostImageMapper imageMapper;

    @Mock
    private TopicMapper topicMapper;

    @Mock
    private PetMapper petMapper;

    @Mock
    private ContentModerationService moderationService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserService userService;

    /** M8.3：AI 审核钩子的可选注入（mock 的 getIfUnique 默认返回 null = 未启用，符合单测语义）。 */
    @Mock
    private org.springframework.beans.factory.ObjectProvider<com.petcare.ai.service.AiModerationReviewService> aiModerationReviewServiceProvider;

    @InjectMocks
    private CommunityPostApplicationService service;

    private static final Long USER_ID = 1001L;
    private static final Long TOPIC_ID = 2001L;
    private static final Long POST_ID = 3001L;
    private static final Long PET_ID = 4001L;
    private static final Long COMMENT_ID = 5001L;

    // ==================== Topic Queries ====================

    @Nested
    @DisplayName("listTopics")
    class ListTopicsTests {

        @Test
        @DisplayName("Returns only ACTIVE non-deleted topics sorted by sort asc, createTime desc")
        void returnsActiveTopicsSorted() {
            // Arrange
            Topic topic1 = buildTopic(TOPIC_ID, "日常分享", "分享宠物日常", 1, "ACTIVE");
            topic1.setCreateTime(LocalDateTime.of(2026, 6, 1, 10, 0));
            Topic topic2 = buildTopic(2002L, "健康问答", "宠物健康", 2, "ACTIVE");
            topic2.setCreateTime(LocalDateTime.of(2026, 6, 2, 10, 0));

            when(topicMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(topic1, topic2));

            // Act
            List<TopicResponse> result = service.listTopics();

            // Assert
            assertThat(result).hasSize(2);
            assertThat(result.get(0).name()).isEqualTo("日常分享");
            assertThat(result.get(1).name()).isEqualTo("健康问答");
            verify(topicMapper).selectList(any(LambdaQueryWrapper.class));
        }
    }

    @Nested
    @DisplayName("getTopic")
    class GetTopicTests {

        @Test
        @DisplayName("Returns topic when exists and active")
        void returnsExistingActiveTopic() {
            // Arrange
            Topic topic = buildTopic(TOPIC_ID, "日常分享", "分享宠物日常", 1, "ACTIVE");
            topic.setCreateTime(LocalDateTime.of(2026, 6, 1, 10, 0));
            when(topicMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(topic);

            // Act
            TopicResponse result = service.getTopic(TOPIC_ID);

            // Assert
            assertThat(result.id()).isEqualTo(TOPIC_ID);
            assertThat(result.name()).isEqualTo("日常分享");
            assertThat(result.description()).isEqualTo("分享宠物日常");
        }

        @Test
        @DisplayName("Throws COMMUNITY_TOPIC_NOT_FOUND when not found or not active")
        void throwsWhenTopicNotFound() {
            // Arrange
            when(topicMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> service.getTopic(9999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.COMMUNITY_TOPIC_NOT_FOUND);
        }
    }

    // ==================== Post Creation ====================

    @Nested
    @DisplayName("createPost")
    class CreatePostTests {

        @Test
        @DisplayName("With no sensitive words -> PUBLISHED status, publishTime set")
        void publishedWhenNoSensitiveWords() {
            // Arrange
            PostCreateRequest request = new PostCreateRequest(
                    TOPIC_ID, PET_ID, "我家猫咪", "今天天气真好", List.of(), List.of());
            Topic topic = buildTopic(TOPIC_ID, "日常分享", "分享日常", 1, "ACTIVE");
            when(topicMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(topic);
            when(petMapper.selectById(PET_ID)).thenReturn(buildPet(PET_ID, USER_ID));

            ContentReviewResult cleanResult = new ContentReviewResult(0, "PUBLISHED", "APPROVED", List.of());
            when(moderationService.moderateAndRecord(
                    eq(CommunityContentType.POST), any(), eq(USER_ID), eq("我家猫咪 今天天气真好")))
                    .thenReturn(cleanResult);

            // Simulate snowflake ID assignment on insert
            doAnswerSetIdOnPostInsert(POST_ID);

            // Act
            PostResponse result = service.createPost(USER_ID, request);

            // Assert
            assertThat(result.status()).isEqualTo("PUBLISHED");
            assertThat(result.publishTime()).isNotNull();
            assertThat(result.title()).isEqualTo("我家猫咪");
            assertThat(result.content()).isEqualTo("今天天气真好");
            verify(postMapper).insert(any(Post.class));
            ArgumentCaptor<Long> contentIdCaptor = ArgumentCaptor.forClass(Long.class);
            verify(moderationService).moderateAndRecord(
                    eq(CommunityContentType.POST), contentIdCaptor.capture(), eq(USER_ID),
                    eq("我家猫咪 今天天气真好"));
            assertThat(contentIdCaptor.getValue()).isNotNull();
        }

        @Test
        @DisplayName("With level 1 sensitive words -> PENDING_REVIEW status")
        void pendingReviewWhenLevel1SensitiveWords() {
            // Arrange
            PostCreateRequest request = new PostCreateRequest(
                    TOPIC_ID, PET_ID, "敏感标题", "敏感内容", List.of(), List.of());
            Topic topic = buildTopic(TOPIC_ID, "日常分享", "分享日常", 1, "ACTIVE");
            when(topicMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(topic);
            when(petMapper.selectById(PET_ID)).thenReturn(buildPet(PET_ID, USER_ID));

            MatchedSensitiveWord matchedWord = mock(MatchedSensitiveWord.class);
            ContentReviewResult level1Result = new ContentReviewResult(1, "PENDING_REVIEW", "PENDING",
                    List.of(matchedWord));
            when(moderationService.moderateAndRecord(any(), any(), eq(USER_ID), any()))
                    .thenReturn(level1Result);

            doAnswerSetIdOnPostInsert(POST_ID);

            // Act
            PostResponse result = service.createPost(USER_ID, request);

            // Assert
            assertThat(result.status()).isEqualTo("PENDING_REVIEW");
            assertThat(result.publishTime()).isNull();
        }

        @Test
        @DisplayName("With level 3 sensitive words -> REJECTED status")
        void rejectedWhenLevel3SensitiveWords() {
            // Arrange
            PostCreateRequest request = new PostCreateRequest(
                    TOPIC_ID, PET_ID, "严重违规", "严重违规内容", List.of(), List.of());
            Topic topic = buildTopic(TOPIC_ID, "日常分享", "分享日常", 1, "ACTIVE");
            when(topicMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(topic);
            when(petMapper.selectById(PET_ID)).thenReturn(buildPet(PET_ID, USER_ID));

            MatchedSensitiveWord matchedWord = mock(MatchedSensitiveWord.class);
            ContentReviewResult level3Result = new ContentReviewResult(3, "REJECTED", "REJECTED",
                    List.of(matchedWord));
            when(moderationService.moderateAndRecord(any(), any(), eq(USER_ID), any()))
                    .thenReturn(level3Result);

            doAnswerSetIdOnPostInsert(POST_ID);

            // Act
            PostResponse result = service.createPost(USER_ID, request);

            // Assert
            assertThat(result.status()).isEqualTo("REJECTED");
            assertThat(result.publishTime()).isNull();
        }

        @Test
        @DisplayName("With invalid topicId -> throws COMMUNITY_TOPIC_NOT_FOUND")
        void throwsWhenTopicIdInvalid() {
            // Arrange
            PostCreateRequest request = new PostCreateRequest(
                    9999L, PET_ID, "标题", "内容", List.of(), List.of());
            when(topicMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> service.createPost(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.COMMUNITY_TOPIC_NOT_FOUND);
            verify(postMapper, never()).insert(any(Post.class));
        }

        @Test
        @DisplayName("With petId not owned by current user -> throws FORBIDDEN")
        void throwsWhenPetDoesNotBelongToCurrentUser() {
            PostCreateRequest request = new PostCreateRequest(
                    TOPIC_ID, PET_ID, "标题", "内容", List.of(), List.of());
            Topic topic = buildTopic(TOPIC_ID, "日常分享", "分享日常", 1, "ACTIVE");
            when(topicMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(topic);

            Pet pet = new Pet();
            pet.setId(PET_ID);
            pet.setUserId(9999L);
            when(petMapper.selectById(PET_ID)).thenReturn(pet);

            assertThatThrownBy(() -> service.createPost(USER_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.FORBIDDEN);
            verify(postMapper, never()).insert(any(Post.class));
            verify(moderationService, never()).moderateAndRecord(any(), any(), any(), any());
        }
    }

    // ==================== Post Listing ====================

    @Nested
    @DisplayName("listPosts")
    class ListPostsTests {

        @Test
        @DisplayName("Returns only PUBLISHED non-deleted posts")
        void returnsPublishedPosts() {
            // Arrange
            Post post1 = buildPost(POST_ID, USER_ID, PET_ID, TOPIC_ID, "标题1", "内容1", "PUBLISHED");
            post1.setPublishTime(LocalDateTime.of(2026, 6, 1, 10, 0));

            Page<Post> pageResult = new Page<>(1, 10);
            pageResult.setRecords(List.of(post1));
            pageResult.setTotal(1);

            when(postMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageResult);

            // Act
            PageResponse<PostResponse> result = service.listPosts(TOPIC_ID, 1, 10);

            // Assert
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).status()).isEqualTo("PUBLISHED");
            assertThat(result.getTotal()).isEqualTo(1);
            verify(postMapper).selectPage(any(Page.class), any(LambdaQueryWrapper.class));
        }
    }

    // ==================== Post Detail ====================

    @Nested
    @DisplayName("getPostDetail")
    class GetPostDetailTests {

        @Test
        @DisplayName("Returns published post detail with images")
        void returnsPublishedPostDetailWithImages() {
            // Arrange
            Post post = buildPost(POST_ID, 9999L, PET_ID, TOPIC_ID, "猫咪照片", "看看我家猫", "PUBLISHED");
            post.setPublishTime(LocalDateTime.of(2026, 6, 1, 10, 0));
            when(postMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(post);

            PostImage img1 = new PostImage();
            img1.setImageUrl("https://example.com/cat1.jpg");
            img1.setSort(1);
            PostImage img2 = new PostImage();
            img2.setImageUrl("https://example.com/cat2.jpg");
            img2.setSort(2);
            when(imageMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(img1, img2));

            // Act
            PostDetailResponse result = service.getPostDetail(USER_ID, POST_ID);

            // Assert
            assertThat(result.id()).isEqualTo(POST_ID);
            assertThat(result.title()).isEqualTo("猫咪照片");
            assertThat(result.imageUrls()).containsExactly(
                    "https://example.com/cat1.jpg",
                    "https://example.com/cat2.jpg"
            );
        }

        @Test
        @DisplayName("Anonymous user viewing non-published post -> throws COMMUNITY_POST_NOT_VISIBLE")
        void anonymousCannotViewNonPublishedPost() {
            // Arrange
            Post post = buildPost(POST_ID, 8888L, PET_ID, TOPIC_ID, "待审核", "审核中", "PENDING_REVIEW");
            when(postMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(post);

            // Act & Assert — currentUserId is null (anonymous)
            assertThatThrownBy(() -> service.getPostDetail(null, POST_ID))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.COMMUNITY_POST_NOT_VISIBLE);
        }

        @Test
        @DisplayName("Non-existent post -> throws COMMUNITY_POST_NOT_FOUND")
        void throwsWhenPostNotFound() {
            // Arrange
            when(postMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            // Act & Assert
            assertThatThrownBy(() -> service.getPostDetail(USER_ID, 9999L))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }
    }

    // ==================== Comment Creation ====================

    @Nested
    @DisplayName("createComment")
    class CreateCommentTests {

        @Test
        @DisplayName("With no sensitive words -> PUBLISHED, comment count incremented")
        void publishedCommentIncrementsCount() {
            // Arrange
            Post post = buildPost(POST_ID, USER_ID, PET_ID, TOPIC_ID, "标题", "内容", "PUBLISHED");
            post.setCommentCount(3);
            when(postMapper.selectOne(any(LambdaQueryWrapper.class)))
                    .thenReturn(post);

            ContentReviewResult cleanResult = new ContentReviewResult(0, "PUBLISHED", "APPROVED", List.of());
            when(moderationService.moderateAndRecord(
                    eq(CommunityContentType.COMMENT), any(), eq(USER_ID), eq("好可爱啊")))
                    .thenReturn(cleanResult);

            CommentCreateRequest request = new CommentCreateRequest(null, "好可爱啊");

            // Act
            CommentResponse result = service.createComment(USER_ID, POST_ID, request);

            // Assert
            assertThat(result.status()).isEqualTo("PUBLISHED");
            assertThat(result.content()).isEqualTo("好可爱啊");
            verify(commentMapper).insert(any(PostComment.class));
            verify(postMapper).updateById(any(Post.class));
            assertThat(post.getCommentCount()).isEqualTo(4);
            ArgumentCaptor<Long> contentIdCaptor = ArgumentCaptor.forClass(Long.class);
            verify(moderationService).moderateAndRecord(
                    eq(CommunityContentType.COMMENT), contentIdCaptor.capture(), eq(USER_ID),
                    eq("好可爱啊"));
            assertThat(contentIdCaptor.getValue()).isNotNull();
        }

        @Test
        @DisplayName("With level 3 words -> REJECTED, count NOT incremented")
        void rejectedCommentDoesNotIncrementCount() {
            // Arrange
            Post post = buildPost(POST_ID, USER_ID, PET_ID, TOPIC_ID, "标题", "内容", "PUBLISHED");
            post.setCommentCount(5);
            when(postMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(post);

            MatchedSensitiveWord matchedWord = mock(MatchedSensitiveWord.class);
            ContentReviewResult level3Result = new ContentReviewResult(3, "REJECTED", "REJECTED",
                    List.of(matchedWord));
            when(moderationService.moderateAndRecord(any(), any(), eq(USER_ID), any()))
                    .thenReturn(level3Result);

            CommentCreateRequest request = new CommentCreateRequest(null, "严重违规评论");

            // Act
            CommentResponse result = service.createComment(USER_ID, POST_ID, request);

            // Assert
            assertThat(result.status()).isEqualTo("REJECTED");
            verify(commentMapper).insert(any(PostComment.class));
            verify(postMapper, never()).updateById(any(Post.class));
            assertThat(post.getCommentCount()).isEqualTo(5);
        }

        @Test
        @DisplayName("Non-published post -> throws COMMUNITY_POST_NOT_FOUND")
        void throwsWhenPostNotPublished() {
            // Arrange
            when(postMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            CommentCreateRequest request = new CommentCreateRequest(null, "评论内容");

            // Act & Assert
            assertThatThrownBy(() -> service.createComment(USER_ID, POST_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }

        @Test
        @DisplayName("Invalid parentId -> throws COMMUNITY_COMMENT_NOT_FOUND")
        void throwsWhenParentCommentNotFound() {
            // Arrange
            Post post = buildPost(POST_ID, USER_ID, PET_ID, TOPIC_ID, "标题", "内容", "PUBLISHED");
            when(postMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(post);
            when(commentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            CommentCreateRequest request = new CommentCreateRequest(9999L, "回复评论");

            // Act & Assert
            assertThatThrownBy(() -> service.createComment(USER_ID, POST_ID, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.COMMUNITY_COMMENT_NOT_FOUND);
        }
    }

    // ==================== Comment Listing ====================

    @Nested
    @DisplayName("listComments")
    class ListCommentsTests {

        @Test
        @DisplayName("Returns only PUBLISHED comments")
        void returnsPublishedComments() {
            // Arrange
            when(postMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            PostComment comment1 = new PostComment();
            comment1.setId(COMMENT_ID);
            comment1.setPostId(POST_ID);
            comment1.setUserId(USER_ID);
            comment1.setContent("好可爱");
            comment1.setStatus("PUBLISHED");
            comment1.setLikeCount(2);
            comment1.setCreateTime(LocalDateTime.of(2026, 6, 1, 12, 0));

            Page<PostComment> pageResult = new Page<>(1, 10);
            pageResult.setRecords(List.of(comment1));
            pageResult.setTotal(1);

            when(commentMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                    .thenReturn(pageResult);

            // Act
            PageResponse<CommentResponse> result = service.listComments(POST_ID, 1, 10);

            // Assert
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).status()).isEqualTo("PUBLISHED");
            assertThat(result.getItems().get(0).content()).isEqualTo("好可爱");
        }
    }

    // ==================== Helper Methods ====================

    private Topic buildTopic(Long id, String name, String description, int sort, String status) {
        Topic topic = new Topic();
        topic.setId(id);
        topic.setName(name);
        topic.setDescription(description);
        topic.setSort(sort);
        topic.setStatus(status);
        return topic;
    }

    private Pet buildPet(Long id, Long userId) {
        Pet pet = new Pet();
        pet.setId(id);
        pet.setUserId(userId);
        pet.setDeleted(0);
        return pet;
    }

    private Post buildPost(Long id, Long userId, Long petId, Long topicId,
                           String title, String content, String status) {
        Post post = new Post();
        post.setId(id);
        post.setUserId(userId);
        post.setPetId(petId);
        post.setTopicId(topicId);
        post.setTitle(title);
        post.setContent(content);
        post.setStatus(status);
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCommentCount(0);
        post.setFavoriteCount(0);
        post.setCreateTime(LocalDateTime.of(2026, 6, 1, 10, 0));
        return post;
    }

    /**
     * Configures the postMapper mock to simulate MyBatis-Plus snowflake ID assignment
     * when insert() is called. Since the service calls moderation before insert,
     * and uses post.getId() in the moderation call, the ID will be null at that point.
     */
    private void doAnswerSetIdOnPostInsert(Long assignedId) {
        when(postMapper.insert(any(Post.class))).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            post.setId(assignedId);
            return 1;
        });
    }

    // ==================== Flat Comment List ====================

    @Nested
    @DisplayName("listPublicCommentsFlat")
    class ListPublicCommentsFlatTests {

        @Test
        @DisplayName("Returns flat list with authorName and replyToName for nested replies")
        void returnsFlatListWithAuthorAndReplyTo() {
            // Arrange — post exists
            when(postMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);

            // C1: top-level comment by USER_ID (1001)
            // C2: reply to C1 by 1002
            // C3: reply to C2 by 1001 (reply-to-reply, invisible in tree but visible here)
            PostComment c1 = buildComment(5001L, POST_ID, USER_ID, null, "一楼", 0);
            PostComment c2 = buildComment(5002L, POST_ID, 1002L, 5001L, "回复一楼", 1);
            PostComment c3 = buildComment(5003L, POST_ID, USER_ID, 5002L, "回复二楼的回复", 0);

            when(commentMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(List.of(c1, c2, c3));

            // Authors: 1001 -> "爱猫人", 1002 -> "狗主人"
            User u1 = buildUser(USER_ID, "爱猫人");
            User u2 = buildUser(1002L, "狗主人");
            when(userService.listByIds(any())).thenReturn(List.of(u1, u2));

            // Act
            List<PublicCommentFlatResponse> result = service.listPublicCommentsFlat(POST_ID, null);

            // Assert — all three visible (no depth cap)
            assertThat(result).hasSize(3);
            // C1: top-level, no replyTo
            assertThat(result.get(0).content()).isEqualTo("一楼");
            assertThat(result.get(0).authorName()).isEqualTo("爱猫人");
            assertThat(result.get(0).replyToName()).isNull();
            // C2: reply to C1, replyToName = C1 author
            assertThat(result.get(1).content()).isEqualTo("回复一楼");
            assertThat(result.get(1).authorName()).isEqualTo("狗主人");
            assertThat(result.get(1).replyToName()).isEqualTo("爱猫人");
            // C3: reply to C2 (reply-to-reply), replyToName = C2 author — this is the bug scenario
            assertThat(result.get(2).content()).isEqualTo("回复二楼的回复");
            assertThat(result.get(2).authorName()).isEqualTo("爱猫人");
            assertThat(result.get(2).replyToName()).isEqualTo("狗主人");
        }

        @Test
        @DisplayName("Throws COMMUNITY_POST_NOT_FOUND when post does not exist")
        void throwsWhenPostNotFound() {
            when(postMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L);

            assertThatThrownBy(() -> service.listPublicCommentsFlat(9999L, null))
                    .isInstanceOf(BusinessException.class)
                    .extracting("code").isEqualTo(ErrorCode.COMMUNITY_POST_NOT_FOUND);
        }

        @Test
        @DisplayName("Returns empty list when post has no comments")
        void returnsEmptyWhenNoComments() {
            when(postMapper.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
            when(commentMapper.selectList(any(LambdaQueryWrapper.class)))
                    .thenReturn(Collections.emptyList());

            List<PublicCommentFlatResponse> result = service.listPublicCommentsFlat(POST_ID, null);
            assertThat(result).isEmpty();
        }

        private PostComment buildComment(Long id, Long postId, Long userId,
                                          Long parentId, String content, int likeCount) {
            PostComment c = new PostComment();
            c.setId(id);
            c.setPostId(postId);
            c.setUserId(userId);
            c.setParentId(parentId);
            c.setContent(content);
            c.setStatus("PUBLISHED");
            c.setLikeCount(likeCount);
            c.setCreateTime(LocalDateTime.of(2026, 6, 1, 10, 0));
            return c;
        }

        private User buildUser(Long id, String nickname) {
            User u = new User();
            u.setId(id);
            u.setNickname(nickname);
            return u;
        }
    }
}
