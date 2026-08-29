package com.petcare.community.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.petcare.common.serialization.SnowflakeIdSerializer;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Public-facing post detail for anonymous readers.
 *
 * <p>Strips all private identifiers and internal moderation fields:
 * no userId, petId, status, riskLevel, rejectReason or deleted markers.
 */
public record PublicPostDetailResponse(
        @JsonSerialize(using = SnowflakeIdSerializer.class) Long id,
        @JsonSerialize(using = SnowflakeIdSerializer.class) Long topicId,
        String title,
        String content,
        Integer viewCount,
        Integer likeCount,
        Integer commentCount,
        Integer favoriteCount,
        LocalDateTime publishTime,
        LocalDateTime createTime,
        List<String> imageUrls,
        List<String> tags,
        String authorName,
        String authorAvatar,
        boolean likedByMe,
        boolean favoritedByMe
) {
}
