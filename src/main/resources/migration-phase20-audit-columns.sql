-- ============================================================================
-- phase20：核心业务表补操作人审计列（2026-08-29）
-- 背景：对标审计四字段模式，service_booking / product_order 增加操作人列，
--       由 MyBatisMetaObjectHandler 自动填充（admin:{id} / user:{id}）；
--       定时任务等系统写入保持 NULL，语义由 cancel_reason / merchant_remark 承载。
-- 说明：initdb 不会自动执行本文件——已初始化的库需手动执行：
--   docker exec -i petcare-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" petcare_o2o \
--     < migration-phase20-audit-columns.sql
-- 幂等性：列已存在时重复执行会报 Duplicate column name，可忽略。
-- ============================================================================

ALTER TABLE `service_booking`
    ADD COLUMN `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人（admin:{id}/user:{id}，系统写为 NULL）' AFTER `cancel_reason`,
    ADD COLUMN `update_by` VARCHAR(64) DEFAULT NULL COMMENT '最后更新人' AFTER `create_by`;

ALTER TABLE `product_order`
    ADD COLUMN `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人（admin:{id}/user:{id}，系统写为 NULL）' AFTER `idempotency_key`,
    ADD COLUMN `update_by` VARCHAR(64) DEFAULT NULL COMMENT '最后更新人' AFTER `create_by`;
