-- ============================================================================
-- phase19：商品/服务项删除权限（2026-08-15）
-- 背景：管理端新增"先下架/停用 → 后删除"功能，新增两个权限码。
-- 关联：AdminManagementController DELETE /api/v1/admin/products/{id}、
--       /api/v1/admin/service-items/{id}（服务端强制 OFF_SALE 才能删）
-- 说明：initdb 不会自动执行本文件——已初始化的库需手动执行：
--   docker exec -i petcare-mysql mysql -uroot -p"$MYSQL_ROOT_PASSWORD" petcare_o2o \
--     < migration-phase19-product-service-delete.sql
-- 幂等性：权限码有唯一约束，重复执行会报 Duplicate entry，可忽略。
-- ============================================================================

INSERT INTO `admin_permission` (`id`, `permission_code`, `permission_name`, `module`, `status`)
VALUES
  (7062, 'product:item:delete',  '商品删除',   'product', 'ACTIVE'),
  (7063, 'service:item:delete',  '服务项删除', 'service', 'ACTIVE');

-- SUPER_ADMIN(role_id=1) / ADMIN(role_id=2)：沿用 data-dev.sql 的 80000/81000 偏移段
INSERT INTO `admin_role_permission` (`id`, `role_id`, `permission_id`)
VALUES
  (87062, 1, 7062), (87063, 1, 7063),
  (88062, 2, 7062), (88063, 2, 7063);
