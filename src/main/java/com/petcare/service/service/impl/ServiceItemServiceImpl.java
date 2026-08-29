package com.petcare.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.service.entity.ServiceItem;
import com.petcare.service.mapper.ServiceItemMapper;
import com.petcare.service.service.ServiceItemService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 服务项目读服务。热点读走 {@code serviceCatalog} 缓存（全局 Caffeine spec），
 * 管理端对服务项的增改/上下架/删除统一整组失效（见 AdminManagementServiceImpl 的 @CacheEvict）。
 * 商品目录刻意不缓存：库存随下单/取消高频变动，写后删的失效流远大于读收益。
 */
@Service
public class ServiceItemServiceImpl extends ServiceImpl<ServiceItemMapper, ServiceItem> implements ServiceItemService {

    @Override
    @Cacheable(cacheNames = "serviceCatalog",
            key = "'items:' + #p0 + ':' + #p1 + ':' + #p2 + ':' + #p3 + ':' + #p4.current + '_' + #p4.size",
            unless = "#result == null")
    public IPage<ServiceItem> listOnSaleItems(Long categoryId, String serviceMode,
                                              String petType, String petSize,
                                              Page<ServiceItem> page) {
        LambdaQueryWrapper<ServiceItem> wrapper = new LambdaQueryWrapper<ServiceItem>()
                .eq(ServiceItem::getStatus, "ON_SALE")
                .orderByAsc(ServiceItem::getSort);

        if (categoryId != null) {
            wrapper.eq(ServiceItem::getCategoryId, categoryId);
        }
        if (serviceMode != null && !serviceMode.isBlank()) {
            wrapper.and(w -> w.eq(ServiceItem::getServiceMode, serviceMode)
                    .or().eq(ServiceItem::getServiceMode, "BOTH"));
        }
        if (petType != null && !petType.isBlank()) {
            wrapper.and(w -> w.eq(ServiceItem::getPetType, petType)
                    .or().eq(ServiceItem::getPetType, "ALL"));
        }
        if (petSize != null && !petSize.isBlank()) {
            wrapper.and(w -> w.eq(ServiceItem::getPetSize, petSize)
                    .or().eq(ServiceItem::getPetSize, "ALL"));
        }

        return page(page, wrapper);
    }

    @Override
    @Cacheable(cacheNames = "serviceCatalog", key = "'item:' + #p0", unless = "#result == null")
    public ServiceItem getOnSaleItem(Long id) {
        ServiceItem item = getById(id);
        if (item == null || !"ON_SALE".equals(item.getStatus())) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "服务项目不存在或已下架");
        }
        return item;
    }
}
