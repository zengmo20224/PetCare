package com.petcare.store.controller;

import com.petcare.common.api.ApiResponse;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.store.dto.StoreResponse;
import com.petcare.store.entity.Store;
import com.petcare.store.service.StoreService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Public store endpoints for the mini-app.
 * <p>Browsing store info is anonymous (same as product catalog) — customers need
 * to see available pickup stores before logging in.
 */
@RestController
@RequestMapping("/api/v1/stores")
public class StoreController {

    private final StoreService storeService;

    public StoreController(StoreService storeService) {
        this.storeService = storeService;
    }

    /** List open stores (sorted by id ascending for stable ordering). */
    @GetMapping
    public ApiResponse<List<StoreResponse>> listStores() {
        List<StoreResponse> items = storeService.listOpenStores().stream()
                .map(this::toResponse)
                .toList();
        return ApiResponse.ok(items);
    }

    /** Get a single store by id. */
    @GetMapping("/{id}")
    public ApiResponse<StoreResponse> getStore(@PathVariable Long id) {
        Store store = storeService.getOpenStore(id);
        if (store == null) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, "门店不存在");
        }
        return ApiResponse.ok(toResponse(store));
    }

    private StoreResponse toResponse(Store store) {
        return new StoreResponse(
                store.getId(),
                store.getStoreName(),
                store.getAddress(),
                store.getPhone(),
                store.getBusinessHours(),
                store.getDescription(),
                store.getStatus());
    }
}
