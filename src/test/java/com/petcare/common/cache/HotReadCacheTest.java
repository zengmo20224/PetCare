package com.petcare.common.cache;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petcare.admin.dto.AdminManagementDtos.ServiceItemRequest;
import com.petcare.admin.dto.AdminManagementDtos.StoreUpdateRequest;
import com.petcare.admin.service.AdminManagementService;
import com.petcare.service.entity.ServiceCategory;
import com.petcare.service.entity.ServiceItem;
import com.petcare.service.mapper.ServiceCategoryMapper;
import com.petcare.service.mapper.ServiceItemMapper;
import com.petcare.service.service.ServiceCategoryService;
import com.petcare.service.service.ServiceItemService;
import com.petcare.store.entity.Store;
import com.petcare.store.mapper.StoreMapper;
import com.petcare.store.service.StoreService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 热点读缓存行为（唯一启用 Caffeine 的测试上下文：显式覆盖 spring.cache.type，
 * 其余测试 profile 走 NoOp 缓存，避免跨测试类假命中）。
 * 验证：二次读不落库、管理端写入后整组失效（写后删策略）。
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.cache.type=caffeine")
@Transactional
class HotReadCacheTest {

    @Autowired
    private ServiceCategoryService serviceCategoryService;
    @Autowired
    private ServiceItemService serviceItemService;
    @Autowired
    private StoreService storeService;
    @Autowired
    private AdminManagementService adminManagementService;
    @Autowired
    private CacheManager cacheManager;

    @SpyBean
    private ServiceCategoryMapper serviceCategoryMapper;
    @SpyBean
    private ServiceItemMapper serviceItemMapper;
    @SpyBean
    private StoreMapper storeMapper;

    private ServiceCategory category;
    private ServiceItem item;
    private Store store;

    @BeforeEach
    void setUp() {
        category = new ServiceCategory();
        category.setName("缓存测试分类");
        category.setSort(1);
        category.setStatus("ACTIVE");
        serviceCategoryMapper.insert(category);

        item = new ServiceItem();
        item.setCategoryId(category.getId());
        item.setName("缓存测试服务");
        item.setServiceMode("BOTH");
        item.setPrice(new BigDecimal("66.00"));
        item.setDurationMinutes(45);
        item.setPetType("ALL");
        item.setPetSize("ALL");
        item.setNeedAddress(0);
        item.setNeedPet(1);
        item.setStatus("ON_SALE");
        item.setSort(0);
        serviceItemMapper.insert(item);

        store = new Store();
        store.setStoreName("缓存测试门店");
        store.setLongitude(new BigDecimal("116.407400"));
        store.setLatitude(new BigDecimal("39.904200"));
        store.setStatus("OPEN");
        store.setBusinessHours("09:00-18:00");
        storeMapper.insert(store);
    }

    @AfterEach
    void clearCaches() {
        for (String name : List.of("serviceCatalog", "storeProfile")) {
            var cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        }
    }

    @Test
    @DisplayName("服务分类列表二次读命中缓存，不再查库")
    void categoriesAreCachedOnSecondRead() {
        serviceCategoryService.listActiveCategories();
        clearInvocations(serviceCategoryMapper);

        serviceCategoryService.listActiveCategories();

        verify(serviceCategoryMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("服务项详情与列表二次读命中缓存")
    void serviceItemsAreCachedOnSecondRead() {
        serviceItemService.getOnSaleItem(item.getId());
        serviceItemService.listOnSaleItems(category.getId(), null, null, null, new Page<>(1, 10));
        clearInvocations(serviceItemMapper);

        serviceItemService.getOnSaleItem(item.getId());
        serviceItemService.listOnSaleItems(category.getId(), null, null, null, new Page<>(1, 10));

        verify(serviceItemMapper, never()).selectById(any());
        verify(serviceItemMapper, never()).selectList(any());
    }

    @Test
    @DisplayName("门店列表与详情二次读命中缓存")
    void storesAreCachedOnSecondRead() {
        storeService.listOpenStores();
        storeService.getOpenStore(store.getId());
        clearInvocations(storeMapper);

        storeService.listOpenStores();
        storeService.getOpenStore(store.getId());

        verify(storeMapper, never()).selectList(any());
        verify(storeMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("管理端更新服务项后，serviceCatalog 整组失效、下次读重新查库")
    void serviceItemWriteEvictsCatalogCache() {
        serviceCategoryService.listActiveCategories();
        serviceItemService.getOnSaleItem(item.getId());
        clearInvocations(serviceCategoryMapper);

        ServiceItemRequest request = new ServiceItemRequest(category.getId(), "缓存测试服务V2",
                "STORE", new BigDecimal("88.00"), 60, "ALL", "ALL",
                Boolean.FALSE, Boolean.TRUE, null, null, List.of(), 0);
        adminManagementService.updateServiceItem(item.getId(), request, 1L);

        serviceCategoryService.listActiveCategories();
        verify(serviceCategoryMapper, times(1)).selectList(any());
    }

    @Test
    @DisplayName("管理端更新门店后，storeProfile 整组失效、下次读重新查库")
    void storeUpdateEvictsStoreProfileCache() {
        storeService.listOpenStores();
        clearInvocations(storeMapper);

        StoreUpdateRequest request = new StoreUpdateRequest("缓存测试门店V2", "010-123",
                "北京市朝阳区", new BigDecimal("116.40"), new BigDecimal("39.90"),
                "09:00-20:00", "OPEN", "更新后的描述");
        adminManagementService.updateStore(store.getId(), request, 1L);

        storeService.listOpenStores();
        verify(storeMapper, times(1)).selectList(any());
    }
}
