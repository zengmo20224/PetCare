-- ============================================================================
-- PetCare O2O 开发演示种子数据
-- 用法：导入 schema.sql 后执行本文件
-- 安全：不含任何生产秘密；管理员密码为 BCrypt 哈希
-- 重复执行前请先 TRUNCATE 或重建数据库
-- ============================================================================

USE petcare_o2o;

-- ============================================================================
-- 1. 门店与配置
-- ============================================================================
INSERT INTO `store` (`id`, `store_name`, `phone`, `address`, `longitude`, `latitude`, `business_hours`, `status`, `description`)
VALUES (1001, '萌宠家园(上海徐汇店)', '021-64880001', '上海市徐汇区漕溪北路100号', 121.436780, 31.195000, '09:00-21:00', 'OPEN', '专业宠物洗护美容与上门照护服务');

INSERT INTO `store_config` (`id`, `store_id`, `home_service_radius_km`, `booking_advance_days`, `booking_cancel_hours`, `time_slot_minutes`, `auto_confirm_booking`, `content_auto_publish`)
VALUES (1001, 1001, 5.00, 14, 4, 30, 0, 1);

-- ============================================================================
-- 2. 服务分类与服务项目
-- ============================================================================
INSERT INTO `service_category` (`id`, `name`, `sort`, `status`) VALUES
(2001, '洗护', 1, 'ACTIVE'),
(2002, '美容', 2, 'ACTIVE'),
(2003, '上门照护', 3, 'ACTIVE'),
(2004, '寄养', 4, 'ACTIVE');

INSERT INTO `service_item` (`id`, `category_id`, `name`, `service_mode`, `price`, `duration_minutes`, `pet_type`, `pet_size`, `need_address`, `need_pet`, `description`, `status`, `sort`) VALUES
(3001, 2001, '小型犬基础洗护', 'STORE', 68.00, 60, 'DOG', 'SMALL', 0, 1, '包含洗澡、吹干、基础梳理', 'ON_SALE', 1),
(3002, 2001, '中型犬深层洗护', 'STORE', 98.00, 75, 'DOG', 'MEDIUM', 0, 1, '深层清洁、吹干、毛发梳理', 'ON_SALE', 2),
(3003, 2001, '猫咪洗护套餐', 'STORE', 88.00, 60, 'CAT', 'ALL', 0, 1, '猫专用低敏洗浴，含吹干', 'ON_SALE', 3),
(3004, 2002, '萌宠美容造型', 'STORE', 168.00, 90, 'DOG', 'ALL', 0, 1, '专业美容师造型修剪', 'ON_SALE', 1),
(3005, 2002, '猫咪造型美容', 'STORE', 138.00, 75, 'CAT', 'ALL', 0, 1, '猫咪专属美容服务', 'ON_SALE', 2),
(3006, 2003, '上门遛狗(30分钟)', 'HOME', 50.00, 30, 'DOG', 'ALL', 1, 0, '专业遛狗服务，限门店5公里范围', 'ON_SALE', 1),
(3007, 2003, '上门喂猫', 'HOME', 60.00, 45, 'CAT', 'ALL', 1, 0, '上门喂食、换水、铲屎', 'ON_SALE', 2),
(3008, 2003, '上门综合照护', 'BOTH', 120.00, 90, 'ALL', 'ALL', 1, 1, '上门喂养+陪伴+基础清洁', 'ON_SALE', 3),
(3009, 2004, '日间寄养', 'STORE', 80.00, 480, 'ALL', 'ALL', 0, 1, '白天寄养，含两餐', 'ON_SALE', 1),
(3010, 2001, '大型犬洗护', 'STORE', 128.00, 90, 'DOG', 'LARGE', 0, 1, '大型犬专属深层洗护', 'ON_SALE', 4);

INSERT INTO `service_item_image` (`id`, `service_item_id`, `image_url`, `sort`) VALUES
(31001, 3001, '/static/logo.png', 1),
(31002, 3001, '/static/logo.png', 2),
(31003, 3004, '/static/logo.png', 1),
(31004, 3006, '/static/logo.png', 1);

-- ============================================================================
-- 3. 商品分类与商品
-- ============================================================================
INSERT INTO `product_category` (`id`, `name`, `sort`, `status`) VALUES
(4001, '主粮', 1, 'ACTIVE'),
(4002, '零食', 2, 'ACTIVE'),
(4003, '日用品', 3, 'ACTIVE');

