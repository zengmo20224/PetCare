package com.petcare.community.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.petcare.common.serialization.SnowflakeIdSerializer;

import java.time.LocalDateTime;

/**
 * Public-facing comment in a flat list (Douyin-style).
 * <p>
 * Unlike {@link PublicCommentTreeResponse}, this is a single-level list: each
 * comment carries the author info plus an optional {@code replyToName} so the
 * UI can render "{@code authorName @ replyToName}" without nested structure.
 * This avoids the two-level cap of the tree response, where a reply to a reply
 * would be invisible.
 */
public record PublicCommentFlatResponse(
        @JsonSerialize(using = SnowflakeIdSerializer.class) Long id,
        @JsonSerialize(using = SnowflakeIdSerializer.class) Long parentId,
        String content,
        Integer likeCount,
        LocalDateTime createTime,
        String authorName,
        String authorAvatar,
        @JsonSerialize(using = SnowflakeIdSerializer.class) Long authorUserId,
        @JsonSerialize(using = SnowflakeIdSerializer.class) Long replyToUserId,
        String replyToName
) {
}
