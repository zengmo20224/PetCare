-- H2-compatible schema for testing.
-- Adapted from schema.sql with MySQL-specific syntax adjusted for H2.

-- A. User & Pet
CREATE TABLE IF NOT EXISTS `user` (
  `id`              BIGINT          NOT NULL,
  `openid`          VARCHAR(128)    DEFAULT NULL,
  `unionid`         VARCHAR(128)    DEFAULT NULL,
  `nickname`        VARCHAR(64)     DEFAULT NULL,
  `avatar_url`      VARCHAR(255)    DEFAULT NULL,
  `phone`           VARCHAR(20)     DEFAULT NULL,
  `password_hash`   VARCHAR(128)    DEFAULT NULL,
  `gender`          TINYINT         DEFAULT NULL,
  `status`          VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE',
  `last_login_time` TIMESTAMP       DEFAULT NULL,
  `real_name`       VARCHAR(64)     DEFAULT NULL,
  `id_card_no`      VARCHAR(32)     DEFAULT NULL,
  `id_card_image_url` VARCHAR(255)  DEFAULT NULL,
  `create_time`     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`         TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE (`phone`)
);

CREATE TABLE IF NOT EXISTS `user_security_question` (
  `id`          BIGINT       NOT NULL,
  `user_id`     BIGINT       NOT NULL,
  `question`    VARCHAR(255) NOT NULL,
  `answer_hash` VARCHAR(128) NOT NULL,
  `sort`        INT          NOT NULL DEFAULT 0,
  `create_time` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `pet` (
  `id`          BIGINT          NOT NULL,
  `user_id`     BIGINT          NOT NULL,
  `name`        VARCHAR(64)     DEFAULT NULL,
  `type`        VARCHAR(32)     DEFAULT NULL,
  `breed`       VARCHAR(64)     DEFAULT NULL,
  `gender`      TINYINT         DEFAULT NULL,
  `age`         DECIMAL(4,1)    DEFAULT NULL,
  `weight`      DECIMAL(5,2)    DEFAULT NULL,
  `size`        VARCHAR(32)     DEFAULT NULL,
  `sterilized`  TINYINT         DEFAULT NULL,
  `avatar_url`  VARCHAR(255)    DEFAULT NULL,
  `remark`      VARCHAR(500)    DEFAULT NULL,
  `create_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`     TINYINT         NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `user_address` (
  `id`             BIGINT       NOT NULL,
  `user_id`        BIGINT       NOT NULL,
  `contact_name`   VARCHAR(64)  DEFAULT NULL,
  `contact_phone`  VARCHAR(20)  DEFAULT NULL,
  `province`       VARCHAR(64)  DEFAULT NULL,
  `city`           VARCHAR(64)  DEFAULT NULL,
  `district`       VARCHAR(64)  DEFAULT NULL,
  `detail_address` VARCHAR(255) DEFAULT NULL,
  `longitude`      DECIMAL(10,6) DEFAULT NULL,
  `latitude`       DECIMAL(10,6) DEFAULT NULL,
  `is_default`     TINYINT      NOT NULL DEFAULT 0,
  `create_time`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

-- B. Store
CREATE TABLE IF NOT EXISTS `store` (
  `id`             BIGINT       NOT NULL,
  `store_name`     VARCHAR(100) DEFAULT NULL,
  `phone`          VARCHAR(20)  DEFAULT NULL,
  `address`        VARCHAR(255) DEFAULT NULL,
  `longitude`      DECIMAL(10,6) DEFAULT NULL,
  `latitude`       DECIMAL(10,6) DEFAULT NULL,
  `business_hours` VARCHAR(100) DEFAULT NULL,
  `status`         VARCHAR(32)  NOT NULL DEFAULT 'OPEN',
  `description`    VARCHAR(500) DEFAULT NULL,
  `create_time`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `store_config` (
  `id`                      BIGINT       NOT NULL,
  `store_id`                BIGINT       NOT NULL,
  `home_service_radius_km`  DECIMAL(5,2) NOT NULL DEFAULT 5.00,
  `booking_advance_days`    INT          NOT NULL DEFAULT 14,
  `booking_cancel_hours`    INT          NOT NULL DEFAULT 4,
  `time_slot_minutes`       INT          NOT NULL DEFAULT 30,
  `auto_confirm_booking`    TINYINT      NOT NULL DEFAULT 0,
  `content_auto_publish`    TINYINT      NOT NULL DEFAULT 1,
  `create_time`             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`             TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

-- C. Service & Staff
CREATE TABLE IF NOT EXISTS `service_category` (
  `id`          BIGINT      NOT NULL,
  `name`        VARCHAR(64) DEFAULT NULL,
  `icon_url`    VARCHAR(255) DEFAULT NULL,
  `sort`        INT         NOT NULL DEFAULT 0,
  `status`      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `create_time` TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `service_item` (
  `id`               BIGINT       NOT NULL,
  `category_id`      BIGINT       NOT NULL,
  `name`             VARCHAR(100) DEFAULT NULL,
  `service_mode`     VARCHAR(32)  NOT NULL DEFAULT 'STORE',
  `price`            DECIMAL(10,2) DEFAULT NULL,
  `duration_minutes` INT          DEFAULT NULL,
  `pet_type`         VARCHAR(32)  DEFAULT NULL,
  `pet_size`         VARCHAR(32)  DEFAULT NULL,
  `need_address`     TINYINT      NOT NULL DEFAULT 0,
  `need_pet`         TINYINT      NOT NULL DEFAULT 1,
  `description`      CLOB         DEFAULT NULL,
  `cover_url`        VARCHAR(255) DEFAULT NULL,
  `status`           VARCHAR(32)  NOT NULL DEFAULT 'ON_SALE',
  `sort`             INT          NOT NULL DEFAULT 0,
  `create_time`      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`          TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `service_item_image` (
  `id`              BIGINT       NOT NULL,
  `service_item_id` BIGINT       NOT NULL,
  `image_url`       VARCHAR(255) NOT NULL,
  `sort`            INT          NOT NULL DEFAULT 0,
  `create_time`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `staff` (
  `id`          BIGINT       NOT NULL,
  `store_id`    BIGINT       NOT NULL,
  `name`        VARCHAR(64)  DEFAULT NULL,
  `phone`       VARCHAR(20)  DEFAULT NULL,
  `avatar_url`  VARCHAR(255) DEFAULT NULL,
  `role`        VARCHAR(32)  NOT NULL DEFAULT 'GROOMER',
  `status`      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  `description` VARCHAR(500) DEFAULT NULL,
  `create_time` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `staff_skill` (
  `id`                  BIGINT   NOT NULL,
  `staff_id`            BIGINT   NOT NULL,
  `service_category_id` BIGINT   NOT NULL,
  `create_time`         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

-- D. Booking
CREATE TABLE IF NOT EXISTS `staff_schedule` (
  `id`          BIGINT      NOT NULL,
  `staff_id`    BIGINT      NOT NULL,
  `store_id`    BIGINT      NOT NULL,
  `work_date`   DATE        NOT NULL,
  `start_time`  TIME        NOT NULL,
  `end_time`    TIME        NOT NULL,
  `status`      VARCHAR(32) NOT NULL DEFAULT 'AVAILABLE',
  `remark`      VARCHAR(255) DEFAULT NULL,
  `create_time` TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `staff_unavailable_time` (
  `id`                BIGINT      NOT NULL,
  `staff_id`          BIGINT      NOT NULL,
  `unavailable_date`  DATE        NOT NULL,
  `start_time`        TIME        NOT NULL,
  `end_time`          TIME        NOT NULL,
  `reason_type`       VARCHAR(32) NOT NULL DEFAULT 'TEMP',
  `reason`            VARCHAR(255) DEFAULT NULL,
  `create_time`       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`           TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `staff_booking_lock` (
  `id`           BIGINT   NOT NULL,
  `staff_id`     BIGINT   NOT NULL,
  `booking_date` DATE     NOT NULL,
  `create_time`  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`staff_id`, `booking_date`)
);

CREATE TABLE IF NOT EXISTS `service_booking` (
  `id`              BIGINT        NOT NULL,
  `booking_no`      VARCHAR(64)   NOT NULL,
  `user_id`         BIGINT        NOT NULL,
  `pet_id`          BIGINT        DEFAULT NULL,
  `store_id`        BIGINT        NOT NULL,
  `service_item_id` BIGINT        NOT NULL,
  `staff_id`        BIGINT        DEFAULT NULL,
  `service_mode`    VARCHAR(32)   NOT NULL DEFAULT 'STORE',
  `booking_date`    DATE          NOT NULL,
  `start_time`      TIME          NOT NULL,
  `end_time`        TIME          NOT NULL,
  `address_id`      BIGINT        DEFAULT NULL,
  `distance_km`     DECIMAL(6,2)  DEFAULT NULL,
  `contact_name`    VARCHAR(64)   DEFAULT NULL,
  `contact_phone`   VARCHAR(20)   DEFAULT NULL,
  `price`           DECIMAL(10,2) DEFAULT NULL,
  `payment_method`  VARCHAR(32)   DEFAULT NULL,
  `payment_status`  VARCHAR(32)   NOT NULL DEFAULT 'UNPAID',
  `status`          VARCHAR(32)   NOT NULL DEFAULT 'PENDING_CONFIRM',
  `remark`          VARCHAR(500)  DEFAULT NULL,
  `merchant_remark` VARCHAR(500)  DEFAULT NULL,
  `confirm_time`    TIMESTAMP     DEFAULT NULL,
  `complete_time`   TIMESTAMP     DEFAULT NULL,
  `cancel_time`     TIMESTAMP     DEFAULT NULL,
  `cancel_reason`   VARCHAR(255)  DEFAULT NULL,
  `create_time`     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`         TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `booking_status_log` (
  `id`            BIGINT      NOT NULL,
  `booking_id`    BIGINT      NOT NULL,
  `old_status`    VARCHAR(32) DEFAULT NULL,
  `new_status`    VARCHAR(32) NOT NULL,
  `operator_type` VARCHAR(32) NOT NULL,
  `operator_id`   BIGINT      DEFAULT NULL,
  `remark`        VARCHAR(500) DEFAULT NULL,
  `create_time`   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

-- E. Community
CREATE TABLE IF NOT EXISTS `topic` (
  `id`          BIGINT      NOT NULL,
  `name`        VARCHAR(64) DEFAULT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `sort`        INT         NOT NULL DEFAULT 0,
  `status`      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `create_time` TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `post` (
  `id`             BIGINT       NOT NULL,
  `user_id`        BIGINT       NOT NULL,
  `pet_id`         BIGINT       DEFAULT NULL,
  `topic_id`       BIGINT       DEFAULT NULL,
  `title`          VARCHAR(120) DEFAULT NULL,
  `content`        CLOB         DEFAULT NULL,
  `status`         VARCHAR(32)  NOT NULL DEFAULT 'PUBLISHED',
  `risk_level`     TINYINT      NOT NULL DEFAULT 0,
  `view_count`     INT          NOT NULL DEFAULT 0,
  `like_count`     INT          NOT NULL DEFAULT 0,
  `comment_count`  INT          NOT NULL DEFAULT 0,
  `favorite_count` INT          NOT NULL DEFAULT 0,
  `reject_reason`  VARCHAR(255) DEFAULT NULL,
  `publish_time`   TIMESTAMP    DEFAULT NULL,
  `create_time`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `post_image` (
  `id`          BIGINT       NOT NULL,
  `post_id`     BIGINT       NOT NULL,
  `image_url`   VARCHAR(255) NOT NULL,
  `sort`        INT          NOT NULL DEFAULT 0,
  `create_time` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `post_comment` (
  `id`          BIGINT        NOT NULL,
  `post_id`     BIGINT        NOT NULL,
  `user_id`     BIGINT        NOT NULL,
  `parent_id`   BIGINT        DEFAULT NULL,
  `content`     VARCHAR(1000) DEFAULT NULL,
  `status`      VARCHAR(32)   NOT NULL DEFAULT 'PUBLISHED',
  `risk_level`  TINYINT       NOT NULL DEFAULT 0,
  `like_count`  INT           NOT NULL DEFAULT 0,
  `create_time` TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`     TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `post_like` (
  `id`          BIGINT   NOT NULL,
  `post_id`     BIGINT   NOT NULL,
  `user_id`     BIGINT   NOT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `post_favorite` (
  `id`          BIGINT   NOT NULL,
  `post_id`     BIGINT   NOT NULL,
  `user_id`     BIGINT   NOT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `post_report` (
  `id`           BIGINT       NOT NULL,
  `post_id`      BIGINT       NOT NULL,
  `reporter_id`  BIGINT       NOT NULL,
  `reason_type`  VARCHAR(32)  NOT NULL,
  `reason`       VARCHAR(500) DEFAULT NULL,
  `status`       VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
  `handle_result` VARCHAR(500) DEFAULT NULL,
  `handler_id`   BIGINT       DEFAULT NULL,
  `handle_time`  TIMESTAMP    DEFAULT NULL,
  `create_time`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

-- F. Moderation
CREATE TABLE IF NOT EXISTS `sensitive_word` (
  `id`          BIGINT       NOT NULL,
  `word`        VARCHAR(100) NOT NULL,
  `category`    VARCHAR(32)  DEFAULT NULL,
  `level`       TINYINT      NOT NULL DEFAULT 1,
  `status`      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  `create_time` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `content_review_record` (
  `id`            BIGINT      NOT NULL,
  `content_type`  VARCHAR(32) NOT NULL,
  `content_id`    BIGINT      NOT NULL,
  `user_id`       BIGINT      NOT NULL,
  `risk_level`    TINYINT     NOT NULL DEFAULT 0,
  `matched_words` VARCHAR(500) DEFAULT NULL,
  `review_status` VARCHAR(32) NOT NULL DEFAULT 'PENDING',
  `reviewer_id`   BIGINT      DEFAULT NULL,
  `review_remark` VARCHAR(500) DEFAULT NULL,
  `create_time`   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `review_time`   TIMESTAMP   DEFAULT NULL,
  PRIMARY KEY (`id`)
);

-- G. Product
CREATE TABLE IF NOT EXISTS `product_category` (
  `id`          BIGINT      NOT NULL,
  `name`        VARCHAR(64) DEFAULT NULL,
  `icon_url`    VARCHAR(255) DEFAULT NULL,
  `sort`        INT         NOT NULL DEFAULT 0,
  `status`      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `create_time` TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `product` (
  `id`          BIGINT        NOT NULL,
  `category_id` BIGINT        NOT NULL,
  `name`        VARCHAR(100)  DEFAULT NULL,
  `cover_url`   VARCHAR(255)  DEFAULT NULL,
  `price`       DECIMAL(10,2) DEFAULT NULL,
  `stock`       INT           NOT NULL DEFAULT 0,
  `sales_count` INT           NOT NULL DEFAULT 0,
  `description` CLOB          DEFAULT NULL,
  `pickup_only` TINYINT       NOT NULL DEFAULT 1,
  `status`      VARCHAR(32)   NOT NULL DEFAULT 'ON_SALE',
  `sort`        INT           NOT NULL DEFAULT 0,
  `create_time` TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`     TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `product_image` (
  `id`          BIGINT       NOT NULL,
  `product_id`  BIGINT       NOT NULL,
  `image_url`   VARCHAR(255) NOT NULL,
  `sort`        INT          NOT NULL DEFAULT 0,
  `create_time` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `product_detail_image` (
  `id`          BIGINT       NOT NULL,
  `product_id`  BIGINT       NOT NULL,
  `image_url`   VARCHAR(255) NOT NULL,
  `sort`        INT          NOT NULL DEFAULT 0,
  `create_time` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `product_carousel_image` (
  `id`             BIGINT       NOT NULL,
  `title`          VARCHAR(80)  DEFAULT NULL,
  `image_url`      VARCHAR(255) NOT NULL,
  `link_type`      VARCHAR(32)  DEFAULT 'NONE',
  `link_target_id` BIGINT       DEFAULT NULL,
  `status`         VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  `sort`           INT          NOT NULL DEFAULT 0,
  `create_time`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`        TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `cart_item` (
  `id`          BIGINT   NOT NULL,
  `user_id`     BIGINT   NOT NULL,
  `product_id`  BIGINT   NOT NULL,
  `quantity`    INT      NOT NULL DEFAULT 1,
  `checked`     TINYINT  NOT NULL DEFAULT 1,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `product_order` (
  `id`              BIGINT        NOT NULL,
  `order_no`        VARCHAR(64)   NOT NULL,
  `user_id`         BIGINT        NOT NULL,
  `store_id`        BIGINT        NULL,
  `total_amount`    DECIMAL(10,2) NOT NULL,
  `delivery_method` VARCHAR(16)   NOT NULL DEFAULT 'PICKUP',
  `address_id`      BIGINT        DEFAULT NULL,
  `address_snapshot` VARCHAR(500) DEFAULT NULL,
  `payment_method`  VARCHAR(32)   DEFAULT NULL,
  `payment_status`  VARCHAR(32)   NOT NULL DEFAULT 'UNPAID',
  `pickup_status`   VARCHAR(32)   NOT NULL DEFAULT 'WAIT_PREPARE',
  `status`          VARCHAR(32)   NOT NULL DEFAULT 'PENDING_CONFIRM',
  `contact_name`    VARCHAR(64)   DEFAULT NULL,
  `contact_phone`   VARCHAR(20)   DEFAULT NULL,
  `remark`          VARCHAR(500)  DEFAULT NULL,
  `merchant_remark` VARCHAR(500)  DEFAULT NULL,
  `confirm_time`    TIMESTAMP     DEFAULT NULL,
  `complete_time`   TIMESTAMP     DEFAULT NULL,
  `cancel_time`     TIMESTAMP     DEFAULT NULL,
  `idempotency_key` VARCHAR(64)   DEFAULT NULL,
  `create_time`     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`         TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE (`user_id`, `idempotency_key`)
);

CREATE TABLE IF NOT EXISTS `product_order_item` (
  `id`                BIGINT        NOT NULL,
  `order_id`          BIGINT        NOT NULL,
  `product_id`        BIGINT        NOT NULL,
  `product_name`      VARCHAR(100)  DEFAULT NULL,
  `product_cover_url` VARCHAR(255)  DEFAULT NULL,
  `price`             DECIMAL(10,2) NOT NULL,
  `quantity`          INT           NOT NULL,
  `total_amount`      DECIMAL(10,2) NOT NULL,
  `create_time`       TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

-- H. Marketing
CREATE TABLE IF NOT EXISTS `marketing_activity` (
  `id`            BIGINT       NOT NULL,
  `title`         VARCHAR(100) DEFAULT NULL,
  `activity_type` VARCHAR(32)  NOT NULL DEFAULT 'MIXED',
  `description`   CLOB         DEFAULT NULL,
  `cover_url`     VARCHAR(255) DEFAULT NULL,
  `start_time`    TIMESTAMP    DEFAULT NULL,
  `end_time`      TIMESTAMP    DEFAULT NULL,
  `status`        VARCHAR(32)  NOT NULL DEFAULT 'DRAFT',
  `create_time`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`       TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `activity_product` (
  `id`          BIGINT   NOT NULL,
  `activity_id` BIGINT   NOT NULL,
  `product_id`  BIGINT   NOT NULL,
  `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `activity_service` (
  `id`              BIGINT   NOT NULL,
  `activity_id`     BIGINT   NOT NULL,
  `service_item_id` BIGINT   NOT NULL,
  `create_time`     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

-- I. AI
CREATE TABLE IF NOT EXISTS `ai_conversation` (
  `id`                BIGINT      NOT NULL,
  `user_id`           BIGINT      DEFAULT NULL,
  `admin_id`          BIGINT      DEFAULT NULL,
  `conversation_type` VARCHAR(32) NOT NULL,
  `title`             VARCHAR(100) DEFAULT NULL,
  `create_time`       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`           TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `ai_message` (
  `id`              BIGINT      NOT NULL,
  `conversation_id` BIGINT      NOT NULL,
  `role`            VARCHAR(32) NOT NULL,
  `content`         CLOB        DEFAULT NULL,
  `token_count`     INT         DEFAULT NULL,
  `create_time`     TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `ai_usage_log` (
  `id`                BIGINT       NOT NULL,
  `user_id`           BIGINT       DEFAULT NULL,
  `admin_id`          BIGINT       DEFAULT NULL,
  `api_type`          VARCHAR(32)  NOT NULL,
  `model_name`        VARCHAR(100) DEFAULT NULL,
  `prompt_tokens`     INT          DEFAULT NULL,
  `completion_tokens` INT          DEFAULT NULL,
  `total_tokens`      INT          DEFAULT NULL,
  `success`           TINYINT      NOT NULL DEFAULT 1,
  `error_message`     VARCHAR(1000) DEFAULT NULL,
  `create_time`       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `ai_analysis_report` (
  `id`             BIGINT      NOT NULL,
  `report_type`    VARCHAR(32) NOT NULL,
  `start_date`     DATE        DEFAULT NULL,
  `end_date`       DATE        DEFAULT NULL,
  `raw_data_json`  CLOB        DEFAULT NULL,
  `ai_summary`     CLOB        DEFAULT NULL,
  `suggestions`    CLOB        DEFAULT NULL,
  `created_by`     BIGINT      DEFAULT NULL,
  `create_time`    TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `faq_knowledge` (
  `id`          BIGINT       NOT NULL,
  `question`    VARCHAR(255) DEFAULT NULL,
  `answer`      CLOB         DEFAULT NULL,
  `category`    VARCHAR(32)  DEFAULT NULL,
  `status`      VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  `create_time` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`     TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

-- J. Admin
CREATE TABLE IF NOT EXISTS `admin_user` (
  `id`              BIGINT       NOT NULL,
  `username`        VARCHAR(64)  NOT NULL,
  `password`        VARCHAR(255) NOT NULL,
  `nickname`        VARCHAR(64)  DEFAULT NULL,
  `phone`           VARCHAR(20)  DEFAULT NULL,
  `role`            VARCHAR(32)  NOT NULL DEFAULT 'STAFF',
  `status`          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  `last_login_time` TIMESTAMP    DEFAULT NULL,
  `create_time`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `admin_role` (
  `id`          BIGINT      NOT NULL,
  `role_code`   VARCHAR(64) NOT NULL,
  `role_name`   VARCHAR(64) DEFAULT NULL,
  `description` VARCHAR(255) DEFAULT NULL,
  `status`      VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  `create_time` TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`     TINYINT     NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `admin_permission` (
  `id`              BIGINT       NOT NULL,
  `permission_code` VARCHAR(128) NOT NULL,
  `permission_name` VARCHAR(100) DEFAULT NULL,
  `module`          VARCHAR(64)  DEFAULT NULL,
  `description`     VARCHAR(255) DEFAULT NULL,
  `status`          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
  `create_time`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`         TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `admin_role_permission` (
  `id`             BIGINT   NOT NULL,
  `role_id`        BIGINT   NOT NULL,
  `permission_id`  BIGINT   NOT NULL,
  `create_time`    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`role_id`, `permission_id`)
);

CREATE TABLE IF NOT EXISTS `admin_operation_log` (
  `id`             BIGINT       NOT NULL,
  `admin_id`       BIGINT       NOT NULL,
  `module`         VARCHAR(64)  DEFAULT NULL,
  `operation`      VARCHAR(100) DEFAULT NULL,
  `request_method` VARCHAR(16)  DEFAULT NULL,
  `request_url`    VARCHAR(255) DEFAULT NULL,
  `request_params` CLOB         DEFAULT NULL,
  `ip`             VARCHAR(64)  DEFAULT NULL,
  `result`         VARCHAR(32)  DEFAULT NULL,
  `error_message`  VARCHAR(1000) DEFAULT NULL,
  `create_time`    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

-- Phase 8: Phone blacklist (progressive ban history)
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

-- Phase 4: Community tag system
CREATE TABLE IF NOT EXISTS `community_tag` (
  `id`           BIGINT       NOT NULL,
  `name`         VARCHAR(64)  NOT NULL,
  `usage_count`  INT          NOT NULL DEFAULT 0,
  `create_time`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`      TINYINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `post_tag_rel` (
  `id`           BIGINT       NOT NULL,
  `post_id`      BIGINT       NOT NULL,
  `tag_id`       BIGINT       NOT NULL,
  `create_time`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
);

-- Phase 2: Comment likes
CREATE TABLE IF NOT EXISTS `comment_like` (
  `id`           BIGINT       NOT NULL,
  `comment_id`   BIGINT       NOT NULL,
  `user_id`      BIGINT       NOT NULL,
  `create_time`  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE (`comment_id`, `user_id`)
);

-- Phase 5: Announcements and notifications
CREATE TABLE IF NOT EXISTS `announcement` (
  `id`           BIGINT        NOT NULL,
  `title`        VARCHAR(200)  NOT NULL,
  `content`      CLOB          NOT NULL,
  `status`       VARCHAR(32)   NOT NULL DEFAULT 'PUBLISHED',
  `sort`         INT           NOT NULL DEFAULT 0,
  `create_time`  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`      TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);

CREATE TABLE IF NOT EXISTS `user_notification` (
  `id`             BIGINT        NOT NULL,
  `user_id`        BIGINT        NOT NULL,
  `actor_id`       BIGINT        DEFAULT NULL,
  `type`           VARCHAR(32)   NOT NULL,
  `post_id`        BIGINT        DEFAULT NULL,
  `comment_id`     BIGINT        DEFAULT NULL,
  `content`        VARCHAR(500)  DEFAULT NULL,
  `is_read`        TINYINT       NOT NULL DEFAULT 0,
  `create_time`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`    TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `deleted`        TINYINT       NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`)
);