INSERT INTO `product` (`id`, `category_id`, `name`, `price`, `stock`, `sales_count`, `description`, `pickup_only`, `status`, `sort`) VALUES
(5001, 4001, '皇家中型犬成犬粮 2kg',   128.00, 50, 120, '法国皇家，中型犬专用成犬粮', 1, 'ON_SALE', 1),
(5002, 4001, '渴望无谷鸡肉猫粮 1.8kg', 168.00, 30, 85,  '高蛋白无谷物配方',          1, 'ON_SALE', 2),
(5003, 4001, '爱肯拿农场盛宴犬粮 2kg', 158.00, 25, 60,  '多种肉类来源，营养均衡',    1, 'ON_SALE', 3),
(5004, 4002, '冻干鸡胸肉训练零食 100g', 35.00, 100, 200, '纯鸡胸肉冻干，训练奖励',   1, 'ON_SALE', 1),
(5005, 4002, '猫条零食组合装 20支',     28.00, 80, 150,  '多种口味猫条组合',          1, 'ON_SALE', 2),
(5006, 4002, '鸭肉磨牙棒 5根装',        45.00, 40, 70,   '天然鸭肉，洁齿磨牙',        1, 'ON_SALE', 3),
(5007, 4003, '宠物不锈钢双碗',          38.00, 60, 90,   '304不锈钢，防滑底座',       1, 'ON_SALE', 1),
(5008, 4003, '猫砂盆半封闭大号',        89.00, 20, 45,   '半封闭防臭，大号适用',      1, 'ON_SALE', 2),
(5009, 4003, '宠物指甲钳(静音款)',      25.00, 70, 110,  '静音设计，不惊吓宠物',      1, 'ON_SALE', 3),
(5010, 4003, '狗狗牵引绳(可调节)',      48.00, 50, 65,   '尼龙材质，1.2米可调节',     1, 'ON_SALE', 4);

INSERT INTO `product_image` (`id`, `product_id`, `image_url`, `sort`) VALUES
(51001, 5001, '/static/logo.png', 1),
(51002, 5001, '/static/logo.png', 2),
(51003, 5004, '/static/logo.png', 1),
(51004, 5007, '/static/logo.png', 1);

INSERT INTO `product_detail_image` (`id`, `product_id`, `image_url`, `sort`) VALUES
(51501, 5001, '/static/logo.png', 1),
(51502, 5001, '/static/logo.png', 2),
(51503, 5004, '/static/logo.png', 1),
(51504, 5007, '/static/logo.png', 1);

INSERT INTO `product_carousel_image` (`id`, `title`, `image_url`, `link_type`, `link_target_id`, `status`, `sort`) VALUES
(52001, '门店精选主粮', '/static/logo.png', 'PRODUCT', 5001, 'ACTIVE', 1),
(52002, '训练零食上新', '/static/logo.png', 'PRODUCT', 5004, 'ACTIVE', 2),
(52003, '实用日用品', '/static/logo.png', 'PRODUCT', 5007, 'ACTIVE', 3);

-- ============================================================================
-- 4. 社区话题
-- ============================================================================
INSERT INTO `topic` (`id`, `name`, `description`, `sort`, `status`) VALUES
(6001, '养宠日常', '分享你和毛孩子的每一天', 1, 'ACTIVE'),
(6002, '洗护心得', '洗护美容经验交流', 2, 'ACTIVE'),
(6003, '新手养宠', '新手必看指南和问答', 3, 'ACTIVE');

-- ============================================================================
-- 5. 社区帖子（已发布，无风险）
-- ============================================================================
INSERT INTO `post` (`id`, `user_id`, `pet_id`, `topic_id`, `title`, `content`, `status`, `risk_level`, `view_count`, `like_count`, `comment_count`, `favorite_count`, `publish_time`)
VALUES
(7001, 10001, 11001, 6001, '我家金毛第一次洗澡', '今天带我家大金毛来店里洗澡，小哥哥特别耐心，洗完毛发蓬松又顺滑，超级满意！', 'PUBLISHED', 0, 156, 32, 5, 8, NOW() - INTERVAL 2 DAY),
(7002, 10002, 11002, 6002, '猫咪美容心得分享', '以前总觉得猫咪不需要美容，试了一次之后发现确实不一样，毛发打理后家里掉毛少了很多。', 'PUBLISHED', 0, 89, 15, 3, 4, NOW() - INTERVAL 1 DAY),
(7003, 10001, NULL,  6003, '新手养猫必看：驱虫时间表', '分享一份兽医推荐的猫咪驱虫时间安排，希望对新手朋友有帮助。', 'PUBLISHED', 0, 234, 56, 12, 22, NOW() - INTERVAL 3 DAY);

