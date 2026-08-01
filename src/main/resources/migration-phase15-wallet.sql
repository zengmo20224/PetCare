-- ============================================================
-- migration-phase15-wallet.sql
-- CI-DB-016：钱包余额 schema 增量迁移
-- 关联：CR-20260718-003、docs/08-pending-decisions.md D-010/D-012
-- ============================================================
-- 范围：
--   1. 新增 user_wallet 表（账户，含乐观锁 version）
--   2. 新增 wallet_transaction 表（流水，只追加）
--   3. 扩展 product_order / service_booking 的 payment_method / payment_status 注释（加入 WALLET / WALLET_PAID）
--   4. 新增 wallet 模块 RBAC 权限码并绑定到 SUPER_ADMIN 角色
--
-- 设计依据：
--   - 余额变更必须先 SELECT ... FOR UPDATE 行锁，再条件 UPDATE（balance >= amount）
--   - 全局锁顺序：wallet → product(asc) → booking，防死锁
--   - 流水表只追加不修改，所有变更必须有 before/after 余额快照
--   - 幂等：(user_id, idempotency_key) 唯一约束
--
-- 金额精度：DECIMAL(10,2)，应用层使用 BigDecimal + RoundingMode.HALF_UP
-- ============================================================

-- 1. 用户钱包账户表
CREATE TABLE IF NOT EXISTS `user_wallet` (
  `id`             BIGINT        NOT NULL COMMENT '主键，雪花 ID',
  `user_id`        BIGINT        NOT NULL COMMENT '所属用户 ID',
  `balance`        DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '可用余额',
  `frozen_amount`  DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '冻结金额（预留，本期不使用）',
  `version`        INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  `create_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户钱包账户表（管理端手工台账）';

-- 2. 钱包流水表（只追加）
CREATE TABLE IF NOT EXISTS `wallet_transaction` (
  `id`                 BIGINT        NOT NULL COMMENT '主键，雪花 ID',
  `user_id`            BIGINT        NOT NULL COMMENT '所属用户 ID',
  `direction`          VARCHAR(8)    NOT NULL COMMENT '方向：DEBIT-扣减 / CREDIT-增加',
  `source_type`        VARCHAR(32)   NOT NULL COMMENT '来源类型：RECHARGE / PAY / REFUND / ADMIN_ADJUST / BONUS（预留）',
  `amount`             DECIMAL(10,2) NOT NULL COMMENT '发生金额（始终为正数，方向由 direction 区分）',
  `balance_before`     DECIMAL(10,2) NOT NULL COMMENT '变更前余额',
  `balance_after`      DECIMAL(10,2) NOT NULL COMMENT '变更后余额',
  `related_order_type` VARCHAR(16)   DEFAULT NULL COMMENT '关联订单类型：PRODUCT_ORDER / SERVICE_BOOKING / NULL',
  `related_order_id`   BIGINT        DEFAULT NULL COMMENT '关联订单 ID',
  `operator_type`      VARCHAR(16)   NOT NULL COMMENT '操作方类型：USER / ADMIN / SYSTEM',
  `operator_id`        BIGINT        DEFAULT NULL COMMENT '操作方 ID',
  `idempotency_key`    VARCHAR(64)   DEFAULT NULL COMMENT '幂等键，与 user_id 共同唯一',
  `reason`             VARCHAR(500)  DEFAULT NULL COMMENT '操作理由（管理员调整必填）',
  `create_time`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_wallet_idempotency` (`user_id`, `idempotency_key`),
  KEY `idx_wallet_user_time` (`user_id`, `create_time`),
  KEY `idx_wallet_related_order` (`related_order_type`, `related_order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包流水表（只追加）';

-- 3. 扩展现有支付字段注释（MySQL 8 用 MODIFY COLUMN 重声明注释）
-- product_order
ALTER TABLE `product_order`
  MODIFY COLUMN `payment_method` VARCHAR(32) DEFAULT NULL COMMENT '付款方式：OFFLINE_STORE / ONLINE_WECHAT / WALLET / FREE',
  MODIFY COLUMN `payment_status` VARCHAR(32) NOT NULL DEFAULT 'UNPAID' COMMENT '支付状态：UNPAID / OFFLINE_PAID / WALLET_PAID / REFUNDED';

-- service_booking
ALTER TABLE `service_booking`
  MODIFY COLUMN `payment_method` VARCHAR(32) DEFAULT NULL COMMENT '付款方式：OFFLINE_STORE / OFFLINE_HOME / ONLINE_WECHAT / WALLET / FREE',
  MODIFY COLUMN `payment_status` VARCHAR(32) NOT NULL DEFAULT 'UNPAID' COMMENT '支付状态：UNPAID / OFFLINE_PAID / WALLET_PAID / REFUNDED';

-- 4. RBAC：wallet 模块权限码 + 绑定 SUPER_ADMIN
-- 权限码格式：模块:资源:动作（schema.sql 注释约定）
-- id 段沿用 phase7 的固定区间约定（phase7 用 7001-7050），wallet 用 7051-7054。
INSERT INTO `admin_permission` (`id`, `permission_code`, `permission_name`, `module`, `description`, `status`)
SELECT * FROM (
  SELECT 7051 AS id, 'wallet:account:read'      AS code, '查看用户钱包' AS name, 'wallet' AS module, '查询用户钱包余额与详情' AS description, 'ACTIVE' AS status UNION ALL
  SELECT 7052,      'wallet:account:recharge',    '钱包充值',     'wallet', '管理员给用户钱包充值（单向加余额）',         'ACTIVE' UNION ALL
  SELECT 7053,      'wallet:account:adjust',      '钱包双向调整', 'wallet', '管理员双向调整用户钱包余额（必填理由）',     'ACTIVE' UNION ALL
  SELECT 7054,      'wallet:transaction:read',    '查询钱包流水', 'wallet', '查询钱包流水记录',                         'ACTIVE'
) AS t
WHERE NOT EXISTS (SELECT 1 FROM `admin_permission` WHERE `permission_code` = t.code);

-- 绑定到 SUPER_ADMIN 角色（幂等）
-- admin_role_permission.id 无默认值，需显式赋值（沿用 phase7 的 80000+pid 约定，wallet 用 80000+pid）。
INSERT INTO `admin_role_permission` (`id`, `role_id`, `permission_id`)
SELECT 80000 + p.id, r.id, p.id
FROM `admin_role` r
JOIN `admin_permission` p ON p.`module` = 'wallet' AND p.`status` = 'ACTIVE'
WHERE r.`role_code` = 'SUPER_ADMIN' AND r.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `admin_role_permission` rp
    WHERE rp.`role_id` = r.id AND rp.`permission_id` = p.id
  )
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
