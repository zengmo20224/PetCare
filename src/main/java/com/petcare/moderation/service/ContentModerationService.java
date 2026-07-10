package com.petcare.moderation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.petcare.community.domain.ReviewStatus;
import com.petcare.moderation.domain.ContentModerationPolicy;
import com.petcare.moderation.domain.MatchedSensitiveWord;
import com.petcare.moderation.domain.SensitiveWordMatcher;
import com.petcare.moderation.dto.ContentReviewResult;
import com.petcare.moderation.entity.ContentReviewRecord;
import com.petcare.moderation.entity.SensitiveWord;
import com.petcare.moderation.mapper.ContentReviewRecordMapper;
import com.petcare.moderation.mapper.SensitiveWordMapper;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Service for content moderation.
 * Loads active sensitive words, runs matching, applies policy, and creates review records.
 */
@Service
public class ContentModerationService {

    private static final Logger log = LoggerFactory.getLogger(ContentModerationService.class);

    private final SensitiveWordMapper sensitiveWordMapper;
    private final ContentReviewRecordMapper reviewRecordMapper;

    public ContentModerationService(SensitiveWordMapper sensitiveWordMapper,
                                     ContentReviewRecordMapper reviewRecordMapper) {
        this.sensitiveWordMapper = sensitiveWordMapper;
        this.reviewRecordMapper = reviewRecordMapper;
    }

    /**
     * Moderates content text and creates a review record.
     *
     * @param contentType the type of content (POST or COMMENT)
     * @param contentId   the ID of the content entity
     * @param userId      the ID of the content author
     * @param text        the text to moderate (title + content for posts, content for comments)
     * @return the moderation result with status decisions
     */
    public ContentReviewResult moderateAndRecord(String contentType, Long contentId,
                                                  Long userId, String text) {
        try {
            List<SensitiveWord> activeWords = loadActiveSensitiveWords();
            List<SensitiveWordMatcher.SensitiveWordEntry> entries = activeWords.stream()
                    .map(w -> new SensitiveWordMatcher.SensitiveWordEntry(w.getWord(), w.getLevel()))
                    .toList();

            List<MatchedSensitiveWord> matched = SensitiveWordMatcher.match(text, entries);
            ContentReviewResult result = ContentModerationPolicy.evaluate(matched);

            ContentReviewRecord record = new ContentReviewRecord();
            record.setContentType(contentType);
            record.setContentId(contentId);
            record.setUserId(userId);
            record.setRiskLevel(result.riskLevel());
            record.setMatchedWords(SensitiveWordMatcher.formatMatchedWords(result.matchedWords()));
            record.setReviewStatus(result.reviewStatus());
            reviewRecordMapper.insert(record);

            return result;
        } catch (Exception e) {
            log.error("Content moderation failed for {} {}: {}", contentType, contentId, e.getMessage(), e);
            // On moderation failure, reject safely rather than silently passing
            ContentReviewResult fallback = new ContentReviewResult(
                    0, "PENDING_REVIEW", "PENDING", List.of());
            ContentReviewRecord record = new ContentReviewRecord();
            record.setContentType(contentType);
            record.setContentId(contentId);
            record.setUserId(userId);
            record.setRiskLevel(0);
            record.setMatchedWords(null);
            record.setReviewStatus("PENDING");
            reviewRecordMapper.insert(record);
            return fallback;
        }
    }

    /**
     * Updates the review record to APPROVED status.
     */
    public void approveRecord(String contentType, Long contentId, Long reviewerId, String remark) {
        ContentReviewRecord record = findLatestRecord(contentType, contentId);
        if (record != null) {
            record.setReviewStatus(ReviewStatus.APPROVED);
            record.setReviewerId(reviewerId);
            record.setReviewRemark(remark);
            record.setReviewTime(java.time.LocalDateTime.now());
            reviewRecordMapper.updateById(record);
        }
    }

    /**
     * Updates the review record to REJECTED status.
     */
    public void rejectRecord(String contentType, Long contentId, Long reviewerId, String remark) {
        ContentReviewRecord record = findLatestRecord(contentType, contentId);
        if (record != null) {
            record.setReviewStatus(ReviewStatus.REJECTED);
            record.setReviewerId(reviewerId);
            record.setReviewRemark(remark);
            record.setReviewTime(java.time.LocalDateTime.now());
            reviewRecordMapper.updateById(record);
        }
    }

    /**
     * Loads all active, non-deleted sensitive words from the database.
     * Cached because the word list is near-static and read on every community write.
     * Cache is evicted by {@link #evictSensitiveWordCache()} on any admin mutation.
     */
    @Cacheable(value = "sensitiveWords")
    public List<SensitiveWord> loadActiveSensitiveWords() {
        return sensitiveWordMapper.selectList(
                new LambdaQueryWrapper<SensitiveWord>()
                        .eq(SensitiveWord::getStatus, "ACTIVE")
                        .eq(SensitiveWord::getDeleted, 0)
        );
    }

    /**
     * Evicts the sensitive-word cache. Called by admin mutation endpoints
     * (create / update / disable) so moderation picks up changes promptly.
     */
    @CacheEvict(value = "sensitiveWords", allEntries = true)
    public void evictSensitiveWordCache() {
        // no-op body: annotation-driven eviction
    }

    private ContentReviewRecord findLatestRecord(String contentType, Long contentId) {
        return reviewRecordMapper.selectOne(
                new LambdaQueryWrapper<ContentReviewRecord>()
                        .eq(ContentReviewRecord::getContentType, contentType)
                        .eq(ContentReviewRecord::getContentId, contentId)
                        .orderByDesc(ContentReviewRecord::getCreateTime)
                        .last("LIMIT 1")
        );
    }
}