-- ============================================================================
-- 6. 用户（手机号 + 密码登录）
-- 密码: user123456 (BCrypt strength=12)
-- 安全问题答案: 豆豆
-- ============================================================================
INSERT INTO `user` (`id`, `openid`, `nickname`, `avatar_url`, `phone`, `password_hash`, `gender`, `status`)
VALUES
(10001, NULL, '爱狗的小李', NULL, '13800138001', '$2a$12$jxklq2BhbNK80U8pJpH.fegUD0RdFXA.4yzAAvt.0wH.2B0bI379G', 1, 'ACTIVE'),
(10002, NULL, '猫奴小王',   NULL, '13800138002', '$2a$12$jxklq2BhbNK80U8pJpH.fegUD0RdFXA.4yzAAvt.0wH.2B0bI379G', 2, 'ACTIVE');

-- 安全问题（找回密码用，答案均为"豆豆"）
INSERT INTO `user_security_question` (`id`, `user_id`, `question`, `answer_hash`, `sort`) VALUES
(13001, 10001, '你的宠物叫什么名字？', '$2a$12$6Jk6nGh5lP7ZxzkDEvcABOFJ9Fxot8DycuNYdPEW7nxpAXiKguvKO', 0),
(13002, 10001, '你最喜欢的食物？', '$2a$12$6Jk6nGh5lP7ZxzkDEvcABOFJ9Fxot8DycuNYdPEW7nxpAXiKguvKO', 1),
(13003, 10002, '你的宠物叫什么名字？', '$2a$12$6Jk6nGh5lP7ZxzkDEvcABOFJ9Fxot8DycuNYdPEW7nxpAXiKguvKO', 0);

-- ============================================================================
-- 7. 宠物档案
-- ============================================================================
INSERT INTO `pet` (`id`, `user_id`, `name`, `type`, `breed`, `gender`, `age`, `weight`, `size`, `sterilized`, `remark`)
VALUES
(11001, 10001, '豆豆', 'DOG', '金毛寻回犬', 1, 3.0, 28.50, 'LARGE', 0, '活泼好动，喜欢水'),
(11002, 10002, '咪咪', 'CAT', '英国短毛猫', 2, 2.5, 4.20, 'SMALL', 1, '性格温顺，怕生');

-- ============================================================================
-- 8. 用户地址（经纬度在门店 5km 范围内）
-- ============================================================================
INSERT INTO `user_address` (`id`, `user_id`, `contact_name`, `contact_phone`, `province`, `city`, `district`, `detail_address`, `longitude`, `latitude`, `is_default`)
VALUES
(12001, 10001, '李明', '13800138001', '上海市', '上海市', '徐汇区', '漕溪北路88号', 121.437000, 31.194500, 1),
(12002, 10002, '王芳', '13800138002', '上海市', '上海市', '徐汇区', '天钥桥路200号', 121.438000, 31.196000, 1);

-- ============================================================================
-- 9. 员工与技能
-- ============================================================================
INSERT INTO `staff` (`id`, `store_id`, `name`, `phone`, `role`, `status`, `description`)
VALUES
(8001, 1001, '张师傅', '13900001001', 'GROOMER', 'ACTIVE', '10年宠物美容经验，擅长大型犬造型'),
(8002, 1001, '刘老师', '13900001002', 'WALKER',  'ACTIVE', '专业遛狗师，擅长中大型犬运动管理'),
(8003, 1001, '陈姐',   '13900001003', 'FEEDER',  'ACTIVE', '资深猫咪护理师，上门照护专家');

INSERT INTO `staff_skill` (`id`, `staff_id`, `service_category_id`)
VALUES
(9001, 8001, 2001),  -- 张师傅：洗护
(9002, 8001, 2002),  -- 张师傅：美容
(9003, 8002, 2003),  -- 刘老师：上门照护
(9004, 8003, 2003),  -- 陈姐：上门照护
(9005, 8003, 2004);  -- 陈姐：寄养

