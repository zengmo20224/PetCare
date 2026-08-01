package com.petcare.notification.service;

import com.petcare.common.pagination.PageResponse;
import com.petcare.notification.dto.AdminAnnouncementResponse;
import com.petcare.notification.entity.Announcement;
import com.petcare.notification.mapper.AnnouncementMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 回归守卫：管理端公告列表的状态过滤（AGENTS.md §5 查询契约）。
 *
 * <p>背景：前端公告管理页有状态下拉框（PUBLISHED/DRAFT），通过 query 参数 status 请求
 * {@code GET /api/v1/admin/announcements?status=...}。后端 Controller 与 Service 必须真正
 * 按该参数过滤，否则"状态查询无法使用"。
 *
 * <p>用 H2 内存库 + @Transactional 回滚，保证用例间隔离。
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationAnnouncementQueryTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private AnnouncementMapper announcementMapper;

    private Announcement seed(String title, String status, int sort) {
        Announcement a = new Announcement();
        a.setId(System.nanoTime() + (long) title.hashCode());
        a.setTitle(title);
        a.setContent("content-" + title);
        a.setStatus(status);
        a.setSort(sort);
        announcementMapper.insert(a);
        return a;
    }

    @Test
    @DisplayName("status 为 null 时返回全部状态（PUBLISHED + DRAFT）")
    void listAllWhenStatusIsNull() {
        seed("A-pub", "PUBLISHED", 1);
        seed("B-draft", "DRAFT", 2);

        PageResponse<AdminAnnouncementResponse> page = notificationService.adminListAnnouncements(1, 20, null);

        assertThat(page.getItems()).extracting(AdminAnnouncementResponse::title)
                .containsExactlyInAnyOrder("A-pub", "B-draft");
    }

    @Test
    @DisplayName("status 为空字符串时同样返回全部（不过滤）")
    void listAllWhenStatusIsBlank() {
        seed("C-pub", "PUBLISHED", 1);
        seed("D-draft", "DRAFT", 2);

        PageResponse<AdminAnnouncementResponse> page = notificationService.adminListAnnouncements(1, 20, "  ");

        assertThat(page.getItems()).extracting(AdminAnnouncementResponse::title)
                .containsExactlyInAnyOrder("C-pub", "D-draft");
    }

    @Test
    @DisplayName("status=PUBLISHED 只返回已发布公告")
    void filterPublishedOnly() {
        seed("E-pub", "PUBLISHED", 1);
        seed("F-draft", "DRAFT", 2);

        PageResponse<AdminAnnouncementResponse> page = notificationService.adminListAnnouncements(1, 20, "PUBLISHED");

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).title()).isEqualTo("E-pub");
        assertThat(page.getItems().get(0).status()).isEqualTo("PUBLISHED");
    }

    @Test
    @DisplayName("status=DRAFT 只返回草稿公告")
    void filterDraftOnly() {
        seed("G-pub", "PUBLISHED", 1);
        seed("H-draft", "DRAFT", 2);

        PageResponse<AdminAnnouncementResponse> page = notificationService.adminListAnnouncements(1, 20, "DRAFT");

        assertThat(page.getItems()).hasSize(1);
        assertThat(page.getItems().get(0).title()).isEqualTo("H-draft");
        assertThat(page.getItems().get(0).status()).isEqualTo("DRAFT");
    }

    @Test
    @DisplayName("逻辑删除的公告不出现在结果中（即使匹配状态）")
    void excludeLogicallyDeleted() {
        seed("I-live", "PUBLISHED", 1);
        Announcement deleted = seed("J-deleted", "PUBLISHED", 2);
        // 走 MyBatis-Plus 标准逻辑删除（将 deleted 置 1），而非直接 updateById 改字段
        announcementMapper.deleteById(deleted.getId());

        PageResponse<AdminAnnouncementResponse> page = notificationService.adminListAnnouncements(1, 20, "PUBLISHED");

        assertThat(page.getItems()).extracting(AdminAnnouncementResponse::title)
                .containsExactly("I-live")
                .doesNotContain("J-deleted");
    }
}
