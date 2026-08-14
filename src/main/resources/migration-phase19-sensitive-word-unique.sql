-- CI-DB-021 | migration-phase19-sensitive-word-unique.sql
-- B4 安全修复：sensitive_word.word 加唯一索引，兜底"先查后插"在并发下产生重复 ACTIVE 词的竞态。
-- 前置说明：全仓无敏感词删除入口（deleted 恒 0），逻辑删除不构成唯一索引阻碍。
-- 若存量数据已有重复 word，本迁移会失败——先按保留最小 id 的方式去重再执行。

-- 1. 存量去重（保留每组 word 中 id 最小的一行，其余逻辑删除）
UPDATE sensitive_word sw
JOIN (
    SELECT word, MIN(id) AS keep_id
    FROM sensitive_word
    WHERE deleted = 0
    GROUP BY word
    HAVING COUNT(*) > 1
) dup ON sw.word = dup.word AND sw.id <> dup.keep_id
SET sw.deleted = 1;

-- 2. 加唯一索引
ALTER TABLE sensitive_word
    ADD UNIQUE KEY `uk_word` (`word`);
