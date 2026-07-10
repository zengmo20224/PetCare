package com.petcare.notification.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petcare.common.content.ContentSanitizer;
import com.petcare.common.pagination.PageResponse;
import com.petcare.notification.dto.AdminAnnouncementResponse;
import com.petcare.notification.dto.PublicAnnouncementResponse;
import com.petcare.notification.dto.UnreadCountResponse;
import com.petcare.notification.dto.UserNotificationResponse;
import com.petcare.notification.entity.Announcement;
import com.petcare.notification.entity.UserNotification;
import com.petcare.notification.mapper.AnnouncementMapper;
import com.petcare.notification.mapper.UserNotificationMapper;
import com.petcare.user.entity.User;
import com.petcare.user.service.UserService;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for announcements and user notifications.
 */
@Service
public class NotificationService {

    private final AnnouncementMapper announcementMapper;
    private final UserNotificationMapper notificationMapper;
    private final UserService userService;

    public NotificationService(AnnouncementMapper announcementMapper,
                                UserNotificationMapper notificationMapper,
                                UserService userService) {
        this.announcementMapper = announcementMapper;
        this.notificationMapper = notificationMapper;
        this.userService = userService;
    }

    // ==================== Announcements (public) ====================

    /**
     * Lists published announcements for anonymous readers, ordered by sort then time.
     */
    public List<PublicAnnouncementResponse> listPublishedAnnouncements(int limit) {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getStatus, "PUBLISHED")
                .eq(Announcement::getDeleted, 0)
                .orderByAsc(Announcement::getSort)
                .orderByDesc(Announcement::getCreateTime)
                .last("LIMIT " + Math.min(limit, 50));

