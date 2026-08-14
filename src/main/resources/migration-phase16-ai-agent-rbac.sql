-- ============================================================
-- migration-phase16-ai-agent-rbac.sql
-- CI-DB-018：V2 AI Agent RBAC 权限码增量迁移
-- 关联：CR-20260801-004、docs/09-ai-agent-design.md §6.3、docs/08-pending-decisions.md D-013
-- ============================================================
-- 范围：
--   1. 新增 MODERATOR 角色（role_id=4，供 ai:moderation:review 授权，docs/09 §6.3）
--   2. 新增 V2 AI Agent 权限码（7055-7058，避开 phase7 的 7001-7049 与 phase15 钱包的 7051-7054）
--   3. 将管理端权限码绑定到 SUPER_ADMIN / ADMIN / MODERATOR
--
-- 权限码清单（docs/09 §6.3）：
--   7055 ai:moderation:review       —— 查看 AI 审核建议（管理端，MODERATOR+）
--   7056 ai:knowledge:rebuild       —— 手动重建知识库（管理端，SUPER_ADMIN）
--   注：用户端权限 ai:customer-service:chat / ai:post-assistant:use 不进 admin_permission 表
--       （运行时由 SecurityContext 校验登录态，docs/09 §6.3）
--   注：7058 复用 phase7 已有的 7048(ai:analysis:generate) / 7049(ai:usage:read)，此处不重复
--
-- 幂等：沿用 phase15 钱包范式（WHERE NOT EXISTS + ON DUPLICATE KEY UPDATE）
-- 设计依据：docs/09-ai-agent-design.md §6.3 Tool RBAC 双重校验
-- ============================================================

-- 1. 新增 MODERATOR 角色（幂等）
INSERT INTO `admin_role` (`id`, `role_code`, `role_name`, `description`, `status`)
SELECT * FROM (
  SELECT 4 AS id, 'MODERATOR' AS role_code, '内容审核员' AS role_name,
         '社区内容审核建议查看权限' AS description, 'ACTIVE' AS status
) AS t
WHERE NOT EXISTS (SELECT 1 FROM `admin_role` WHERE `role_code` = 'MODERATOR' AND `deleted` = 0)
ON DUPLICATE KEY UPDATE `role_name` = VALUES(`role_name`), `status` = 'ACTIVE';

-- 2. 新增 V2 AI Agent 权限码（幂等，避开 7051-7054 钱包段）
INSERT INTO `admin_permission` (`id`, `permission_code`, `permission_name`, `module`, `description`, `status`)
SELECT * FROM (
  SELECT 7055 AS id, 'ai:moderation:review'  AS code, '查看AI审核建议' AS name, 'ai' AS module,
         '查看社区内容 AI 审核建议（文本+图片），仅查看不直接处置' AS description, 'ACTIVE' AS status UNION ALL
  SELECT 7056,      'ai:knowledge:rebuild',   '重建AI知识库',  'ai',
         '手动重建 PgVector 知识库索引（管理员触发全量重索引）',     'ACTIVE'
) AS t
WHERE NOT EXISTS (SELECT 1 FROM `admin_permission` WHERE `permission_code` = t.code);

-- 注：ai:customer-service:chat / ai:post-assistant:use 是用户端权限（所有登录用户可用），
--     不进管理端 admin_permission 表（该表只管后台权限），运行时由 SecurityContext 校验登录态。
--     详见 docs/09 §6.3 表格注释。

-- 3. 权限绑定（幂等）
-- 3.1 SUPER_ADMIN / ADMIN：获得全部 ai 模块权限（含 7055-7056 + 已有 7048-7049）
INSERT INTO `admin_role_permission` (`id`, `role_id`, `permission_id`)
SELECT 80000 + p.id, r.id, p.id
FROM `admin_role` r
JOIN `admin_permission` p ON p.`module` = 'ai' AND p.`status` = 'ACTIVE' AND p.`id` IN (7048, 7049, 7055, 7056)
WHERE r.`role_code` IN ('SUPER_ADMIN', 'ADMIN') AND r.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `admin_role_permission` rp
    WHERE rp.`role_id` = r.id AND rp.`permission_id` = p.id
  )
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);

-- 3.2 MODERATOR：只获得 ai:moderation:review（7055），不能重建知识库（7056）
INSERT INTO `admin_role_permission` (`id`, `role_id`, `permission_id`)
SELECT 80000 + p.id, r.id, p.id
FROM `admin_role` r
JOIN `admin_permission` p ON p.`id` = 7055 AND p.`status` = 'ACTIVE'
WHERE r.`role_code` = 'MODERATOR' AND r.`deleted` = 0
  AND NOT EXISTS (
    SELECT 1 FROM `admin_role_permission` rp
    WHERE rp.`role_id` = r.id AND rp.`permission_id` = p.id
  )
ON DUPLICATE KEY UPDATE `role_id` = VALUES(`role_id`);
