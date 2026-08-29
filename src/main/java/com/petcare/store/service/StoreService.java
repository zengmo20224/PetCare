package com.petcare.store.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.petcare.store.entity.Store;

import java.util.List;

public interface StoreService extends IService<Store> {

    /**
     * 营业中的门店列表（用户端首页热点读，Caffeine 缓存，管理端改门店后整组失效）。
     */
    List<Store> listOpenStores();

    /**
     * 按 id 取门店（保留非营业状态可见的既有语义）。
     */
    Store getOpenStore(Long id);
}