-- ============================================================================
-- 10. 员工排班（当天 + 明天，保证可预约时间计算有数据）
-- ============================================================================
INSERT INTO `staff_schedule` (`id`, `staff_id`, `store_id`, `work_date`, `start_time`, `end_time`, `status`)
VALUES
(10001, 8001, 1001, CURDATE(),     '09:00', '18:00', 'AVAILABLE'),
(10002, 8002, 1001, CURDATE(),     '10:00', '19:00', 'AVAILABLE'),
(10003, 8003, 1001, CURDATE(),     '09:00', '17:00', 'AVAILABLE'),
(10004, 8001, 1001, CURDATE() + 1, '09:00', '18:00', 'AVAILABLE'),
(10005, 8002, 1001, CURDATE() + 1, '10:00', '19:00', 'AVAILABLE'),
(10006, 8003, 1001, CURDATE() + 1, '09:00', '17:00', 'AVAILABLE');

-- ============================================================================
-- 11. 员工不可用时间（午休）
-- ============================================================================
INSERT INTO `staff_unavailable_time` (`id`, `staff_id`, `unavailable_date`, `start_time`, `end_time`, `reason_type`, `reason`)
VALUES
(11001, 8001, CURDATE(),     '12:00', '13:00', 'LUNCH', '午休'),
(11002, 8002, CURDATE(),     '12:00', '13:00', 'LUNCH', '午休'),
(11003, 8003, CURDATE(),     '12:00', '13:00', 'LUNCH', '午休');

-- ============================================================================
-- 12. 管理员账号
-- 密码: admin123456
-- 哈希算法: BCrypt (strength=12)
-- 注意: 此哈希仅用于开发环境，生产环境必须单独创建
-- ============================================================================
INSERT INTO `admin_user` (`id`, `username`, `password`, `nickname`, `phone`, `role`, `status`)
VALUES (9001, 'admin', '$2b$12$EyESqRL.42dvxGBLT/SRDODpS.ysy7zK86IbphsryXjW9gIysJVIa', '超级管理员', '13900009001', 'SUPER_ADMIN', 'ACTIVE');

-- ============================================================================
-- 13. 敏感词（基础示例）
-- ============================================================================
INSERT INTO `sensitive_word` (`id`, `word`, `category`, `level`, `status`)
VALUES
(13001, '广告推销', 'SPAM',     1, 'ACTIVE'),
(13002, '违禁药品', 'ILLEGAL',  3, 'ACTIVE'),
(13003, '辱骂词汇', 'ABUSE',    2, 'ACTIVE');

-- ============================================================================
-- 14. 管理员 RBAC（角色 / 权限 / 角色-权限映射）
--     未写入这些数据会导致默认 admin 账号登录后零权限，所有 @PreAuthorize 接口 403。
--     完整版见 migration-phase7-admin-rbac.sql
-- ============================================================================
INSERT INTO `admin_role` (`id`, `role_code`, `role_name`, `description`, `status`) VALUES
  (1, 'SUPER_ADMIN', '超级管理员', '全部权限', 'ACTIVE'),
  (2, 'ADMIN',       '管理员',     '管理权限', 'ACTIVE'),
  (3, 'STAFF',       '员工',       '只读权限', 'ACTIVE'),
  (4, 'MODERATOR',   '内容审核员', '社区内容审核建议查看权限（V2 AI Agent，D-013）', 'ACTIVE');

