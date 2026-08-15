-- Phase 7: Admin RBAC seed data (roles, permissions, role-permission mappings)
--
-- This is the critical unblocker for the admin-web backoffice: without these rows
-- the default admin account logs in with zero permissions and every @PreAuthorize
-- endpoint returns 403.
--
-- Idempotency: uses INSERT ... ON DUPLICATE KEY UPDATE so re-running is safe.
-- Target: dev MySQL (and mirrored in data-dev.sql for fresh setups).

-- ============================================================================
-- 1. Roles
-- ============================================================================
INSERT INTO `admin_role` (`id`, `role_code`, `role_name`, `description`, `status`) VALUES
  (1, 'SUPER_ADMIN', '超级管理员', '全部权限', 'ACTIVE'),
  (2, 'ADMIN',       '管理员',     '管理权限（开发环境等同超管，生产可细化）', 'ACTIVE'),
  (3, 'STAFF',       '员工',       '只读权限', 'ACTIVE')
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`), `status` = 'ACTIVE';

-- ============================================================================
-- 2. Permissions
-- id range 7001-7050 reserved for admin permissions; 7060-7061 for product/service-item enable
-- (7051-7054 reserved by phase15 wallet, 7055+ by phase16 ai-agent)
-- ============================================================================
INSERT INTO `admin_permission` (`id`, `permission_code`, `permission_name`, `module`, `status`) VALUES
  -- store (7001-7004)
  (7001, 'store:info:read',     '门店信息查看', 'store', 'ACTIVE'),
  (7002, 'store:info:update',   '门店信息编辑', 'store', 'ACTIVE'),
  (7003, 'store:config:read',   '门店配置查看', 'store', 'ACTIVE'),
  (7004, 'store:config:update', '门店配置编辑', 'store', 'ACTIVE'),
  -- service (7005-7008)
  (7005, 'service:item:read',    '服务项查看', 'service', 'ACTIVE'),
  (7006, 'service:item:create',  '服务项创建', 'service', 'ACTIVE'),
  (7007, 'service:item:update',  '服务项编辑', 'service', 'ACTIVE'),
  (7008, 'service:item:disable', '服务项停用', 'service', 'ACTIVE'),
  -- staff (7009-7014)
  (7009, 'staff:profile:read',     '员工查看',   'staff', 'ACTIVE'),
  (7010, 'staff:profile:create',   '员工创建',   'staff', 'ACTIVE'),
  (7011, 'staff:profile:update',   '员工编辑',   'staff', 'ACTIVE'),
  (7012, 'staff:profile:disable',  '员工停用',   'staff', 'ACTIVE'),
  (7013, 'staff:skill:manage',     '员工技能管理', 'staff', 'ACTIVE'),
  (7014, 'staff:schedule:read',    '排班查看',   'staff', 'ACTIVE'),
  (7015, 'staff:schedule:manage',  '排班管理',   'staff', 'ACTIVE'),
  -- product catalog (7016-7020)
  (7016, 'product:item:read',      '商品查看', 'product', 'ACTIVE'),
  (7017, 'product:item:create',    '商品创建', 'product', 'ACTIVE'),
  (7018, 'product:item:update',    '商品编辑', 'product', 'ACTIVE'),
  (7019, 'product:item:disable',   '商品下架', 'product', 'ACTIVE'),
  (7020, 'product:stock:update',   '商品库存调整', 'product', 'ACTIVE'),
  -- product order (7021-7026)
  (7021, 'product:order:read',           '商品订单查看',   'product', 'ACTIVE'),
  (7022, 'product:order:confirm',        '订单确认',       'product', 'ACTIVE'),
  (7023, 'product:order:confirm-payment', '订单确认收款',  'product', 'ACTIVE'),
  (7024, 'product:order:ready',          '订单备货完成',   'product', 'ACTIVE'),
  (7025, 'product:order:complete',       '订单完成',       'product', 'ACTIVE'),
  (7026, 'product:order:cancel',         '订单取消',       'product', 'ACTIVE'),
  -- booking (7027-7032)
  (7027, 'booking:booking:read',    '预约查看',   'booking', 'ACTIVE'),
  (7028, 'booking:booking:confirm', '预约确认',   'booking', 'ACTIVE'),
  (7029, 'booking:booking:reject',  '预约拒绝',   'booking', 'ACTIVE'),
  (7030, 'booking:booking:start',   '预约开始服务', 'booking', 'ACTIVE'),
  (7031, 'booking:booking:complete','预约完成',   'booking', 'ACTIVE'),
  (7032, 'booking:booking:cancel',  '预约取消',   'booking', 'ACTIVE'),
  -- community (7033-7040)
  (7033, 'community:post:read',           '帖子查看',   'community', 'ACTIVE'),
  (7034, 'community:post:approve',        '帖子审核通过', 'community', 'ACTIVE'),
  (7035, 'community:post:reject',         '帖子审核拒绝', 'community', 'ACTIVE'),
  (7036, 'community:post:hide',           '帖子隐藏',   'community', 'ACTIVE'),
  (7037, 'community:post:delete',         '帖子删除',   'community', 'ACTIVE'),
  (7038, 'community:report:handle',       '举报处理',   'community', 'ACTIVE'),
  (7039, 'community:sensitive-word:manage','敏感词管理', 'community', 'ACTIVE'),
  -- moderation (comment hide/delete reuse community codes; reports reuse above)
  -- system / announcement (7040)
  (7040, 'system:config',                 '系统配置/公告管理', 'system', 'ACTIVE'),
  -- admin (7041)
  (7041, 'admin:operation-log:read',      '操作日志查看', 'admin', 'ACTIVE'),
  -- analytics (7042)
  (7042, 'analytics:dashboard:read',      '仪表盘统计', 'analytics', 'ACTIVE'),
  -- marketing (7045-7046)
  (7045, 'marketing:activity:read',       '营销活动查看', 'marketing', 'ACTIVE'),
  (7046, 'marketing:activity:manage',     '营销活动管理', 'marketing', 'ACTIVE'),
  -- staff enable (7047)
  (7047, 'staff:profile:enable',          '员工启用',     'staff', 'ACTIVE'),
  -- ai (7048-7049) — D-004 修订（2026-07-21）：用户端客服 + 管理端分析报告已激活
  (7048, 'ai:analysis:generate',          '生成AI分析报告', 'ai', 'ACTIVE'),
  (7049, 'ai:usage:read',                 'AI用量查看',     'ai', 'ACTIVE'),
  -- product/service-item enable (7060-7061) — 与 disable 对称：下架后可重新上架
  -- 注：7050 留给 phase7 后续扩展，7051-7054 已被 phase15 钱包占用，故启用新段 7060+
  (7060, 'product:item:enable',           '商品上架',       'product', 'ACTIVE'),
  (7061, 'service:item:enable',           '服务项启用',     'service', 'ACTIVE')
ON DUPLICATE KEY UPDATE `permission_name` = VALUES(`permission_name`), `status` = 'ACTIVE';

-- ============================================================================
-- 3. Role-Permission mappings
--    SUPER_ADMIN and ADMIN: all permissions (dev simplification)
--    STAFF: read-only subset
-- ============================================================================
-- SUPER_ADMIN (role_id=1): all permission ids 7001-7061 (含 7060-7061 enable)
INSERT INTO `admin_role_permission` (`id`, `role_id`, `permission_id`)
SELECT 80000 + p.id, 1, p.id
FROM `admin_permission` p
WHERE p.id BETWEEN 7001 AND 7061
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- ADMIN (role_id=2): same as SUPER_ADMIN in dev (all permissions)
INSERT INTO `admin_role_permission` (`id`, `role_id`, `permission_id`)
SELECT 81000 + p.id, 2, p.id
FROM `admin_permission` p
WHERE p.id BETWEEN 7001 AND 7061
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- STAFF (role_id=3): read-only permissions only
INSERT INTO `admin_role_permission` (`id`, `role_id`, `permission_id`) VALUES
  (82001, 3, 7001),  -- store:info:read
  (82002, 3, 7003),  -- store:config:read
  (82003, 3, 7005),  -- service:item:read
  (82004, 3, 7009),  -- staff:profile:read
  (82005, 3, 7014),  -- staff:schedule:read
  (82006, 3, 7016),  -- product:item:read
  (82007, 3, 7021),  -- product:order:read
  (82008, 3, 7027),  -- booking:booking:read
  (82009, 3, 7033),  -- community:post:read
  (82010, 3, 7041),  -- admin:operation-log:read
  (82011, 3, 7042),  -- analytics:dashboard:read
  (82012, 3, 7045)   -- marketing:activity:read
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
