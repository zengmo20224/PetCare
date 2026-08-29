package com.petcare.store.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.petcare.store.entity.Store;
import com.petcare.store.mapper.StoreMapper;
import com.petcare.store.service.StoreService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 门店读服务。热点读走 {@code storeProfile} 缓存（全局 Caffeine spec：500 条 / 10 分钟），
 * 统一"写后删"策略：管理端 updateStore 时整组失效（allEntries），不追单 key。
 */
@Service
public class StoreServiceImpl extends ServiceImpl<StoreMapper, Store> implements StoreService {

    @Override
    @Cacheable(cacheNames = "storeProfile", key = "'open-stores'",
            unless = "#result == null || #result.isEmpty()")
    public List<Store> listOpenStores() {
        // @TableLogic 使 list() 自动过滤 deleted=0，与原控制器写法等价
        return list(new LambdaQueryWrapper<Store>()
                .eq(Store::getStatus, "OPEN")
                .orderByAsc(Store::getId));
    }

    @Override
    @Cacheable(cacheNames = "storeProfile", key = "'id:' + #p0", unless = "#result == null")
    public Store getOpenStore(Long id) {
        return getById(id);
    }
}
