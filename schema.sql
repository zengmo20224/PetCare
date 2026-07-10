-- ============================================================================
-- PetCare O2O 数据库初始化脚本
-- 数据库：petcare_o2o
-- 兼容：MySQL 8.0+
-- 字符集：utf8mb4 / utf8mb4_unicode_ci
-- 引擎：InnoDB
-- 主键策略：MyBatis-Plus 雪花 ID，不使用 AUTO_INCREMENT
-- 外键：V1 不使用物理外键，仅通过逻辑关系 + 索引维护
-- ============================================================================

CREATE DATABASE IF NOT EXISTS petcare_o2o
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE petcare_o2o;

-- ============================================================================
-- A. 用户与宠物模块
-- ============================================================================

-- 用户表（user 是 MySQL 保留字，使用反引号）
CREATE TABLE `user` (
  `id`              BIGINT          NOT NULL COMMENT '主键，雪花 ID',
  `openid`          VARCHAR(128)    DEFAULT NULL COMMENT '微信 openid',
  `unionid`         VARCHAR(128)    DEFAULT NULL COMMENT '微信 unionid',
  `nickname`        VARCHAR(64)     DEFAULT NULL COMMENT '用户昵称',
  `real_name`       VARCHAR(64)     DEFAULT NULL COMMENT '真实姓名',
  `id_card_no`      VARCHAR(32)     DEFAULT NULL COMMENT '身份证号',
  `id_card_image_url` VARCHAR(255)  DEFAULT NULL COMMENT '身份证图片地址',
  `avatar_url`      VARCHAR(255)    DEFAULT NULL COMMENT '头像地址',
  `phone`           VARCHAR(20)     DEFAULT NULL COMMENT '手机号',
  `password_hash`   VARCHAR(128)    DEFAULT NULL COMMENT 'BCrypt 密码哈希',
  `gender`          TINYINT         DEFAULT NULL COMMENT '性别：0-未知 1-男 2-女',
  `status`          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
  `last_login_time` DATETIME        DEFAULT NULL COMMENT '最后登录时间',
  `create_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_openid` (`openid`),
  UNIQUE KEY `uk_phone` (`phone`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- 用户安全问题表（找回密码用）
CREATE TABLE `user_security_question` (
  `id`          BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户 ID',
  `question`    VARCHAR(255) NOT NULL COMMENT '安全问题题目',
  `answer_hash` VARCHAR(128) NOT NULL COMMENT '答案 BCrypt 哈希',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户安全问题表';

-- 宠物档案表
CREATE TABLE `pet` (
  `id`          BIGINT          NOT NULL COMMENT '主键，雪花 ID',
  `user_id`     BIGINT          NOT NULL COMMENT '所属用户 ID',
  `name`        VARCHAR(64)     DEFAULT NULL COMMENT '宠物名称',
  `type`        VARCHAR(32)     DEFAULT NULL COMMENT '宠物类型：DOG / CAT / OTHER',
  `breed`       VARCHAR(64)     DEFAULT NULL COMMENT '品种',
  `gender`      TINYINT         DEFAULT NULL COMMENT '性别：0-未知 1-公 2-母',
  `age`         DECIMAL(4,1)    DEFAULT NULL COMMENT '年龄（岁）',
  `weight`      DECIMAL(5,2)    DEFAULT NULL COMMENT '体重（kg）',
  `size`        VARCHAR(32)     DEFAULT NULL COMMENT '体型：SMALL / MEDIUM / LARGE',
  `sterilized`  TINYINT         DEFAULT NULL COMMENT '是否绝育：0-否 1-是',
  `avatar_url`  VARCHAR(255)    DEFAULT NULL COMMENT '宠物头像地址',
  `remark`      VARCHAR(500)    DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT         NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_type` (`type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='宠物档案表';

-- 用户地址表
CREATE TABLE `user_address` (
  `id`             BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `user_id`        BIGINT       NOT NULL COMMENT '所属用户 ID',
  `contact_name`   VARCHAR(64)  DEFAULT NULL COMMENT '联系人姓名',
  `contact_phone`  VARCHAR(20)  DEFAULT NULL COMMENT '联系人手机号',
  `province`       VARCHAR(64)  DEFAULT NULL COMMENT '省份',
  `city`           VARCHAR(64)  DEFAULT NULL COMMENT '城市',
  `district`       VARCHAR(64)  DEFAULT NULL COMMENT '区县',
  `detail_address` VARCHAR(255) DEFAULT NULL COMMENT '详细地址',
  `longitude`      DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
  `latitude`       DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
  `is_default`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认地址：0-否 1-是',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户地址表';

-- ============================================================================
-- B. 门店与配置模块
-- ============================================================================

-- 门店表
CREATE TABLE `store` (
  `id`             BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `store_name`     VARCHAR(100) DEFAULT NULL COMMENT '门店名称',
  `phone`          VARCHAR(20)  DEFAULT NULL COMMENT '门店电话',
  `address`        VARCHAR(255) DEFAULT NULL COMMENT '门店地址',
  `longitude`      DECIMAL(10,6) DEFAULT NULL COMMENT '经度',
  `latitude`       DECIMAL(10,6) DEFAULT NULL COMMENT '纬度',
  `business_hours` VARCHAR(100) DEFAULT NULL COMMENT '营业时间描述',
  `status`         VARCHAR(32)  NOT NULL DEFAULT 'OPEN' COMMENT '状态：OPEN / CLOSED',
  `description`    VARCHAR(500) DEFAULT NULL COMMENT '门店描述',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店表';

-- 门店配置表
CREATE TABLE `store_config` (
  `id`                      BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `store_id`                BIGINT       NOT NULL COMMENT '门店 ID',
  `home_service_radius_km`  DECIMAL(5,2) NOT NULL DEFAULT 5.00 COMMENT '上门服务半径（公里），默认 5',
  `booking_advance_days`    INT          NOT NULL DEFAULT 14 COMMENT '可提前预约天数，默认 14',
  `booking_cancel_hours`    INT          NOT NULL DEFAULT 4 COMMENT '预约取消提前小时数，默认 4',
  `time_slot_minutes`       INT          NOT NULL DEFAULT 30 COMMENT '时间槽粒度（分钟），默认 30',
  `auto_confirm_booking`    TINYINT      NOT NULL DEFAULT 0 COMMENT '是否自动确认预约：0-否 1-是',
  `content_auto_publish`    TINYINT      NOT NULL DEFAULT 1 COMMENT '内容是否自动发布：0-否 1-是',
  `create_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`             DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_store_id` (`store_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='门店配置表';

-- ============================================================================
-- C. 服务与员工模块
-- ============================================================================

-- 服务分类表
CREATE TABLE `service_category` (
  `id`          BIGINT      NOT NULL COMMENT '主键，雪花 ID',
  `name`        VARCHAR(64) DEFAULT NULL COMMENT '分类名称',
  `icon_url`    VARCHAR(255) DEFAULT NULL COMMENT '分类图标地址',
  `sort`        INT         NOT NULL DEFAULT 0 COMMENT '排序序号',
  `status`      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务分类表';

-- 服务项目表
CREATE TABLE `service_item` (
  `id`               BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `category_id`      BIGINT       NOT NULL COMMENT '服务分类 ID',
  `name`             VARCHAR(100) DEFAULT NULL COMMENT '服务名称',
  `service_mode`     VARCHAR(32)  NOT NULL DEFAULT 'STORE' COMMENT '服务模式：STORE / HOME / BOTH',
  `price`            DECIMAL(10,2) DEFAULT NULL COMMENT '价格',
  `duration_minutes` INT          DEFAULT NULL COMMENT '服务时长（分钟）',
  `pet_type`         VARCHAR(32)  DEFAULT NULL COMMENT '适用宠物类型：DOG / CAT / ALL',
  `pet_size`         VARCHAR(32)  DEFAULT NULL COMMENT '适用体型：SMALL / MEDIUM / LARGE / ALL',
  `need_address`     TINYINT      NOT NULL DEFAULT 0 COMMENT '是否需要地址：0-否 1-是',
  `need_pet`         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否需要宠物信息：0-否 1-是',
  `description`      TEXT         DEFAULT NULL COMMENT '服务描述',
  `cover_url`        VARCHAR(255) DEFAULT NULL COMMENT '封面图地址',
  `status`           VARCHAR(32)  NOT NULL DEFAULT 'ON_SALE' COMMENT '状态：ON_SALE / OFF_SALE',
  `sort`             INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
  `create_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`          TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`),
  KEY `idx_service_mode` (`service_mode`),
  KEY `idx_pet_type` (`pet_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务项目表';

-- 服务项目图片表
CREATE TABLE `service_item_image` (
  `id`              BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `service_item_id` BIGINT       NOT NULL COMMENT '服务项目 ID',
  `image_url`       VARCHAR(255) NOT NULL COMMENT '图片地址',
  `sort`            INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_service_item_id` (`service_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务项目图片表';

-- 员工表
CREATE TABLE `staff` (
  `id`          BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `store_id`    BIGINT       NOT NULL COMMENT '所属门店 ID',
  `name`        VARCHAR(64)  DEFAULT NULL COMMENT '员工姓名',
  `phone`       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
  `avatar_url`  VARCHAR(255) DEFAULT NULL COMMENT '头像地址',
  `role`        VARCHAR(32)  NOT NULL DEFAULT 'GROOMER' COMMENT '员工角色：GROOMER / WALKER / FEEDER / MANAGER',
  `status`      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / INACTIVE',
  `description` VARCHAR(500) DEFAULT NULL COMMENT '员工描述',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_status` (`status`),
  KEY `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工表';

-- 员工技能表
CREATE TABLE `staff_skill` (
  `id`                  BIGINT   NOT NULL COMMENT '主键，雪花 ID',
  `staff_id`            BIGINT   NOT NULL COMMENT '员工 ID',
  `service_category_id` BIGINT   NOT NULL COMMENT '服务分类 ID',
  `create_time`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_category` (`staff_id`, `service_category_id`),
  KEY `idx_service_category_id` (`service_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工技能表';

-- ============================================================================
-- D. 排班与预约模块
-- ============================================================================

-- 员工排班表
CREATE TABLE `staff_schedule` (
  `id`          BIGINT      NOT NULL COMMENT '主键，雪花 ID',
  `staff_id`    BIGINT      NOT NULL COMMENT '员工 ID',
  `store_id`    BIGINT      NOT NULL COMMENT '门店 ID',
  `work_date`   DATE        NOT NULL COMMENT '工作日期',
  `start_time`  TIME        NOT NULL COMMENT '上班时间',
  `end_time`    TIME        NOT NULL COMMENT '下班时间',
  `status`      VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE' COMMENT '状态：AVAILABLE / UNAVAILABLE',
  `remark`      VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_staff_date` (`staff_id`, `work_date`),
  KEY `idx_store_date` (`store_id`, `work_date`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工排班表';

-- 员工不可用时间表
CREATE TABLE `staff_unavailable_time` (
  `id`                BIGINT      NOT NULL COMMENT '主键，雪花 ID',
  `staff_id`          BIGINT      NOT NULL COMMENT '员工 ID',
  `unavailable_date`  DATE        NOT NULL COMMENT '不可用日期',
  `start_time`        TIME        NOT NULL COMMENT '开始时间',
  `end_time`          TIME        NOT NULL COMMENT '结束时间',
  `reason_type`       VARCHAR(32) NOT NULL DEFAULT 'TEMP' COMMENT '原因类型：LUNCH / LEAVE / TEMP',
  `reason`            VARCHAR(255) DEFAULT NULL COMMENT '具体原因',
  `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_staff_date` (`staff_id`, `unavailable_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工不可用时间表';

-- 员工预约锁定点表
-- 用于预约并发防冲突方案：为同一员工同一日期提供稳定的数据库锁定点
-- 设计依据：docs/00-project-boundary.md 与 docs/01-architecture-design.md
CREATE TABLE `staff_booking_lock` (
  `id`           BIGINT   NOT NULL COMMENT '主键，雪花 ID',
  `staff_id`     BIGINT   NOT NULL COMMENT '员工 ID',
  `booking_date` DATE     NOT NULL COMMENT '预约日期',
  `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_staff_booking_date` (`staff_id`, `booking_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工预约锁定点表（并发防冲突辅助表）';

-- 服务预约表
CREATE TABLE `service_booking` (
  `id`              BIGINT        NOT NULL COMMENT '主键，雪花 ID',
  `booking_no`      VARCHAR(64)   NOT NULL COMMENT '预约编号',
  `user_id`         BIGINT        NOT NULL COMMENT '预约用户 ID',
  `pet_id`          BIGINT        DEFAULT NULL COMMENT '宠物 ID',
  `store_id`        BIGINT        NOT NULL COMMENT '门店 ID',
  `service_item_id` BIGINT        NOT NULL COMMENT '服务项目 ID',
  `staff_id`        BIGINT        DEFAULT NULL COMMENT '分配的员工 ID',
  `service_mode`    VARCHAR(32)   NOT NULL DEFAULT 'STORE' COMMENT '服务模式：STORE / HOME',
  `booking_date`    DATE          NOT NULL COMMENT '预约日期',
  `start_time`      TIME          NOT NULL COMMENT '预约开始时间',
  `end_time`        TIME          NOT NULL COMMENT '预约结束时间',
  `address_id`      BIGINT        DEFAULT NULL COMMENT '用户地址 ID（上门服务时必填）',
  `distance_km`     DECIMAL(6,2)  DEFAULT NULL COMMENT '上门距离（公里）',
  `contact_name`    VARCHAR(64)   DEFAULT NULL COMMENT '联系人姓名',
  `contact_phone`   VARCHAR(20)   DEFAULT NULL COMMENT '联系人手机号',
  `price`           DECIMAL(10,2) DEFAULT NULL COMMENT '预约价格',
  `payment_method`  VARCHAR(32)   DEFAULT NULL COMMENT '付款方式：OFFLINE_STORE / OFFLINE_HOME / ONLINE_WECHAT / FREE',
  `payment_status`  VARCHAR(32)   NOT NULL DEFAULT 'UNPAID' COMMENT '支付状态：UNPAID / OFFLINE_PAID / REFUNDED',
  `status`          VARCHAR(32)   NOT NULL DEFAULT 'CONFIRMED' COMMENT '预约状态：PENDING_CONFIRM / CONFIRMED / IN_SERVICE / COMPLETED / CANCELLED / REJECTED（创建即确认，默认 CONFIRMED）',
  `remark`          VARCHAR(500)  DEFAULT NULL COMMENT '用户备注',
  `merchant_remark` VARCHAR(500)  DEFAULT NULL COMMENT '商家备注',
  `confirm_time`    DATETIME      DEFAULT NULL COMMENT '商家确认时间',
  `complete_time`   DATETIME      DEFAULT NULL COMMENT '服务完成时间',
  `cancel_time`     DATETIME      DEFAULT NULL COMMENT '取消时间',
  `cancel_reason`   VARCHAR(255)  DEFAULT NULL COMMENT '取消原因',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_booking_no` (`booking_no`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_staff_id` (`staff_id`),
  KEY `idx_service_item_id` (`service_item_id`),
  KEY `idx_booking_date` (`booking_date`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_booking_staff_date_status_time` (`staff_id`, `booking_date`, `status`, `deleted`, `start_time`, `end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务预约表';

-- 预约状态日志表
CREATE TABLE `booking_status_log` (
  `id`            BIGINT      NOT NULL COMMENT '主键，雪花 ID',
  `booking_id`    BIGINT      NOT NULL COMMENT '预约 ID',
  `old_status`    VARCHAR(32) DEFAULT NULL COMMENT '原状态',
  `new_status`    VARCHAR(32) NOT NULL COMMENT '新状态',
  `operator_type` VARCHAR(32) NOT NULL COMMENT '操作者类型：USER / ADMIN / SYSTEM',
  `operator_id`   BIGINT      DEFAULT NULL COMMENT '操作者 ID',
  `remark`        VARCHAR(500) DEFAULT NULL COMMENT '备注',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_booking_id` (`booking_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='预约状态日志表';

-- ============================================================================
-- E. 社区与内容模块
-- ============================================================================

-- 话题表
CREATE TABLE `topic` (
  `id`          BIGINT      NOT NULL COMMENT '主键，雪花 ID',
  `name`        VARCHAR(64) DEFAULT NULL COMMENT '话题名称',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '话题描述',
  `sort`        INT         NOT NULL DEFAULT 0 COMMENT '排序序号',
  `status`      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='话题表';

-- 帖子表
CREATE TABLE `post` (
  `id`             BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `user_id`        BIGINT       NOT NULL COMMENT '发帖用户 ID',
  `pet_id`         BIGINT       DEFAULT NULL COMMENT '关联宠物 ID',
  `topic_id`       BIGINT       DEFAULT NULL COMMENT '话题 ID',
  `title`          VARCHAR(120) DEFAULT NULL COMMENT '帖子标题',
  `content`        TEXT         DEFAULT NULL COMMENT '帖子内容',
  `status`         VARCHAR(32)  NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态：PUBLISHED / PENDING_REVIEW / REJECTED / HIDDEN / DELETED',
  `risk_level`     TINYINT      NOT NULL DEFAULT 0 COMMENT '风险等级：0-无风险 1-轻度 2-中度 3-严重',
  `view_count`     INT          NOT NULL DEFAULT 0 COMMENT '浏览数',
  `like_count`     INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
  `comment_count`  INT          NOT NULL DEFAULT 0 COMMENT '评论数',
  `favorite_count` INT          NOT NULL DEFAULT 0 COMMENT '收藏数',
  `reject_reason`  VARCHAR(255) DEFAULT NULL COMMENT '审核拒绝原因',
  `publish_time`   DATETIME     DEFAULT NULL COMMENT '发布时间',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_topic_id` (`topic_id`),
  KEY `idx_status` (`status`),
  KEY `idx_risk_level` (`risk_level`),
  KEY `idx_publish_time` (`publish_time`),
  KEY `idx_create_time` (`create_time`),
  KEY `idx_feed` (`status`, `deleted`, `publish_time`),
  KEY `idx_topic_feed` (`topic_id`, `status`, `deleted`, `publish_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子表';

-- 帖子图片表
CREATE TABLE `post_image` (
  `id`          BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `post_id`     BIGINT       NOT NULL COMMENT '帖子 ID',
  `image_url`   VARCHAR(255) NOT NULL COMMENT '图片地址',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子图片表';

-- 帖子评论表
CREATE TABLE `post_comment` (
  `id`          BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `post_id`     BIGINT       NOT NULL COMMENT '帖子 ID',
  `user_id`     BIGINT       NOT NULL COMMENT '评论用户 ID',
  `parent_id`   BIGINT       DEFAULT NULL COMMENT '父评论 ID（支持楼中楼回复）',
  `content`     VARCHAR(1000) DEFAULT NULL COMMENT '评论内容',
  `status`      VARCHAR(32)  NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态：PUBLISHED / PENDING_REVIEW / HIDDEN / DELETED',
  `risk_level`  TINYINT      NOT NULL DEFAULT 0 COMMENT '风险等级：0-无风险 1-轻度 2-中度 3-严重',
  `like_count`  INT          NOT NULL DEFAULT 0 COMMENT '点赞数',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子评论表';

-- 帖子点赞表
CREATE TABLE `post_like` (
  `id`          BIGINT   NOT NULL COMMENT '主键，雪花 ID',
  `post_id`     BIGINT   NOT NULL COMMENT '帖子 ID',
  `user_id`     BIGINT   NOT NULL COMMENT '点赞用户 ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子点赞表';

-- 帖子收藏表
CREATE TABLE `post_favorite` (
  `id`          BIGINT   NOT NULL COMMENT '主键，雪花 ID',
  `post_id`     BIGINT   NOT NULL COMMENT '帖子 ID',
  `user_id`     BIGINT   NOT NULL COMMENT '收藏用户 ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_user` (`post_id`, `user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子收藏表';

-- 帖子举报表
CREATE TABLE `post_report` (
  `id`           BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `post_id`      BIGINT       NOT NULL COMMENT '被举报帖子 ID',
  `reporter_id`  BIGINT       NOT NULL COMMENT '举报人 ID',
  `reason_type`  VARCHAR(32)  NOT NULL COMMENT '举报类型：SPAM / ILLEGAL / ABUSE / OTHER',
  `reason`       VARCHAR(500) DEFAULT NULL COMMENT '举报原因描述',
  `status`       VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / PROCESSED / IGNORED',
  `handle_result` VARCHAR(500) DEFAULT NULL COMMENT '处理结果',
  `handler_id`   BIGINT       DEFAULT NULL COMMENT '处理管理员 ID',
  `handle_time`  DATETIME     DEFAULT NULL COMMENT '处理时间',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_post_id` (`post_id`),
  KEY `idx_reporter_id` (`reporter_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子举报表';

-- ============================================================================
-- F. 敏感词与审核模块
-- ============================================================================

-- 敏感词表
CREATE TABLE `sensitive_word` (
  `id`          BIGINT      NOT NULL COMMENT '主键，雪花 ID',
  `word`        VARCHAR(100) NOT NULL COMMENT '敏感词',
  `category`    VARCHAR(32) DEFAULT NULL COMMENT '分类',
  `level`       TINYINT     NOT NULL DEFAULT 1 COMMENT '风险等级：1-轻度 2-中度 3-严重',
  `status`      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_level` (`level`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='敏感词表';

-- 内容审核记录表
CREATE TABLE `content_review_record` (
  `id`            BIGINT      NOT NULL COMMENT '主键，雪花 ID',
  `content_type`  VARCHAR(32) NOT NULL COMMENT '内容类型：POST / COMMENT',
  `content_id`    BIGINT      NOT NULL COMMENT '内容 ID',
  `user_id`       BIGINT      NOT NULL COMMENT '内容发布者 ID',
  `risk_level`    TINYINT     NOT NULL DEFAULT 0 COMMENT '风险等级：0-无风险 1-轻度 2-中度 3-严重',
  `matched_words` VARCHAR(500) DEFAULT NULL COMMENT '命中的敏感词列表',
  `review_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING / APPROVED / REJECTED',
  `reviewer_id`   BIGINT      DEFAULT NULL COMMENT '审核管理员 ID',
  `review_remark` VARCHAR(500) DEFAULT NULL COMMENT '审核备注',
  `create_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `review_time`   DATETIME    DEFAULT NULL COMMENT '审核时间',
  PRIMARY KEY (`id`),
  KEY `idx_content_type_id` (`content_type`, `content_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_review_status` (`review_status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='内容审核记录表';

-- ============================================================================
-- G. 商品与自提订单模块
-- ============================================================================

-- 商品分类表
CREATE TABLE `product_category` (
  `id`          BIGINT      NOT NULL COMMENT '主键，雪花 ID',
  `name`        VARCHAR(64) DEFAULT NULL COMMENT '分类名称',
  `icon_url`    VARCHAR(255) DEFAULT NULL COMMENT '分类图标地址',
  `sort`        INT         NOT NULL DEFAULT 0 COMMENT '排序序号',
  `status`      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品分类表';

-- 商品表
CREATE TABLE `product` (
  `id`          BIGINT        NOT NULL COMMENT '主键，雪花 ID',
  `category_id` BIGINT        NOT NULL COMMENT '商品分类 ID',
  `name`        VARCHAR(100)  DEFAULT NULL COMMENT '商品名称',
  `cover_url`   VARCHAR(255)  DEFAULT NULL COMMENT '封面图地址',
  `price`       DECIMAL(10,2) DEFAULT NULL COMMENT '价格',
  `stock`       INT           NOT NULL DEFAULT 0 COMMENT '库存数量',
  `sales_count` INT           NOT NULL DEFAULT 0 COMMENT '销量',
  `description` TEXT          DEFAULT NULL COMMENT '商品描述',
  `pickup_only` TINYINT       NOT NULL DEFAULT 1 COMMENT '是否仅到店自提：0-否 1-是',
  `status`      VARCHAR(32)   NOT NULL DEFAULT 'ON_SALE' COMMENT '状态：ON_SALE / OFF_SALE',
  `sort`        INT           NOT NULL DEFAULT 0 COMMENT '排序序号',
  `create_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_category_id` (`category_id`),
  KEY `idx_status` (`status`),
  KEY `idx_sales_count` (`sales_count`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品表';

-- 商品图片表
CREATE TABLE `product_image` (
  `id`          BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `product_id`  BIGINT       NOT NULL COMMENT '商品 ID',
  `image_url`   VARCHAR(255) NOT NULL COMMENT '图片地址',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品图片表';

-- 商品介绍图片表
CREATE TABLE `product_detail_image` (
  `id`          BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `product_id`  BIGINT       NOT NULL COMMENT '商品 ID',
  `image_url`   VARCHAR(255) NOT NULL COMMENT '图片地址',
  `sort`        INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品介绍图片表';

-- 商品展示页轮播图表
CREATE TABLE `product_carousel_image` (
  `id`             BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `title`          VARCHAR(80)  DEFAULT NULL COMMENT '轮播标题',
  `image_url`      VARCHAR(255) NOT NULL COMMENT '轮播图片地址',
  `link_type`      VARCHAR(32)  DEFAULT 'NONE' COMMENT '跳转类型：NONE / PRODUCT',
  `link_target_id` BIGINT       DEFAULT NULL COMMENT '跳转目标 ID',
  `status`         VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / INACTIVE',
  `sort`           INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_status_sort` (`status`, `sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品展示页轮播图表';

-- 购物车表
CREATE TABLE `cart_item` (
  `id`          BIGINT   NOT NULL COMMENT '主键，雪花 ID',
  `user_id`     BIGINT   NOT NULL COMMENT '用户 ID',
  `product_id`  BIGINT   NOT NULL COMMENT '商品 ID',
  `quantity`    INT      NOT NULL DEFAULT 1 COMMENT '数量',
  `checked`     TINYINT  NOT NULL DEFAULT 1 COMMENT '是否选中：0-否 1-是',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_product` (`user_id`, `product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='购物车表';

-- 商品订单表
CREATE TABLE `product_order` (
  `id`              BIGINT        NOT NULL COMMENT '主键，雪花 ID',
  `order_no`        VARCHAR(64)   NOT NULL COMMENT '订单编号',
  `user_id`         BIGINT        NOT NULL COMMENT '下单用户 ID',
  `store_id`        BIGINT        NOT NULL COMMENT '门店 ID',
  `total_amount`    DECIMAL(10,2) NOT NULL COMMENT '订单总金额',
  `delivery_method` VARCHAR(16)   NOT NULL DEFAULT 'PICKUP' COMMENT '交付方式：PICKUP / DELIVERY',
  `address_id`      BIGINT        DEFAULT NULL COMMENT '配送地址 ID',
  `address_snapshot` VARCHAR(500) DEFAULT NULL COMMENT '配送地址快照',
  `payment_method`  VARCHAR(32)   DEFAULT NULL COMMENT '付款方式：OFFLINE_STORE / ONLINE_WECHAT / FREE',
  `payment_status`  VARCHAR(32)   NOT NULL DEFAULT 'UNPAID' COMMENT '支付状态：UNPAID / OFFLINE_PAID / REFUNDED',
  `pickup_status`   VARCHAR(32)   NOT NULL DEFAULT 'WAIT_PREPARE' COMMENT '自提状态：WAIT_PREPARE / READY_FOR_PICKUP / PICKED_UP',
  `status`          VARCHAR(32)   NOT NULL DEFAULT 'PENDING_CONFIRM' COMMENT '订单状态：PENDING_CONFIRM / PREPARING / READY_FOR_PICKUP / COMPLETED / CANCELLED / OUT_OF_STOCK',
  `contact_name`    VARCHAR(64)   DEFAULT NULL COMMENT '联系人姓名',
  `contact_phone`   VARCHAR(20)   DEFAULT NULL COMMENT '联系人手机号',
  `remark`          VARCHAR(500)  DEFAULT NULL COMMENT '用户备注',
  `merchant_remark` VARCHAR(500)  DEFAULT NULL COMMENT '商家备注',
  `confirm_time`    DATETIME      DEFAULT NULL COMMENT '商家确认时间',
  `complete_time`   DATETIME      DEFAULT NULL COMMENT '订单完成时间',
  `cancel_time`     DATETIME      DEFAULT NULL COMMENT '取消时间',
  `idempotency_key` VARCHAR(64)   DEFAULT NULL COMMENT '客户端幂等键，与 user_id 共同唯一；NULL 表示不启用幂等',
  `create_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_user_idempotency` (`user_id`, `idempotency_key`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_store_id` (`store_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品订单表';

-- 商品订单项表
CREATE TABLE `product_order_item` (
  `id`                BIGINT        NOT NULL COMMENT '主键，雪花 ID',
  `order_id`          BIGINT        NOT NULL COMMENT '订单 ID',
  `product_id`        BIGINT        NOT NULL COMMENT '商品 ID',
  `product_name`      VARCHAR(100)  DEFAULT NULL COMMENT '商品名称（下单时快照）',
  `product_cover_url` VARCHAR(255)  DEFAULT NULL COMMENT '商品封面图（下单时快照）',
  `price`             DECIMAL(10,2) NOT NULL COMMENT '商品单价（下单时快照）',
  `quantity`          INT           NOT NULL COMMENT '购买数量',
  `total_amount`      DECIMAL(10,2) NOT NULL COMMENT '小计金额',
  `create_time`       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_order_id` (`order_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='商品订单项表';

-- ============================================================================
-- H. 营销活动模块
-- ============================================================================

-- 营销活动表
CREATE TABLE `marketing_activity` (
  `id`           BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `title`        VARCHAR(100) DEFAULT NULL COMMENT '活动标题',
  `activity_type` VARCHAR(32) NOT NULL DEFAULT 'MIXED' COMMENT '活动类型：SERVICE / PRODUCT / COMMUNITY / MIXED',
  `description`  TEXT         DEFAULT NULL COMMENT '活动描述',
  `cover_url`    VARCHAR(255) DEFAULT NULL COMMENT '活动封面图 URL',
  `start_time`   DATETIME     DEFAULT NULL COMMENT '活动开始时间',
  `end_time`     DATETIME     DEFAULT NULL COMMENT '活动结束时间',
  `status`       VARCHAR(32)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT / ACTIVE / ENDED / CANCELLED',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_start_end_time` (`start_time`, `end_time`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='营销活动表';

-- 活动关联商品表
CREATE TABLE `activity_product` (
  `id`          BIGINT   NOT NULL COMMENT '主键，雪花 ID',
  `activity_id` BIGINT   NOT NULL COMMENT '活动 ID',
  `product_id`  BIGINT   NOT NULL COMMENT '商品 ID',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_activity_id` (`activity_id`),
  KEY `idx_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动关联商品表';

-- 活动关联服务表
CREATE TABLE `activity_service` (
  `id`              BIGINT   NOT NULL COMMENT '主键，雪花 ID',
  `activity_id`     BIGINT   NOT NULL COMMENT '活动 ID',
  `service_item_id` BIGINT   NOT NULL COMMENT '服务项目 ID',
  `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_activity_id` (`activity_id`),
  KEY `idx_service_item_id` (`service_item_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='活动关联服务表';

-- ============================================================================
-- I. AI 模块
-- ============================================================================

-- AI 会话表
CREATE TABLE `ai_conversation` (
  `id`                BIGINT      NOT NULL COMMENT '主键，雪花 ID',
  `user_id`           BIGINT      DEFAULT NULL COMMENT '用户 ID',
  `admin_id`          BIGINT      DEFAULT NULL COMMENT '管理员 ID',
  `conversation_type` VARCHAR(32) NOT NULL COMMENT '会话类型：CUSTOMER_SERVICE / PET_CHAT / ADMIN_ANALYSIS',
  `title`             VARCHAR(100) DEFAULT NULL COMMENT '会话标题',
  `create_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`           TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_conversation_type` (`conversation_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 会话表';

-- AI 消息表
CREATE TABLE `ai_message` (
  `id`              BIGINT   NOT NULL COMMENT '主键，雪花 ID',
  `conversation_id` BIGINT   NOT NULL COMMENT '会话 ID',
  `role`            VARCHAR(32) NOT NULL COMMENT '角色：system / user / assistant',
  `content`         TEXT     DEFAULT NULL COMMENT '消息内容',
  `token_count`     INT      DEFAULT NULL COMMENT 'Token 消耗数',
  `create_time`     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_conversation_id` (`conversation_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 消息表';

-- AI 用量日志表
CREATE TABLE `ai_usage_log` (
  `id`                BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `user_id`           BIGINT       DEFAULT NULL COMMENT '用户 ID',
  `admin_id`          BIGINT       DEFAULT NULL COMMENT '管理员 ID',
  `api_type`          VARCHAR(32)  NOT NULL COMMENT 'API 类型：CHAT / CUSTOMER_SERVICE / CONTENT_GENERATE / ANALYSIS',
  `model_name`        VARCHAR(100) DEFAULT NULL COMMENT '模型名称',
  `prompt_tokens`     INT          DEFAULT NULL COMMENT '输入 Token 数',
  `completion_tokens` INT          DEFAULT NULL COMMENT '输出 Token 数',
  `total_tokens`      INT          DEFAULT NULL COMMENT '总 Token 数',
  `success`           TINYINT      NOT NULL DEFAULT 1 COMMENT '是否成功：0-失败 1-成功',
  `error_message`     VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_api_type` (`api_type`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 用量日志表';

-- AI 分析报告表
CREATE TABLE `ai_analysis_report` (
  `id`             BIGINT      NOT NULL COMMENT '主键，雪花 ID',
  `report_type`    VARCHAR(32) NOT NULL COMMENT '报告类型：BUSINESS / COMMUNITY / SALES / ACTIVITY',
  `start_date`     DATE        DEFAULT NULL COMMENT '分析起始日期',
  `end_date`       DATE        DEFAULT NULL COMMENT '分析结束日期',
  `raw_data_json`  JSON        DEFAULT NULL COMMENT '后端聚合的原始数据（JSON）',
  `ai_summary`     TEXT        DEFAULT NULL COMMENT 'AI 生成的分析摘要',
  `suggestions`    TEXT        DEFAULT NULL COMMENT 'AI 生成的运营建议',
  `created_by`     BIGINT      DEFAULT NULL COMMENT '创建者（管理员 ID）',
  `create_time`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_report_type` (`report_type`),
  KEY `idx_created_by` (`created_by`),
  KEY `idx_start_date` (`start_date`, `end_date`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 分析报告表';

-- FAQ 知识库表
CREATE TABLE `faq_knowledge` (
  `id`          BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `question`    VARCHAR(255) DEFAULT NULL COMMENT '问题',
  `answer`      TEXT         DEFAULT NULL COMMENT '回答',
  `category`    VARCHAR(32)  DEFAULT NULL COMMENT '分类：STORE / BOOKING / SERVICE / PRODUCT / AFTER_SALE',
  `status`      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
  `create_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='FAQ 知识库表';

-- ============================================================================
-- J. 后台管理模块
-- ============================================================================

-- 管理员表
CREATE TABLE `admin_user` (
  `id`              BIGINT      NOT NULL COMMENT '主键，雪花 ID',
  `username`        VARCHAR(64) NOT NULL COMMENT '登录用户名',
  `password`        VARCHAR(255) NOT NULL COMMENT '密码（哈希存储）',
  `nickname`        VARCHAR(64) DEFAULT NULL COMMENT '管理员昵称',
  `phone`           VARCHAR(20) DEFAULT NULL COMMENT '手机号',
  `role`            VARCHAR(32) NOT NULL DEFAULT 'STAFF' COMMENT '基础角色：SUPER_ADMIN / MANAGER / STAFF',
  `status`          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
  `last_login_time` DATETIME    DEFAULT NULL COMMENT '最后登录时间',
  `create_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  KEY `idx_role` (`role`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员表';

-- 管理员角色定义表
-- 细粒度 RBAC：角色定义
-- 设计依据：docs/00-project-boundary.md 与 docs/01-architecture-design.md
CREATE TABLE `admin_role` (
  `id`          BIGINT      NOT NULL COMMENT '主键，雪花 ID',
  `role_code`   VARCHAR(64) NOT NULL COMMENT '角色编码',
  `role_name`   VARCHAR(64) DEFAULT NULL COMMENT '角色名称',
  `description` VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
  `status`      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
  `create_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_code` (`role_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员角色定义表';

-- 管理员权限码表
-- 细粒度 RBAC：权限码定义
-- 权限码格式：模块:资源:动作，例如 booking:booking:confirm
-- 设计依据：docs/00-project-boundary.md 与 docs/01-architecture-design.md
CREATE TABLE `admin_permission` (
  `id`              BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `permission_code` VARCHAR(128) NOT NULL COMMENT '权限码，格式：模块:资源:动作',
  `permission_name` VARCHAR(100) DEFAULT NULL COMMENT '权限名称',
  `module`          VARCHAR(64)  DEFAULT NULL COMMENT '所属模块',
  `description`     VARCHAR(255) DEFAULT NULL COMMENT '权限描述',
  `status`          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / DISABLED',
  `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_permission_code` (`permission_code`),
  KEY `idx_module` (`module`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员权限码表';

-- 管理员角色权限关联表
-- 细粒度 RBAC：角色与权限的多对多关系
-- 设计依据：docs/00-project-boundary.md 与 docs/01-architecture-design.md
CREATE TABLE `admin_role_permission` (
  `id`             BIGINT   NOT NULL COMMENT '主键，雪花 ID',
  `role_id`        BIGINT   NOT NULL COMMENT '角色 ID',
  `permission_id`  BIGINT   NOT NULL COMMENT '权限 ID',
  `create_time`    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
  KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员角色权限关联表';

-- 管理员操作日志表
CREATE TABLE `admin_operation_log` (
  `id`             BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `admin_id`       BIGINT       NOT NULL COMMENT '操作管理员 ID',
  `module`         VARCHAR(64)  DEFAULT NULL COMMENT '操作模块',
  `operation`      VARCHAR(100) DEFAULT NULL COMMENT '操作描述',
  `request_method` VARCHAR(16)  DEFAULT NULL COMMENT '请求方法：GET / POST / PUT / DELETE',
  `request_url`    VARCHAR(255) DEFAULT NULL COMMENT '请求 URL',
  `request_params` TEXT         DEFAULT NULL COMMENT '请求参数（脱敏处理）',
  `ip`             VARCHAR(64)  DEFAULT NULL COMMENT '操作 IP 地址',
  `result`         VARCHAR(32)  DEFAULT NULL COMMENT '操作结果：SUCCESS / FAIL',
  `error_message`  VARCHAR(1000) DEFAULT NULL COMMENT '错误信息',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_admin_id` (`admin_id`),
  KEY `idx_module` (`module`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员操作日志表';

-- ============================================================================
-- K. 用户风控、社区标签与通知模块
-- ============================================================================

-- 手机号黑名单表
CREATE TABLE `phone_blacklist` (
  `id`           BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `phone`        VARCHAR(20)  NOT NULL COMMENT '手机号',
  `user_id`      BIGINT       DEFAULT NULL COMMENT '关联用户 ID',
  `reason`       VARCHAR(255) DEFAULT NULL COMMENT '封禁原因',
  `operator_id`  BIGINT       DEFAULT NULL COMMENT '操作管理员 ID',
  `status`       VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE / INACTIVE',
  `ban_level`    INT          NOT NULL DEFAULT 1 COMMENT '封禁等级',
  `ban_days`     INT          DEFAULT NULL COMMENT '封禁天数',
  `ban_until`    DATETIME     DEFAULT NULL COMMENT '封禁截止时间',
  `unban_time`   DATETIME     DEFAULT NULL COMMENT '解封时间',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_phone` (`phone`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='手机号黑名单表';

-- 社区标签表
CREATE TABLE `community_tag` (
  `id`           BIGINT      NOT NULL COMMENT '主键，雪花 ID',
  `name`         VARCHAR(64) NOT NULL COMMENT '标签名称',
  `usage_count`  INT         NOT NULL DEFAULT 0 COMMENT '使用次数',
  `create_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='社区标签表';

-- 帖子标签关联表
CREATE TABLE `post_tag_rel` (
  `id`           BIGINT   NOT NULL COMMENT '主键，雪花 ID',
  `post_id`      BIGINT   NOT NULL COMMENT '帖子 ID',
  `tag_id`       BIGINT   NOT NULL COMMENT '标签 ID',
  `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_post_tag` (`post_id`, `tag_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='帖子标签关联表';

-- 评论点赞表
CREATE TABLE `comment_like` (
  `id`           BIGINT   NOT NULL COMMENT '主键，雪花 ID',
  `comment_id`   BIGINT   NOT NULL COMMENT '评论 ID',
  `user_id`      BIGINT   NOT NULL COMMENT '用户 ID',
  `create_time`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_comment_user` (`comment_id`, `user_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论点赞表';

-- 公告表
CREATE TABLE `announcement` (
  `id`           BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `title`        VARCHAR(200) NOT NULL COMMENT '公告标题',
  `content`      TEXT         NOT NULL COMMENT '公告内容',
  `status`       VARCHAR(32)  NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态：DRAFT / PUBLISHED',
  `sort`         INT          NOT NULL DEFAULT 0 COMMENT '排序值',
  `create_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`      TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告表';

-- 用户通知表
CREATE TABLE `user_notification` (
  `id`             BIGINT       NOT NULL COMMENT '主键，雪花 ID',
  `user_id`        BIGINT       NOT NULL COMMENT '接收用户 ID',
  `actor_id`       BIGINT       DEFAULT NULL COMMENT '触发用户 ID',
  `type`           VARCHAR(32)  NOT NULL COMMENT '通知类型：LIKE / COMMENT / FAVORITE',
  `post_id`        BIGINT       DEFAULT NULL COMMENT '帖子 ID',
  `comment_id`     BIGINT       DEFAULT NULL COMMENT '评论 ID',
  `content`        VARCHAR(500) DEFAULT NULL COMMENT '通知内容',
  `is_read`        TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读 1-已读',
  `create_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`        TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0-正常 1-已删除',
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `is_read`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户通知表';