        return announcementMapper.selectList(wrapper).stream()
                .map(a -> new PublicAnnouncementResponse(
                        a.getId(), a.getTitle(), a.getContent(),
                        a.getSort(), a.getCreateTime()))
                .toList();
    }

    /**
     * Gets a single published announcement by ID.
     */
    public PublicAnnouncementResponse getPublishedAnnouncement(Long id) {
        Announcement ann = announcementMapper.selectOne(
                new LambdaQueryWrapper<Announcement>()
                        .eq(Announcement::getId, id)
                        .eq(Announcement::getStatus, "PUBLISHED")
                        .eq(Announcement::getDeleted, 0)
        );
        if (ann == null) {
            return null;
        }
        return new PublicAnnouncementResponse(
                ann.getId(), ann.getTitle(), ann.getContent(),
                ann.getSort(), ann.getCreateTime());
    }

    // ==================== User Notifications ====================

    /**
     * Lists current user's notifications, most recent first.
     */
    public PageResponse<UserNotificationResponse> listNotifications(Long userId, int page, int size) {
        LambdaQueryWrapper<UserNotification> wrapper = new LambdaQueryWrapper<UserNotification>()
                .eq(UserNotification::getUserId, userId)
                .eq(UserNotification::getDeleted, 0)
                .orderByDesc(UserNotification::getCreateTime);

        Page<UserNotification> pageResult = notificationMapper.selectPage(new Page<>(page, size), wrapper);
        List<UserNotification> notifs = pageResult.getRecords();

        // Batch load actor info
        Map<Long, User> actorById = loadUsers(
                notifs.stream().map(UserNotification::getActorId).filter(java.util.Objects::nonNull).toList()
        );

        List<UserNotificationResponse> items = notifs.stream()
                .map(n -> {
                    User actor = n.getActorId() != null ? actorById.get(n.getActorId()) : null;
                    return new UserNotificationResponse(
                            n.getId(), n.getType(), n.getPostId(), n.getCommentId(),
                            n.getContent(),
                            n.getIsRead() != null && n.getIsRead() == 1,
                            actor != null ? actor.getNickname() : null,
                            actor != null ? actor.getAvatarUrl() : null,
                            n.getCreateTime()
                    );
                })
                .toList();
        return PageResponse.of(items, pageResult.getTotal(), page, size);
    }

    /**
     * Gets unread count for current user.
     */
    public UnreadCountResponse getUnreadCount(Long userId) {
        Long count = notificationMapper.selectCount(
                new LambdaQueryWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, userId)
                        .eq(UserNotification::getIsRead, 0)
                        .eq(UserNotification::getDeleted, 0)
        );
        return new UnreadCountResponse(count.intValue());
    }

    /**
     * Marks a single notification as read.
     */
    public void markAsRead(Long userId, Long notificationId) {
        UserNotification notif = notificationMapper.selectOne(
                new LambdaQueryWrapper<UserNotification>()
                        .eq(UserNotification::getId, notificationId)
                        .eq(UserNotification::getUserId, userId)
                        .eq(UserNotification::getDeleted, 0)
        );
        if (notif == null) {
            return;
        }
        if (notif.getIsRead() == null || notif.getIsRead() == 0) {
            notif.setIsRead(1);
            notificationMapper.updateById(notif);
        }
    }

    /**
     * Marks all notifications as read for current user.
     * Single UPDATE statement instead of per-row updates to avoid N round-trips.
     */
    public void markAllAsRead(Long userId) {
        notificationMapper.update(null,
                new LambdaUpdateWrapper<UserNotification>()
                        .eq(UserNotification::getUserId, userId)
                        .eq(UserNotification::getIsRead, 0)
                        .eq(UserNotification::getDeleted, 0)
                        .set(UserNotification::getIsRead, 1)
        );
    }

    // ==================== Internal: notification creation ====================

    /**
     * Creates a notification record. Called by interaction services when
     * someone likes / comments on / favorites another user's post.
     * Skips if actor is the post author (no self-notification).
     */
    public void createNotification(Long recipientUserId, Long actorId, String type,
                                     Long postId, Long commentId, String content) {
        // Don't notify self
        if (actorId != null && actorId.equals(recipientUserId)) {
            return;
        }

        UserNotification notif = new UserNotification();
        notif.setId(IdWorker.getId());
        notif.setUserId(recipientUserId);
        notif.setActorId(actorId);
        notif.setType(type);
        notif.setPostId(postId);
        notif.setCommentId(commentId);
        notif.setContent(content);
        notif.setIsRead(0);
        notificationMapper.insert(notif);
    }

    // ==================== Admin: Announcement Management ====================

    /**
     * Lists all announcements for admin (includes DRAFT and deleted).
     */
    public PageResponse<AdminAnnouncementResponse> adminListAnnouncements(int page, int size) {
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<Announcement>()
                .eq(Announcement::getDeleted, 0)
                .orderByAsc(Announcement::getSort)
                .orderByDesc(Announcement::getCreateTime);

        Page<Announcement> pageResult = announcementMapper.selectPage(new Page<>(page, size), wrapper);
        List<AdminAnnouncementResponse> items = pageResult.getRecords().stream()
                .map(a -> new AdminAnnouncementResponse(
                        a.getId(), a.getTitle(), a.getContent(),
                        a.getStatus(), a.getSort(),
                        a.getCreateTime(), a.getUpdateTime()))
                .toList();
        return PageResponse.of(items, pageResult.getTotal(), page, size);
    }

    /**
     * Creates a new announcement.
     */
    @org.springframework.transaction.annotation.Transactional
    public AdminAnnouncementResponse createAnnouncement(String title, String content, String status, Integer sort) {
        Announcement ann = new Announcement();
        ann.setId(IdWorker.getId());
        // M3 防存储型 XSS：写入前对自由文本做 HTML 转义
        ann.setTitle(ContentSanitizer.escapePlainText(title));
        ann.setContent(ContentSanitizer.escapePlainText(content));
        ann.setStatus(status != null ? status : "PUBLISHED");
        ann.setSort(sort != null ? sort : 0);
        announcementMapper.insert(ann);

        return new AdminAnnouncementResponse(
                ann.getId(), ann.getTitle(), ann.getContent(),
                ann.getStatus(), ann.getSort(),
                ann.getCreateTime(), ann.getUpdateTime());
    }

    /**
     * Updates an existing announcement.
     */
    @org.springframework.transaction.annotation.Transactional
    public AdminAnnouncementResponse updateAnnouncement(Long id, String title, String content, String status, Integer sort) {
        Announcement ann = announcementMapper.selectOne(
                new LambdaQueryWrapper<Announcement>()
                        .eq(Announcement::getId, id)
                        .eq(Announcement::getDeleted, 0)
        );
        if (ann == null) {
            return null;
        }

        if (title != null) ann.setTitle(ContentSanitizer.escapePlainText(title));
        if (content != null) ann.setContent(ContentSanitizer.escapePlainText(content));
        if (status != null) ann.setStatus(status);
        if (sort != null) ann.setSort(sort);
        announcementMapper.updateById(ann);

        return new AdminAnnouncementResponse(
                ann.getId(), ann.getTitle(), ann.getContent(),
                ann.getStatus(), ann.getSort(),
                ann.getCreateTime(), ann.getUpdateTime());
    }

    /**
     * Deletes an announcement (logical delete).
     */
    @org.springframework.transaction.annotation.Transactional
    public void deleteAnnouncement(Long id) {
        Announcement ann = announcementMapper.selectOne(
                new LambdaQueryWrapper<Announcement>()
                        .eq(Announcement::getId, id)
                        .eq(Announcement::getDeleted, 0)
        );
        if (ann == null) {
            return;
        }
        announcementMapper.deleteById(id);
    }

    // ==================== Private Helpers ====================

    private Map<Long, User> loadUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> distinctIds = userIds.stream().distinct().toList();
        return userService.listByIds(distinctIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }
}
