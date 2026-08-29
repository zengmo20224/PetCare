package com.petcare.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petcare.service.entity.ServiceCategory;
import com.petcare.service.mapper.ServiceCategoryMapper;
import com.petcare.service.service.ServiceCategoryService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServiceCategoryServiceImpl extends ServiceImpl<ServiceCategoryMapper, ServiceCategory> implements ServiceCategoryService {

    /**
     * 分类没有管理端写入口（仅种子数据），缓存恒安全；10 分钟 TTL 兜底人工改库。
     */
    @Override
    @Cacheable(cacheNames = "serviceCatalog", key = "'active-categories'",
            unless = "#result == null || #result.isEmpty()")
    public List<ServiceCategory> listActiveCategories() {
        return list(new LambdaQueryWrapper<ServiceCategory>()
                .eq(ServiceCategory::getStatus, "ACTIVE")
                .orderByAsc(ServiceCategory::getSort));
    }
}
