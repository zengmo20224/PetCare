package com.petcare.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petcare.admin.dto.AdminManagementDtos.OperationLogView;
import com.petcare.admin.dto.AdminManagementDtos.ProductCarouselImageRequest;
import com.petcare.admin.dto.AdminManagementDtos.ProductCarouselImageView;
import com.petcare.admin.dto.AdminManagementDtos.ProductCarouselImagesUpdateRequest;
import com.petcare.admin.dto.AdminManagementDtos.ProductRequest;
import com.petcare.admin.dto.AdminManagementDtos.ProductView;
import com.petcare.admin.dto.AdminManagementDtos.ServiceItemRequest;
import com.petcare.admin.dto.AdminManagementDtos.ServiceItemView;
import com.petcare.admin.dto.AdminManagementDtos.StaffRequest;
import com.petcare.admin.dto.AdminManagementDtos.StaffScheduleRequest;
import com.petcare.admin.dto.AdminManagementDtos.StaffScheduleView;
import com.petcare.admin.dto.AdminManagementDtos.StaffSkillView;
import com.petcare.admin.dto.AdminManagementDtos.StaffView;
import com.petcare.admin.dto.AdminManagementDtos.StoreConfigUpdateRequest;
import com.petcare.admin.dto.AdminManagementDtos.StoreConfigView;
import com.petcare.admin.dto.AdminManagementDtos.StoreUpdateRequest;
import com.petcare.admin.dto.AdminManagementDtos.StoreView;
import com.petcare.admin.dto.AdminManagementDtos.UserView;
import com.petcare.admin.entity.AdminOperationLog;
import com.petcare.admin.service.AdminManagementService;
import com.petcare.admin.service.AdminOperationLogService;
import com.petcare.booking.dto.BookingResponse;
import com.petcare.booking.entity.StaffSchedule;
import com.petcare.booking.service.BookingApplicationService;
import com.petcare.booking.service.StaffScheduleService;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.pagination.PageResponse;
import com.petcare.product.dto.ProductOrderResponse;
import com.petcare.product.entity.Product;
import com.petcare.product.entity.ProductCarouselImage;
import com.petcare.product.entity.ProductCategory;
import com.petcare.product.entity.ProductDetailImage;
import com.petcare.product.entity.ProductImage;
import com.petcare.product.service.ProductCategoryService;
import com.petcare.product.service.ProductCarouselImageService;
import com.petcare.product.service.ProductDetailImageService;
import com.petcare.product.service.ProductImageService;
import com.petcare.product.service.ProductOrderApplicationService;
import com.petcare.product.service.ProductService;
import com.petcare.service.entity.ServiceCategory;
import com.petcare.service.entity.ServiceItem;
import com.petcare.service.entity.ServiceItemImage;
import com.petcare.service.service.ServiceCategoryService;
import com.petcare.service.service.ServiceItemImageService;
import com.petcare.service.service.ServiceItemService;
import com.petcare.staff.entity.Staff;
import com.petcare.staff.entity.StaffSkill;
import com.petcare.staff.service.StaffService;
import com.petcare.staff.service.StaffSkillService;
import com.petcare.store.entity.Store;
import com.petcare.store.entity.StoreConfig;
import com.petcare.store.service.StoreConfigService;
import com.petcare.store.service.StoreService;
import com.petcare.user.entity.User;
import com.petcare.user.entity.PhoneBlacklist;
import com.petcare.user.service.PhoneBlacklistService;
import com.petcare.user.service.UserService;
import com.petcare.admin.dto.AdminManagementDtos.UserBanResult;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminManagementServiceImpl implements AdminManagementService {

    private static final Logger log = LoggerFactory.getLogger(AdminManagementServiceImpl.class);

    private final StoreService storeService;
    private final StoreConfigService storeConfigService;
    private final ServiceCategoryService serviceCategoryService;
    private final ServiceItemService serviceItemService;
    private final StaffService staffService;
    private final StaffSkillService staffSkillService;
    private final StaffScheduleService scheduleService;
    private final ProductCategoryService productCategoryService;
    private final ProductService productService;
    private final ProductImageService productImageService;
    private final ProductDetailImageService productDetailImageService;
    private final ProductCarouselImageService productCarouselImageService;
    private final ServiceItemImageService serviceItemImageService;
    private final AdminOperationLogService operationLogService;
    private final UserService userService;
    private final PhoneBlacklistService phoneBlacklistService;
    private final BookingApplicationService bookingApplicationService;
    private final ProductOrderApplicationService productOrderApplicationService;

    public AdminManagementServiceImpl(StoreService storeService, StoreConfigService storeConfigService,
            ServiceCategoryService serviceCategoryService, ServiceItemService serviceItemService,
            StaffService staffService, StaffSkillService staffSkillService,
            StaffScheduleService scheduleService, ProductCategoryService productCategoryService,
            ProductService productService, ProductImageService productImageService,
            ProductDetailImageService productDetailImageService,
            ProductCarouselImageService productCarouselImageService,
            ServiceItemImageService serviceItemImageService,
            AdminOperationLogService operationLogService,
            UserService userService, PhoneBlacklistService phoneBlacklistService,
            BookingApplicationService bookingApplicationService,
            ProductOrderApplicationService productOrderApplicationService) {
        this.storeService = storeService;
        this.storeConfigService = storeConfigService;
        this.serviceCategoryService = serviceCategoryService;
        this.serviceItemService = serviceItemService;
        this.staffService = staffService;
        this.staffSkillService = staffSkillService;
        this.scheduleService = scheduleService;
        this.productCategoryService = productCategoryService;
        this.productService = productService;
        this.productImageService = productImageService;
        this.productDetailImageService = productDetailImageService;
        this.productCarouselImageService = productCarouselImageService;
        this.serviceItemImageService = serviceItemImageService;
        this.operationLogService = operationLogService;
        this.userService = userService;
        this.phoneBlacklistService = phoneBlacklistService;
        this.bookingApplicationService = bookingApplicationService;
        this.productOrderApplicationService = productOrderApplicationService;
    }

    @Override
    public StoreView getStore(Long id) {
        return storeView(requireStore(id));
    }

    @Override
    @Transactional
    public StoreView updateStore(Long id, StoreUpdateRequest request, Long operatorId) {
        String url = "/api/v1/admin/stores/" + id;
        Store store = null;
        try {
            store = requireStore(id);
            store.setStoreName(request.storeName());
            store.setPhone(request.phone());
            store.setAddress(request.address());
            store.setLongitude(request.longitude());
            store.setLatitude(request.latitude());
            store.setBusinessHours(request.businessHours());
            store.setStatus(request.status());
            store.setDescription(request.description());
            storeService.updateById(store);
            String params = "targetName=" + store.getStoreName() + ", storeId=" + store.getId();
            audit(operatorId, "store", "update-info", "PATCH", url, "SUCCESS", params, null);
            return storeView(store);
        } catch (RuntimeException e) {
            String params = store != null ? "targetName=" + store.getStoreName() + ", storeId=" + store.getId() : null;
            audit(operatorId, "store", "update-info", "PATCH", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    public StoreConfigView getStoreConfig(Long storeId) {
        return storeConfigView(requireStoreConfig(storeId));
    }

    @Override
    @Transactional
    public StoreConfigView updateStoreConfig(Long storeId, StoreConfigUpdateRequest request, Long operatorId) {
        String url = "/api/v1/admin/stores/" + storeId + "/config";
        StoreConfig config = null;
        try {
            requireStore(storeId);
            config = requireStoreConfig(storeId);
            config.setHomeServiceRadiusKm(request.homeServiceRadiusKm());
            config.setBookingAdvanceDays(request.bookingAdvanceDays());
            config.setBookingCancelHours(request.bookingCancelHours());
            config.setTimeSlotMinutes(request.timeSlotMinutes());
            config.setAutoConfirmBooking(flag(request.autoConfirmBooking()));
            config.setContentAutoPublish(flag(request.contentAutoPublish()));
            storeConfigService.updateById(config);
            String params = "storeId=" + storeId;
            audit(operatorId, "store", "update-config", "PUT", url, "SUCCESS", params, null);
            return storeConfigView(config);
        } catch (RuntimeException e) {
            String params = "storeId=" + storeId;
            audit(operatorId, "store", "update-config", "PUT", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    public PageResponse<ServiceItemView> listServiceItems(int page, int size, String status) {
        LambdaQueryWrapper<ServiceItem> query = new LambdaQueryWrapper<ServiceItem>()
                .eq(ServiceItem::getDeleted, 0)
                .eq(hasText(status), ServiceItem::getStatus, status)
                .orderByAsc(ServiceItem::getSort).orderByDesc(ServiceItem::getCreateTime);
        IPage<ServiceItem> result = serviceItemService.page(page(page, size), query);
        return response(result, page, size, this::serviceItemView);
    }

    @Override
    @Transactional
    public ServiceItemView createServiceItem(ServiceItemRequest request, Long operatorId) {
        String url = "/api/v1/admin/service-items";
        ServiceItem item = null;
        try {
            requireServiceCategory(request.categoryId());
            item = new ServiceItem();
            apply(item, request);
            item.setStatus("ON_SALE");
            serviceItemService.save(item);
            replaceServiceItemImages(item.getId(), request.imageUrls());
            String params = "targetName=" + item.getName() + ", serviceItemId=" + item.getId();
            audit(operatorId, "service", "create-item", "POST", url, "SUCCESS", params, null);
            return serviceItemView(item);
        } catch (RuntimeException e) {
            String params = item != null ? "targetName=" + item.getName() + ", serviceItemId=" + item.getId() : null;
            audit(operatorId, "service", "create-item", "POST", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    @Transactional
    public ServiceItemView updateServiceItem(Long id, ServiceItemRequest request, Long operatorId) {
        String url = "/api/v1/admin/service-items/" + id;
        ServiceItem item = null;
        try {
            requireServiceCategory(request.categoryId());
            item = requireServiceItem(id);
            apply(item, request);
            serviceItemService.updateById(item);
            replaceServiceItemImages(item.getId(), request.imageUrls());
            String params = "targetName=" + item.getName() + ", serviceItemId=" + item.getId();
            audit(operatorId, "service", "update-item", "PUT", url, "SUCCESS", params, null);
            return serviceItemView(item);
        } catch (RuntimeException e) {
            String params = item != null ? "targetName=" + item.getName() + ", serviceItemId=" + item.getId() : null;
            audit(operatorId, "service", "update-item", "PUT", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    @Transactional
    public ServiceItemView disableServiceItem(Long id, Long operatorId) {
        String url = "/api/v1/admin/service-items/" + id + "/disable";
        ServiceItem item = null;
        try {
            item = requireServiceItem(id);
            item.setStatus("OFF_SALE");
            serviceItemService.updateById(item);
            String params = "targetName=" + item.getName() + ", serviceItemId=" + item.getId();
            audit(operatorId, "service", "disable-item", "POST", url, "SUCCESS", params, null);
            return serviceItemView(item);
        } catch (RuntimeException e) {
            String params = item != null ? "targetName=" + item.getName() + ", serviceItemId=" + item.getId() : null;
            audit(operatorId, "service", "disable-item", "POST", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    @Transactional
    public ServiceItemView enableServiceItem(Long id, Long operatorId) {
        String url = "/api/v1/admin/service-items/" + id + "/enable";
        ServiceItem item = null;
        try {
            item = requireServiceItem(id);
            if (!"OFF_SALE".equals(item.getStatus())) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT, "该服务项目未处于停用状态");
            }
            item.setStatus("ON_SALE");
            serviceItemService.updateById(item);
            String params = "targetName=" + item.getName() + ", serviceItemId=" + item.getId();
            audit(operatorId, "service", "enable-item", "POST", url, "SUCCESS", params, null);
            return serviceItemView(item);
        } catch (RuntimeException e) {
            String params = item != null ? "targetName=" + item.getName() + ", serviceItemId=" + item.getId() : null;
            audit(operatorId, "service", "enable-item", "POST", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    @Transactional
    public void deleteServiceItem(Long id, Long operatorId) {
        String url = "/api/v1/admin/service-items/" + id;
        ServiceItem item = null;
        try {
            item = requireServiceItem(id);
            if (!"OFF_SALE".equals(item.getStatus())) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT, "该服务项目未处于停用状态，请先停用再删除");
            }
            serviceItemService.removeById(item.getId());
            String params = "targetName=" + item.getName() + ", serviceItemId=" + item.getId();
            audit(operatorId, "service", "delete-item", "DELETE", url, "SUCCESS", params, null);
        } catch (RuntimeException e) {
            String params = item != null ? "targetName=" + item.getName() + ", serviceItemId=" + item.getId() : null;
            audit(operatorId, "service", "delete-item", "DELETE", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    public PageResponse<StaffView> listStaff(int page, int size, String status) {
        LambdaQueryWrapper<Staff> query = new LambdaQueryWrapper<Staff>()
                .eq(Staff::getDeleted, 0)
                .eq(hasText(status), Staff::getStatus, status)
                .orderByDesc(Staff::getCreateTime);
        IPage<Staff> result = staffService.page(page(page, size), query);
        return response(result, page, size, this::staffView);
    }

    @Override
    @Transactional
    public StaffView createStaff(StaffRequest request, Long operatorId) {
        String url = "/api/v1/admin/staff";
        Staff staff = null;
        try {
            requireStore(request.storeId());
            staff = new Staff();
            apply(staff, request);
            staff.setStatus("ACTIVE");
            staffService.save(staff);
            // 创建时一并分配技能（若有），避免新员工因 0 技能而不出现在预约可选列表
            if (request.skillCategoryIds() != null && !request.skillCategoryIds().isEmpty()) {
                replaceStaffSkills(staff.getId(), request.skillCategoryIds(), operatorId);
            }
            String params = "targetName=" + staff.getName() + ", staffId=" + staff.getId();
            audit(operatorId, "staff", "create-profile", "POST", url, "SUCCESS", params, null);
            return staffView(staff);
        } catch (RuntimeException e) {
            String params = staff != null ? "targetName=" + staff.getName() + ", staffId=" + staff.getId() : null;
            audit(operatorId, "staff", "create-profile", "POST", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    @Transactional
    public StaffView updateStaff(Long id, StaffRequest request, Long operatorId) {
        String url = "/api/v1/admin/staff/" + id;
        Staff staff = null;
        try {
            requireStore(request.storeId());
            staff = requireStaff(id);
            apply(staff, request);
            staffService.updateById(staff);
            // 编辑时若提供了技能列表，一并更新
            if (request.skillCategoryIds() != null) {
                replaceStaffSkills(id, request.skillCategoryIds(), operatorId);
            }
            String params = "targetName=" + staff.getName() + ", staffId=" + staff.getId();
            audit(operatorId, "staff", "update-profile", "PUT", url, "SUCCESS", params, null);
            return staffView(staff);
        } catch (RuntimeException e) {
            String params = staff != null ? "targetName=" + staff.getName() + ", staffId=" + staff.getId() : null;
            audit(operatorId, "staff", "update-profile", "PUT", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    @Transactional
    public StaffView disableStaff(Long id, Long operatorId) {
        String url = "/api/v1/admin/staff/" + id + "/disable";
        Staff staff = null;
        try {
            staff = requireStaff(id);
            staff.setStatus("INACTIVE");
            staffService.updateById(staff);
            String params = "targetName=" + staff.getName() + ", staffId=" + staff.getId();
            audit(operatorId, "staff", "disable-profile", "POST", url, "SUCCESS", params, null);
            return staffView(staff);
        } catch (RuntimeException e) {
            String params = staff != null ? "targetName=" + staff.getName() + ", staffId=" + staff.getId() : null;
            audit(operatorId, "staff", "disable-profile", "POST", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    @Transactional
    public StaffView enableStaff(Long id, Long operatorId) {
        String url = "/api/v1/admin/staff/" + id + "/enable";
        Staff staff = null;
        try {
            staff = requireStaff(id);
            if (!"INACTIVE".equals(staff.getStatus())) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT, "该员工未处于停用状态");
            }
            staff.setStatus("ACTIVE");
            staffService.updateById(staff);
            String params = "targetName=" + staff.getName() + ", staffId=" + staff.getId();
            audit(operatorId, "staff", "enable-profile", "POST", url, "SUCCESS", params, null);
            return staffView(staff);
        } catch (RuntimeException e) {
            String params = staff != null ? "targetName=" + staff.getName() + ", staffId=" + staff.getId() : null;
            audit(operatorId, "staff", "enable-profile", "POST", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    @Transactional
    public StaffSkillView replaceStaffSkills(Long staffId, List<Long> categoryIds, Long operatorId) {
        String url = "/api/v1/admin/staff/" + staffId + "/skills";
        Staff staff = null;
        try {
            staff = requireStaff(staffId);
            List<Long> distinctIds = categoryIds.stream().distinct().toList();
            distinctIds.forEach(this::requireServiceCategory);
            staffSkillService.remove(new LambdaQueryWrapper<StaffSkill>().eq(StaffSkill::getStaffId, staffId));
            distinctIds.forEach(categoryId -> {
                StaffSkill skill = new StaffSkill();
                skill.setStaffId(staffId);
                skill.setServiceCategoryId(categoryId);
                staffSkillService.save(skill);
            });
            String params = "targetName=" + staff.getName() + ", staffId=" + staff.getId();
            audit(operatorId, "staff", "replace-skills", "PUT", url, "SUCCESS", params, null);
            return new StaffSkillView(staffId, distinctIds);
        } catch (RuntimeException e) {
            String params = staff != null ? "targetName=" + staff.getName() + ", staffId=" + staff.getId() : null;
            audit(operatorId, "staff", "replace-skills", "PUT", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    public StaffSkillView getStaffSkills(Long staffId) {
        requireStaff(staffId);
        List<Long> categoryIds = staffSkillService.list(
                new LambdaQueryWrapper<StaffSkill>().eq(StaffSkill::getStaffId, staffId)
        ).stream().map(StaffSkill::getServiceCategoryId).toList();
        return new StaffSkillView(staffId, categoryIds);
    }

    @Override
    public PageResponse<StaffScheduleView> listSchedules(Long staffId, int page, int size) {
        requireStaff(staffId);
        LambdaQueryWrapper<StaffSchedule> query = new LambdaQueryWrapper<StaffSchedule>()
                .eq(StaffSchedule::getStaffId, staffId)
                .eq(StaffSchedule::getDeleted, 0)
                .orderByDesc(StaffSchedule::getWorkDate).orderByAsc(StaffSchedule::getStartTime);
        IPage<StaffSchedule> result = scheduleService.page(page(page, size), query);
        return response(result, page, size, this::scheduleView);
    }

    @Override
    @Transactional
    public StaffScheduleView createSchedule(Long staffId, StaffScheduleRequest request, Long operatorId) {
        String url = "/api/v1/admin/staff/" + staffId + "/schedules";
        Staff staff = null;
        StaffSchedule schedule = null;
        try {
            staff = requireStaff(staffId);
            validateSchedule(staff, request, null);
            schedule = new StaffSchedule();
            schedule.setStaffId(staffId);
            apply(schedule, request);
            scheduleService.save(schedule);
            String params = "targetName=" + staff.getName() + " " + schedule.getWorkDate()
                    + ", staffId=" + staff.getId() + ", scheduleId=" + schedule.getId();
            audit(operatorId, "staff", "create-schedule", "POST", url, "SUCCESS", params, null);
            return scheduleView(schedule);
        } catch (RuntimeException e) {
            String params = (staff != null && schedule != null)
                    ? "targetName=" + staff.getName() + " " + schedule.getWorkDate()
                    + ", staffId=" + staff.getId() + ", scheduleId=" + schedule.getId()
                    : null;
            audit(operatorId, "staff", "create-schedule", "POST", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    @Transactional
    public StaffScheduleView updateSchedule(Long staffId, Long scheduleId,
            StaffScheduleRequest request, Long operatorId) {
        String url = "/api/v1/admin/staff/" + staffId + "/schedules/" + scheduleId;
        Staff staff = null;
        StaffSchedule schedule = null;
        try {
            staff = requireStaff(staffId);
            schedule = requireSchedule(staffId, scheduleId);
            validateSchedule(staff, request, scheduleId);
            apply(schedule, request);
            scheduleService.updateById(schedule);
            String params = "targetName=" + staff.getName() + " " + schedule.getWorkDate()
                    + ", staffId=" + staff.getId() + ", scheduleId=" + schedule.getId();
            audit(operatorId, "staff", "update-schedule", "PUT", url, "SUCCESS", params, null);
            return scheduleView(schedule);
        } catch (RuntimeException e) {
            String params = (staff != null && schedule != null)
                    ? "targetName=" + staff.getName() + " " + schedule.getWorkDate()
                    + ", staffId=" + staff.getId() + ", scheduleId=" + schedule.getId()
                    : null;
            audit(operatorId, "staff", "update-schedule", "PUT", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    public PageResponse<ProductView> listProducts(int page, int size, String status) {
        LambdaQueryWrapper<Product> query = new LambdaQueryWrapper<Product>()
                .eq(Product::getDeleted, 0)
                .eq(hasText(status), Product::getStatus, status)
                .orderByAsc(Product::getSort).orderByDesc(Product::getCreateTime);
        IPage<Product> result = productService.page(page(page, size), query);
        return response(result, page, size, this::productView);
    }

    @Override
    @Transactional
    public ProductView createProduct(ProductRequest request, Long operatorId) {
        String url = "/api/v1/admin/products";
        Product product = null;
        try {
            requireProductCategory(request.categoryId());
            product = new Product();
            apply(product, request);
            product.setStock(0);
            product.setSalesCount(0);
            product.setStatus("ON_SALE");
            productService.save(product);
            replaceProductImages(product.getId(), request.imageUrls());
            replaceProductDetailImages(product.getId(), request.detailImageUrls());
            String params = "targetName=" + product.getName() + ", productId=" + product.getId();
            audit(operatorId, "product", "create-item", "POST", url, "SUCCESS", params, null);
            return productView(product);
        } catch (RuntimeException e) {
            String params = product != null ? "targetName=" + product.getName() + ", productId=" + product.getId() : null;
            audit(operatorId, "product", "create-item", "POST", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    @Transactional
    public ProductView updateProduct(Long id, ProductRequest request, Long operatorId) {
        String url = "/api/v1/admin/products/" + id;
        Product product = null;
        try {
            requireProductCategory(request.categoryId());
            product = requireProduct(id);
            apply(product, request);
            productService.updateById(product);
            replaceProductImages(product.getId(), request.imageUrls());
            replaceProductDetailImages(product.getId(), request.detailImageUrls());
            String params = "targetName=" + product.getName() + ", productId=" + product.getId();
            audit(operatorId, "product", "update-item", "PUT", url, "SUCCESS", params, null);
            return productView(product);
        } catch (RuntimeException e) {
            String params = product != null ? "targetName=" + product.getName() + ", productId=" + product.getId() : null;
            audit(operatorId, "product", "update-item", "PUT", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    @Transactional
    public ProductView disableProduct(Long id, Long operatorId) {
        String url = "/api/v1/admin/products/" + id + "/disable";
        Product product = null;
        try {
            product = requireProduct(id);
            product.setStatus("OFF_SALE");
            productService.updateById(product);
            String params = "targetName=" + product.getName() + ", productId=" + product.getId();
            audit(operatorId, "product", "disable-item", "POST", url, "SUCCESS", params, null);
            return productView(product);
        } catch (RuntimeException e) {
            String params = product != null ? "targetName=" + product.getName() + ", productId=" + product.getId() : null;
            audit(operatorId, "product", "disable-item", "POST", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    @Transactional
    public ProductView enableProduct(Long id, Long operatorId) {
        String url = "/api/v1/admin/products/" + id + "/enable";
        Product product = null;
        try {
            product = requireProduct(id);
            if (!"OFF_SALE".equals(product.getStatus())) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT, "该商品未处于下架状态");
            }
            product.setStatus("ON_SALE");
            productService.updateById(product);
            String params = "targetName=" + product.getName() + ", productId=" + product.getId();
            audit(operatorId, "product", "enable-item", "POST", url, "SUCCESS", params, null);
            return productView(product);
        } catch (RuntimeException e) {
            String params = product != null ? "targetName=" + product.getName() + ", productId=" + product.getId() : null;
            audit(operatorId, "product", "enable-item", "POST", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    @Transactional
    public void deleteProduct(Long id, Long operatorId) {
        String url = "/api/v1/admin/products/" + id;
        Product product = null;
        try {
            product = requireProduct(id);
            if (!"OFF_SALE".equals(product.getStatus())) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT, "该商品未处于下架状态，请先下架再删除");
            }
            productService.removeById(product.getId());
            String params = "targetName=" + product.getName() + ", productId=" + product.getId();
            audit(operatorId, "product", "delete-item", "DELETE", url, "SUCCESS", params, null);
        } catch (RuntimeException e) {
            String params = product != null ? "targetName=" + product.getName() + ", productId=" + product.getId() : null;
            audit(operatorId, "product", "delete-item", "DELETE", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    @Transactional
    public ProductView updateProductStock(Long id, Integer stock, Long operatorId) {
        String url = "/api/v1/admin/products/" + id + "/stock";
        Product product = null;
        try {
            product = requireProductForUpdate(id);
            product.setStock(stock);
            productService.updateById(product);
            String params = "targetName=" + product.getName() + ", productId=" + product.getId();
            audit(operatorId, "product", "update-stock", "PUT", url, "SUCCESS", params, null);
            return productView(product);
        } catch (RuntimeException e) {
            String params = product != null ? "targetName=" + product.getName() + ", productId=" + product.getId() : null;
            audit(operatorId, "product", "update-stock", "PUT", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    public List<ProductCarouselImageView> listProductCarouselImages() {
        LambdaQueryWrapper<ProductCarouselImage> query = new LambdaQueryWrapper<ProductCarouselImage>()
                .eq(ProductCarouselImage::getDeleted, 0)
                .orderByAsc(ProductCarouselImage::getSort)
                .orderByAsc(ProductCarouselImage::getCreateTime);
        return productCarouselImageService.list(query).stream()
                .map(this::productCarouselImageView)
                .toList();
    }

    @Override
    @Transactional
    public List<ProductCarouselImageView> replaceProductCarouselImages(
            ProductCarouselImagesUpdateRequest request, Long operatorId) {
        String url = "/api/v1/admin/product-carousel-images";
        try {
            productCarouselImageService.remove(new LambdaQueryWrapper<ProductCarouselImage>()
                    .eq(ProductCarouselImage::getDeleted, 0));
            int sort = 0;
            for (ProductCarouselImageRequest imageRequest : request.images()) {
                validateCarouselLink(imageRequest);
                ProductCarouselImage image = new ProductCarouselImage();
                apply(image, imageRequest, sort);
                productCarouselImageService.save(image);
                sort++;
            }
            String params = "scope=carousel-images, count=" + request.images().size();
            audit(operatorId, "product", "replace-carousel-images", "PUT", url, "SUCCESS", params, null);
            return listProductCarouselImages();
        } catch (RuntimeException e) {
            String params = "scope=carousel-images, count=" + request.images().size();
            audit(operatorId, "product", "replace-carousel-images", "PUT", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    public PageResponse<OperationLogView> listOperationLogs(int page, int size, String module) {
        LambdaQueryWrapper<AdminOperationLog> query = new LambdaQueryWrapper<AdminOperationLog>()
                .eq(hasText(module), AdminOperationLog::getModule, module)
                .orderByDesc(AdminOperationLog::getCreateTime);
        IPage<AdminOperationLog> result = operationLogService.page(page(page, size), query);
        return response(result, page, size, this::operationLogView);
    }

    // ─── User management ───

    @Override
    public PageResponse<UserView> listUsers(int page, int size, String status, String keyword) {
        LambdaQueryWrapper<User> query = new LambdaQueryWrapper<User>()
                .eq(hasText(status), User::getStatus, status)
                .and(hasText(keyword), w -> w
                        .like(User::getPhone, keyword)
                        .or().like(User::getNickname, keyword))
                .orderByDesc(User::getCreateTime);
        IPage<User> result = userService.page(page(page, size), query);
        return response(result, page, size, this::userView);
    }

    @Override
    public UserView getUser(Long id) {
        return userView(requireUser(id));
    }

    @Override
    @Transactional
    public UserBanResult banUser(Long id, String reason, Long operatorId) {
        String url = "/api/v1/admin/users/" + id + "/ban";
        User user = null;
        try {
            user = requireUser(id);
            // Progressive ban: compute next level + duration from ban history
            PhoneBlacklist ban = null;
            if (hasText(user.getPhone())) {
                ban = phoneBlacklistService.banPhone(user.getPhone(), user.getId(), reason, operatorId);
            }
            user.setStatus("BANNED");
            userService.updateById(user);
            String params = "targetName=" + user.getNickname() + ", userId=" + user.getId() + ", phone=" + user.getPhone();
            audit(operatorId, "user", "ban-user", "POST", url, "SUCCESS", params, null);
            String description = (ban != null) ? phoneBlacklistService.describeRemaining(ban) : "已封禁";
            return new UserBanResult(
                    user.getId(),
                    user.getStatus(),
                    ban != null ? ban.getBanLevel() : 1,
                    ban != null ? ban.getBanDays() : null,
                    ban != null ? ban.getBanUntil() : null,
                    description);
        } catch (RuntimeException e) {
            String params = user != null
                    ? "targetName=" + user.getNickname() + ", userId=" + user.getId() + ", phone=" + user.getPhone()
                    : null;
            audit(operatorId, "user", "ban-user", "POST", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    @Transactional
    public UserView unbanUser(Long id, Long operatorId) {
        String url = "/api/v1/admin/users/" + id + "/unban";
        User user = null;
        try {
            user = requireUser(id);
            if (!"BANNED".equals(user.getStatus())) {
                throw new BusinessException(ErrorCode.STATE_CONFLICT, "该用户未被封禁");
            }
            user.setStatus("ACTIVE");
            userService.updateById(user);
            // Mark the active ban record as UNBANNED (history preserved for level escalation)
            if (hasText(user.getPhone())) {
                phoneBlacklistService.unbanPhone(user.getPhone());
            }
            String params = "targetName=" + user.getNickname() + ", userId=" + user.getId() + ", phone=" + user.getPhone();
            audit(operatorId, "user", "unban-user", "POST", url, "SUCCESS", params, null);
            return userView(user);
        } catch (RuntimeException e) {
            String params = user != null
                    ? "targetName=" + user.getNickname() + ", userId=" + user.getId() + ", phone=" + user.getPhone()
                    : null;
            audit(operatorId, "user", "unban-user", "POST", url, "FAIL", params, auditFailureMessage(e));
            throw e;
        }
    }

    @Override
    public PageResponse<BookingResponse> listUserBookings(Long userId, int page, int size) {
        requireUser(userId);
        return bookingApplicationService.getMyBookings(userId, page, size);
    }

    @Override
    public PageResponse<ProductOrderResponse> listUserOrders(Long userId, int page, int size) {
        requireUser(userId);
        return productOrderApplicationService.getMyOrders(userId, page, size);
    }

    private User requireUser(Long id) {
        User user = userService.getById(id);
        if (user == null || Integer.valueOf(1).equals(user.getDeleted())) {
            throw notFound("用户不存在");
        }
        return user;
    }

    private UserView userView(User user) {
        // Describe active ban (remaining time) for display in the user list
        String banDescription = null;
        if ("BANNED".equals(user.getStatus()) && hasText(user.getPhone())) {
            PhoneBlacklist active = phoneBlacklistService.getActiveBan(user.getPhone());
            banDescription = phoneBlacklistService.describeRemaining(active);
        }
        return new UserView(
                user.getId(),
                user.getNickname(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getGender(),
                user.getStatus(),
                user.getRealName(),
                banDescription,
                user.getLastLoginTime(),
                user.getCreateTime());
    }

    private void audit(Long operatorId, String module, String operation,
            String method, String url, String result, String errorMessage) {
        audit(operatorId, module, operation, method, url, result, null, errorMessage);
    }

    /**
     * Writes an audit log entry. {@code params} carries the operation target's human-readable
     * details (e.g. "targetName=张三, targetId=3001, phone=139****1234") so admins can answer
     * "who/what was acted on" without cross-referencing IDs. Null/blank params falls back to
     * legacy behavior (URL-only).
     */
    private void audit(Long operatorId, String module, String operation,
            String method, String url, String result, String params, String errorMessage) {
        AdminOperationLog entry = new AdminOperationLog();
        entry.setAdminId(operatorId);
        entry.setModule(module);
        entry.setOperation(operation);
        entry.setRequestMethod(method);
        entry.setRequestUrl(url);
        entry.setResult(result);
        entry.setRequestParams(params);
        entry.setErrorMessage(errorMessage);
        entry.setCreateTime(LocalDateTime.now());
        if ("FAIL".equals(result)) {
            try {
                operationLogService.saveFailLog(entry);
            } catch (RuntimeException auditException) {
                log.warn("Failed to write FAIL admin operation log: operatorId={}, operation={}",
                        operatorId, operation);
            }
            return;
        }
        if (!operationLogService.save(entry)) {
            throw new IllegalStateException("Failed to persist required admin operation log");
        }
    }

    private String auditFailureMessage(RuntimeException exception) {
        if (exception instanceof BusinessException && exception.getMessage() != null) {
            return exception.getMessage().substring(0, Math.min(exception.getMessage().length(), 1000));
        }
        return "unexpected_error";
    }

    private Store requireStore(Long id) {
        Store store = storeService.getById(id);
        if (store == null || Integer.valueOf(1).equals(store.getDeleted())) {
            throw notFound("门店不存在");
        }
        return store;
    }

    private StoreConfig requireStoreConfig(Long storeId) {
        StoreConfig config = storeConfigService.getOne(
                new LambdaQueryWrapper<StoreConfig>().eq(StoreConfig::getStoreId, storeId), false);
        if (config == null) {
            throw notFound("门店配置不存在");
        }
        return config;
    }

    private ServiceItem requireServiceItem(Long id) {
        ServiceItem item = serviceItemService.getById(id);
        if (item == null || Integer.valueOf(1).equals(item.getDeleted())) {
            throw notFound("服务项目不存在");
        }
        return item;
    }

    private ServiceCategory requireServiceCategory(Long id) {
        ServiceCategory category = serviceCategoryService.getById(id);
        if (category == null || Integer.valueOf(1).equals(category.getDeleted())
                || !"ACTIVE".equals(category.getStatus())) {
            throw notFound("服务分类不存在或已禁用");
        }
        return category;
    }

    private Staff requireStaff(Long id) {
        Staff staff = staffService.getById(id);
        if (staff == null || Integer.valueOf(1).equals(staff.getDeleted())) {
            throw notFound("员工不存在");
        }
        return staff;
    }

    private StaffSchedule requireSchedule(Long staffId, Long scheduleId) {
        StaffSchedule schedule = scheduleService.getById(scheduleId);
        if (schedule == null || !staffId.equals(schedule.getStaffId())
                || Integer.valueOf(1).equals(schedule.getDeleted())) {
            throw notFound("排班不存在");
        }
        return schedule;
    }

    private Product requireProduct(Long id) {
        Product product = productService.getById(id);
        if (product == null || Integer.valueOf(1).equals(product.getDeleted())) {
            throw notFound("商品不存在");
        }
        return product;
    }

    private Product requireProductForUpdate(Long id) {
        Product product = productService.getOne(new LambdaQueryWrapper<Product>()
                .eq(Product::getId, id)
                .eq(Product::getDeleted, 0)
                .last("FOR UPDATE"), false);
        if (product == null) {
            throw notFound("商品不存在");
        }
        return product;
    }

    private ProductCategory requireProductCategory(Long id) {
        ProductCategory category = productCategoryService.getById(id);
        if (category == null || Integer.valueOf(1).equals(category.getDeleted())
                || !"ACTIVE".equals(category.getStatus())) {
            throw notFound("商品分类不存在或已禁用");
        }
        return category;
    }

    private void validateSchedule(Staff staff, StaffScheduleRequest request, Long excludedScheduleId) {
        if (!staff.getStoreId().equals(request.storeId())) {
            throw new BusinessException(ErrorCode.BUSINESS_RULE_VIOLATION, "排班门店必须与员工所属门店一致");
        }
        if (!request.startTime().isBefore(request.endTime())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "排班开始时间必须早于结束时间");
        }
        LambdaQueryWrapper<StaffSchedule> conflictQuery = new LambdaQueryWrapper<StaffSchedule>()
                .eq(StaffSchedule::getStaffId, staff.getId())
                .eq(StaffSchedule::getWorkDate, request.workDate())
                .eq(StaffSchedule::getDeleted, 0)
                .lt(StaffSchedule::getStartTime, request.endTime())
                .gt(StaffSchedule::getEndTime, request.startTime())
                .ne(excludedScheduleId != null, StaffSchedule::getId, excludedScheduleId);
        if (scheduleService.count(conflictQuery) > 0) {
            throw new BusinessException(ErrorCode.STATE_CONFLICT, "该员工在所选时间段已有排班");
        }
    }

    private BusinessException notFound(String message) {
        return new BusinessException(ErrorCode.RESOURCE_NOT_FOUND, message);
    }

    private <T> Page<T> page(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return new Page<>(safePage, safeSize);
    }

    private <E, V> PageResponse<V> response(IPage<E> source, int page, int size,
            java.util.function.Function<E, V> mapper) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageResponse.of(source.getRecords().stream().map(mapper).toList(),
                source.getTotal(), safePage, safeSize);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int flag(Boolean value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }

    private void apply(ServiceItem item, ServiceItemRequest request) {
        item.setCategoryId(request.categoryId());
        item.setName(request.name());
        item.setServiceMode(request.serviceMode());
        item.setPrice(request.price());
        item.setDurationMinutes(request.durationMinutes());
        item.setPetType(request.petType());
        item.setPetSize(request.petSize());
        item.setNeedAddress(flag(request.needAddress()));
        item.setNeedPet(flag(request.needPet()));
        item.setDescription(request.description());
        item.setCoverUrl(request.coverUrl());
        item.setSort(request.sort() == null ? 0 : request.sort());
    }

    private void apply(Staff staff, StaffRequest request) {
        staff.setStoreId(request.storeId());
        staff.setName(request.name());
        staff.setPhone(request.phone());
        staff.setAvatarUrl(request.avatarUrl());
        staff.setRole(request.role());
        staff.setDescription(request.description());
    }

    private void apply(StaffSchedule schedule, StaffScheduleRequest request) {
        schedule.setStoreId(request.storeId());
        schedule.setWorkDate(request.workDate());
        schedule.setStartTime(request.startTime());
        schedule.setEndTime(request.endTime());
        schedule.setStatus(request.status());
        schedule.setRemark(request.remark());
    }

    private void apply(Product product, ProductRequest request) {
        product.setCategoryId(request.categoryId());
        product.setName(request.name());
        product.setCoverUrl(request.coverUrl());
        product.setPrice(request.price());
        product.setDescription(request.description());
        product.setPickupOnly(flag(request.pickupOnly()));
        product.setSort(request.sort() == null ? 0 : request.sort());
    }

    private void apply(ProductCarouselImage image, ProductCarouselImageRequest request, int fallbackSort) {
        String linkType = hasText(request.linkType()) ? request.linkType() : "NONE";
        image.setTitle(request.title());
        image.setImageUrl(request.imageUrl().trim());
        image.setLinkType(linkType);
        image.setLinkTargetId("PRODUCT".equals(linkType) ? request.linkTargetId() : null);
        image.setStatus(hasText(request.status()) ? request.status() : "ACTIVE");
        image.setSort(request.sort() == null ? fallbackSort : request.sort());
    }

    private void replaceServiceItemImages(Long serviceItemId, List<String> imageUrls) {
        serviceItemImageService.remove(new LambdaQueryWrapper<ServiceItemImage>()
                .eq(ServiceItemImage::getServiceItemId, serviceItemId));
        List<String> urls = normalizeImageUrls(imageUrls);
        for (int i = 0; i < urls.size(); i++) {
            ServiceItemImage image = new ServiceItemImage();
            image.setServiceItemId(serviceItemId);
            image.setImageUrl(urls.get(i));
            image.setSort(i + 1);
            serviceItemImageService.save(image);
        }
    }

    private void replaceProductImages(Long productId, List<String> imageUrls) {
        productImageService.remove(new LambdaQueryWrapper<ProductImage>()
                .eq(ProductImage::getProductId, productId));
        List<String> urls = normalizeImageUrls(imageUrls);
        for (int i = 0; i < urls.size(); i++) {
            ProductImage image = new ProductImage();
            image.setProductId(productId);
            image.setImageUrl(urls.get(i));
            image.setSort(i + 1);
            productImageService.save(image);
        }
    }

    private void replaceProductDetailImages(Long productId, List<String> imageUrls) {
        productDetailImageService.remove(new LambdaQueryWrapper<ProductDetailImage>()
                .eq(ProductDetailImage::getProductId, productId));
        List<String> urls = normalizeImageUrls(imageUrls);
        for (int i = 0; i < urls.size(); i++) {
            ProductDetailImage image = new ProductDetailImage();
            image.setProductId(productId);
            image.setImageUrl(urls.get(i));
            image.setSort(i + 1);
            productDetailImageService.save(image);
        }
    }

    private List<String> normalizeImageUrls(List<String> imageUrls) {
        if (imageUrls == null) {
            return List.of();
        }
        return imageUrls.stream()
                .filter(this::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private List<String> serviceItemImageUrls(Long serviceItemId) {
        return serviceItemImageService.list(new LambdaQueryWrapper<ServiceItemImage>()
                        .eq(ServiceItemImage::getServiceItemId, serviceItemId)
                        .orderByAsc(ServiceItemImage::getSort))
                .stream()
                .map(ServiceItemImage::getImageUrl)
                .toList();
    }

    private List<String> productImageUrls(Long productId) {
        return productImageService.list(new LambdaQueryWrapper<ProductImage>()
                        .eq(ProductImage::getProductId, productId)
                        .orderByAsc(ProductImage::getSort))
                .stream()
                .map(ProductImage::getImageUrl)
                .toList();
    }

    private List<String> productDetailImageUrls(Long productId) {
        return productDetailImageService.list(new LambdaQueryWrapper<ProductDetailImage>()
                        .eq(ProductDetailImage::getProductId, productId)
                        .orderByAsc(ProductDetailImage::getSort))
                .stream()
                .map(ProductDetailImage::getImageUrl)
                .toList();
    }

    private void validateCarouselLink(ProductCarouselImageRequest request) {
        String linkType = hasText(request.linkType()) ? request.linkType() : "NONE";
        if (!"PRODUCT".equals(linkType)) {
            return;
        }
        if (request.linkTargetId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "商品轮播图跳转商品不能为空");
        }
        requireProduct(request.linkTargetId());
    }

    private StoreView storeView(Store store) {
        return new StoreView(store.getId(), store.getStoreName(), store.getPhone(), store.getAddress(),
                store.getLongitude(), store.getLatitude(), store.getBusinessHours(), store.getStatus(),
                store.getDescription());
    }

    private StoreConfigView storeConfigView(StoreConfig config) {
        return new StoreConfigView(config.getId(), config.getStoreId(), config.getHomeServiceRadiusKm(),
                config.getBookingAdvanceDays(), config.getBookingCancelHours(), config.getTimeSlotMinutes(),
                config.getAutoConfirmBooking() == 1, config.getContentAutoPublish() == 1);
    }

    private ServiceItemView serviceItemView(ServiceItem item) {
        return new ServiceItemView(item.getId(), item.getCategoryId(), item.getName(), item.getServiceMode(),
                item.getPrice(), item.getDurationMinutes(), item.getPetType(), item.getPetSize(),
                Integer.valueOf(1).equals(item.getNeedAddress()), Integer.valueOf(1).equals(item.getNeedPet()),
                item.getDescription(), item.getCoverUrl(), serviceItemImageUrls(item.getId()),
                item.getStatus(), item.getSort());
    }

    private StaffView staffView(Staff staff) {
        return new StaffView(staff.getId(), staff.getStoreId(), staff.getName(), staff.getPhone(),
                staff.getAvatarUrl(), staff.getRole(), staff.getStatus(), staff.getDescription());
    }

    private StaffScheduleView scheduleView(StaffSchedule schedule) {
        return new StaffScheduleView(schedule.getId(), schedule.getStaffId(), schedule.getStoreId(),
                schedule.getWorkDate(), schedule.getStartTime(), schedule.getEndTime(),
                schedule.getStatus(), schedule.getRemark());
    }

    private ProductView productView(Product product) {
        return new ProductView(product.getId(), product.getCategoryId(), product.getName(), product.getCoverUrl(),
                product.getPrice(), product.getStock(), product.getSalesCount(), product.getDescription(),
                Integer.valueOf(1).equals(product.getPickupOnly()), productImageUrls(product.getId()),
                productDetailImageUrls(product.getId()), product.getStatus(), product.getSort());
    }

    private ProductCarouselImageView productCarouselImageView(ProductCarouselImage image) {
        return new ProductCarouselImageView(
                image.getId(),
                image.getTitle(),
                image.getImageUrl(),
                image.getLinkType(),
                image.getLinkTargetId(),
                image.getStatus(),
                image.getSort());
    }

    private OperationLogView operationLogView(AdminOperationLog entry) {
        return new OperationLogView(entry.getId(), entry.getAdminId(), entry.getModule(), entry.getOperation(),
                entry.getRequestMethod(), entry.getRequestUrl(), entry.getResult(),
                entry.getRequestParams(),
                entry.getErrorMessage(), entry.getCreateTime());
    }
}