INSERT INTO `admin_permission` (`id`, `permission_code`, `permission_name`, `module`, `status`) VALUES
  (7001, 'store:info:read',     '门店信息查看', 'store', 'ACTIVE'),
  (7002, 'store:info:update',   '门店信息编辑', 'store', 'ACTIVE'),
  (7003, 'store:config:read',   '门店配置查看', 'store', 'ACTIVE'),
  (7004, 'store:config:update', '门店配置编辑', 'store', 'ACTIVE'),
  (7005, 'service:item:read',    '服务项查看', 'service', 'ACTIVE'),
  (7006, 'service:item:create',  '服务项创建', 'service', 'ACTIVE'),
  (7007, 'service:item:update',  '服务项编辑', 'service', 'ACTIVE'),
  (7008, 'service:item:disable', '服务项停用', 'service', 'ACTIVE'),
  (7009, 'staff:profile:read',     '员工查看',   'staff', 'ACTIVE'),
  (7010, 'staff:profile:create',   '员工创建',   'staff', 'ACTIVE'),
  (7011, 'staff:profile:update',   '员工编辑',   'staff', 'ACTIVE'),
  (7012, 'staff:profile:disable',  '员工停用',   'staff', 'ACTIVE'),
  (7013, 'staff:skill:manage',     '员工技能管理', 'staff', 'ACTIVE'),
  (7014, 'staff:schedule:read',    '排班查看',   'staff', 'ACTIVE'),
  (7015, 'staff:schedule:manage',  '排班管理',   'staff', 'ACTIVE'),
  (7016, 'product:item:read',      '商品查看', 'product', 'ACTIVE'),
  (7017, 'product:item:create',    '商品创建', 'product', 'ACTIVE'),
  (7018, 'product:item:update',    '商品编辑', 'product', 'ACTIVE'),
  (7019, 'product:item:disable',   '商品下架', 'product', 'ACTIVE'),
  (7020, 'product:stock:update',   '商品库存调整', 'product', 'ACTIVE'),
  (7021, 'product:order:read',           '商品订单查看',   'product', 'ACTIVE'),
  (7022, 'product:order:confirm',        '订单确认',       'product', 'ACTIVE'),
  (7023, 'product:order:confirm-payment', '订单确认收款',  'product', 'ACTIVE'),
  (7024, 'product:order:ready',          '订单备货完成',   'product', 'ACTIVE'),
  (7025, 'product:order:complete',       '订单完成',       'product', 'ACTIVE'),
  (7026, 'product:order:cancel',         '订单取消',       'product', 'ACTIVE'),
  (7027, 'booking:booking:read',    '预约查看',   'booking', 'ACTIVE'),
  (7028, 'booking:booking:confirm', '预约确认',   'booking', 'ACTIVE'),
  (7029, 'booking:booking:reject',  '预约拒绝',   'booking', 'ACTIVE'),
  (7030, 'booking:booking:start',   '预约开始服务', 'booking', 'ACTIVE'),
  (7031, 'booking:booking:complete','预约完成',   'booking', 'ACTIVE'),
  (7032, 'booking:booking:cancel',  '预约取消',   'booking', 'ACTIVE'),
  (7033, 'community:post:read',           '帖子查看',   'community', 'ACTIVE'),
  (7034, 'community:post:approve',        '帖子审核通过', 'community', 'ACTIVE'),
  (7035, 'community:post:reject',         '帖子审核拒绝', 'community', 'ACTIVE'),
  (7036, 'community:post:hide',           '帖子隐藏',   'community', 'ACTIVE'),
  (7037, 'community:post:delete',         '帖子删除',   'community', 'ACTIVE'),
  (7038, 'community:report:handle',       '举报处理',   'community', 'ACTIVE'),
  (7039, 'community:sensitive-word:manage','敏感词管理', 'community', 'ACTIVE'),
  (7040, 'system:config',                 '系统配置/公告管理', 'system', 'ACTIVE'),
  (7041, 'admin:operation-log:read',      '操作日志查看', 'admin', 'ACTIVE'),
  (7042, 'analytics:dashboard:read',      '仪表盘统计', 'analytics', 'ACTIVE'),
  (7043, 'user:profile:read',             '用户查看',     'user', 'ACTIVE'),
  (7044, 'user:profile:ban',              '用户封禁/解封', 'user', 'ACTIVE'),
  (7045, 'marketing:activity:read',       '营销活动查看', 'marketing', 'ACTIVE'),
  (7046, 'marketing:activity:manage',     '营销活动管理', 'marketing', 'ACTIVE'),
  (7047, 'staff:profile:enable',          '员工启用',     'staff', 'ACTIVE'),
  -- ai (7048-7049) — D-004 修订（2026-07-21）：用户端客服 + 管理端分析报告已激活
  (7048, 'ai:analysis:generate',          '生成AI分析报告', 'ai', 'ACTIVE'),
  (7049, 'ai:usage:read',                 'AI用量查看',     'ai', 'ACTIVE'),
  -- ai V2 Agent (7055-7056) — D-013（2026-08-01）：审核建议 + 知识库重建
  -- 注：ai:customer-service:chat / ai:post-assistant:use 是用户端权限（登录即可），不进管理端 RBAC
  (7055, 'ai:moderation:review',          '查看AI审核建议', 'ai', 'ACTIVE'),
  (7056, 'ai:knowledge:rebuild',          '重建AI知识库',   'ai', 'ACTIVE');

