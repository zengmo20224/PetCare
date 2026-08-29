package com.petcare.common.config;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.petcare.common.security.SecurityContextHelper;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * MyBatis-Plus auto-fill handler.
 * Fills create_time on INSERT and update_time on INSERT/UPDATE.
 * Business timestamps (confirm_time, complete_time, etc.) are NOT auto-filled.
 * <p>
 * 同时为声明了对应字段的核心业务表（service_booking/product_order）填充操作人审计列：
 * create_by/update_by，取值为 admin:{id} / user:{id}。无登录态（定时任务等系统写入）
 * 不填充，行上保持 NULL——系统操作语义由 cancel_reason / merchant_remark /
 * booking_status_log 等业务字段承载。
 */
@Component
public class MyBatisMetaObjectHandler implements MetaObjectHandler {

    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        String actor = currentActor();
        if (actor != null) {
            this.strictInsertFill(metaObject, "createBy", String.class, actor);
            this.strictInsertFill(metaObject, "updateBy", String.class, actor);
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
        String actor = currentActor();
        // 审计语义要求"最后更新人"：不能用 strictUpdateFill（字段非 null 即跳过，
        // 会保留从库里读出的旧操作人），必须无条件覆盖。
        if (actor != null && metaObject.hasSetter("updateBy")) {
            metaObject.setValue("updateBy", actor);
        }
    }

    private String currentActor() {
        Optional<Long> adminId = SecurityContextHelper.getCurrentAdminId();
        if (adminId.isPresent()) {
            return "admin:" + adminId.get();
        }
        return SecurityContextHelper.getCurrentUserId().map(id -> "user:" + id).orElse(null);
    }
}
