package com.petcare.service.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petcare.common.api.ApiResponse;
import com.petcare.common.pagination.PageResponse;
import com.petcare.service.dto.ServiceCategoryResponse;
import com.petcare.service.dto.ServiceItemResponse;
import com.petcare.service.entity.ServiceCategory;
import com.petcare.service.entity.ServiceItem;
import com.petcare.service.entity.ServiceItemImage;
import com.petcare.service.service.ServiceCategoryService;
import com.petcare.service.service.ServiceItemImageService;
import com.petcare.service.service.ServiceItemService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Public-facing service catalog endpoints.
 * Lists service categories and on-sale service items.
 */
@RestController
@RequestMapping("/api/v1")
public class ServiceCatalogController {

    private final ServiceCategoryService serviceCategoryService;
    private final ServiceItemService serviceItemService;
    private final ServiceItemImageService serviceItemImageService;

    public ServiceCatalogController(ServiceCategoryService serviceCategoryService,
                                    ServiceItemService serviceItemService,
                                    ServiceItemImageService serviceItemImageService) {
        this.serviceCategoryService = serviceCategoryService;
        this.serviceItemService = serviceItemService;
        this.serviceItemImageService = serviceItemImageService;
    }

    /**
     * Lists all active service categories.
     */
    @GetMapping("/service-categories")
    public ApiResponse<List<ServiceCategoryResponse>> listCategories() {
        List<ServiceCategory> categories = serviceCategoryService.listActiveCategories();
        List<ServiceCategoryResponse> response = categories.stream()
                .map(this::toCategoryResponse)
                .toList();
        return ApiResponse.ok(response);
    }

    /**
     * Lists on-sale service items with optional filters and pagination.
     */
    @GetMapping("/service-items")
    public ApiResponse<PageResponse<ServiceItemResponse>> listItems(
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String serviceMode,
            @RequestParam(required = false) String petType,
            @RequestParam(required = false) String petSize,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        // Clamp size to reasonable max
        int effectiveSize = Math.min(Math.max(size, 1), 100);

        Page<ServiceItem> pageParam = new Page<>(page, effectiveSize);
        IPage<ServiceItem> result = serviceItemService.listOnSaleItems(
                categoryId, serviceMode, petType, petSize, pageParam);

        // Batch-load images for the whole page in one query to avoid N+1.
        List<ServiceItem> pageItems = result.getRecords();
        Map<Long, List<String>> imagesByItemId = loadImageUrls(
                pageItems.stream().map(ServiceItem::getId).toList()
        );

        List<ServiceItemResponse> items = pageItems.stream()
                .map(i -> toItemResponse(i, imagesByItemId.getOrDefault(i.getId(), Collections.emptyList())))
                .toList();

        return ApiResponse.ok(PageResponse.of(items, result.getTotal(), page, effectiveSize));
    }

    /**
     * Gets a single on-sale service item by ID.
     */
    @GetMapping("/service-items/{id}")
    public ApiResponse<ServiceItemResponse> getItem(@PathVariable Long id) {
        ServiceItem item = serviceItemService.getOnSaleItem(id);
        return ApiResponse.ok(toItemResponse(item));
    }

    private ServiceCategoryResponse toCategoryResponse(ServiceCategory c) {
        return new ServiceCategoryResponse(c.getId(), c.getName(), c.getIconUrl(), c.getSort());
    }

    private ServiceItemResponse toItemResponse(ServiceItem i) {
        return toItemResponse(i, imageUrls(i.getId()));
    }

    private ServiceItemResponse toItemResponse(ServiceItem i, List<String> imageUrls) {
        return new ServiceItemResponse(
                i.getId(), i.getCategoryId(), i.getName(), i.getServiceMode(),
                i.getPrice(), i.getDurationMinutes(), i.getPetType(), i.getPetSize(),
                i.getNeedAddress(), i.getNeedPet(), i.getDescription(), i.getCoverUrl(),
                imageUrls);
    }

    private List<String> imageUrls(Long serviceItemId) {
        return serviceItemImageService.list(new LambdaQueryWrapper<ServiceItemImage>()
                        .eq(ServiceItemImage::getServiceItemId, serviceItemId)
                        .orderByAsc(ServiceItemImage::getSort))
                .stream()
                .map(ServiceItemImage::getImageUrl)
                .toList();
    }

    /**
     * Batch-loads image URLs for a set of service items in a single query,
     * grouped by service item id, ordered by sort. Avoids N+1 on list endpoints.
     */
    private Map<Long, List<String>> loadImageUrls(List<Long> serviceItemIds) {
        if (serviceItemIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<ServiceItemImage> images = serviceItemImageService.list(
                new LambdaQueryWrapper<ServiceItemImage>()
                        .in(ServiceItemImage::getServiceItemId, serviceItemIds)
                        .orderByAsc(ServiceItemImage::getSort)
        );
        return images.stream()
                .collect(Collectors.groupingBy(
                        ServiceItemImage::getServiceItemId,
                        Collectors.mapping(ServiceItemImage::getImageUrl, Collectors.toList())
                ));
    }
}