-- SUPER_ADMIN 和 ADMIN：全部权限（开发环境，含 V2 ai 段）
INSERT INTO `admin_role_permission` (`id`, `role_id`, `permission_id`)
SELECT 80000 + p.id, 1, p.id FROM `admin_permission` p WHERE p.id BETWEEN 7001 AND 7056;
INSERT INTO `admin_role_permission` (`id`, `role_id`, `permission_id`)
SELECT 81000 + p.id, 2, p.id FROM `admin_permission` p WHERE p.id BETWEEN 7001 AND 7056;

-- MODERATOR：只获得 ai:moderation:review（7055），不能重建知识库
INSERT INTO `admin_role_permission` (`id`, `role_id`, `permission_id`) VALUES
  (80055, 4, 7055);

-- STAFF：只读权限
INSERT INTO `admin_role_permission` (`id`, `role_id`, `permission_id`) VALUES
  (82001, 3, 7001), (82002, 3, 7003), (82003, 3, 7005), (82004, 3, 7009),
  (82005, 3, 7014), (82006, 3, 7016), (82007, 3, 7021), (82008, 3, 7027),
  (82009, 3, 7033), (82010, 3, 7041), (82011, 3, 7042), (82012, 3, 7045);

-- ============================================================================
-- 14. FAQ 知识库（AI 客服上下文，当前 AI 禁用但不影响数据存在）
-- ============================================================================
INSERT INTO `faq_knowledge` (`id`, `question`, `answer`, `category`, `status`)
VALUES
(14001, '营业时间是什么时候？', '我们的营业时间是每天 9:00 到 21:00。', 'STORE', 'ACTIVE'),
(14002, '上门服务的范围是多少？', '上门服务范围是门店周围 5 公里内。', 'BOOKING', 'ACTIVE'),
(14003, '如何取消预约？', '请在服务开始前至少 4 小时在订单中取消。', 'BOOKING', 'ACTIVE');

-- ============================================================================
-- 15. 营销活动（ACTIVE 状态可在 H5 公开浏览）
-- ============================================================================
INSERT INTO `marketing_activity` (`id`, `title`, `activity_type`, `description`, `cover_url`, `start_time`, `end_time`, `status`)
VALUES
(15001, '夏季洗护特惠', 'SERVICE', '夏季来临，全部洗护服务享特价体验，名额有限！', '/uploads/images/activity-service.jpg', NOW() - INTERVAL 10 DAY, NOW() + INTERVAL 20 DAY, 'ACTIVE'),
(15002, '宠物食品节', 'PRODUCT', '精选主粮零食满减优惠，到店自提更方便。', '/uploads/images/activity-product.jpg', NOW() - INTERVAL 5 DAY, NOW() + INTERVAL 15 DAY, 'ACTIVE');

INSERT INTO `activity_service` (`id`, `activity_id`, `service_item_id`)
VALUES
(16001, 15001, 3001),
(16002, 15001, 3002),
(16003, 15001, 3003);

INSERT INTO `activity_product` (`id`, `activity_id`, `product_id`)
VALUES
(17001, 15002, 5001),
(17002, 15002, 5002),
(17003, 15002, 5004),
(17004, 15002, 5005);

-- ============================================================================
-- 16. 手机号黑名单表（封禁用户的手机号，禁止重新注册）
-- ============================================================================
CREATE TABLE IF NOT EXISTS `phone_blacklist` (
  `id`           BIGINT       NOT NULL,
  `phone`        VARCHAR(20)  NOT NULL,
  `user_id`      BIGINT       DEFAULT NULL,
  `reason`       VARCHAR(255) DEFAULT NULL,
  `operator_id`  BIGINT       DEFAULT NULL,
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
  `ban_level`    INT          NOT NULL DEFAULT 1,
  `ban_days`     INT          DEFAULT NULL,
  `ban_until`    TIMESTAMP    DEFAULT NULL,
  `unban_time`   TIMESTAMP    DEFAULT NULL,
  `create_time`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);
