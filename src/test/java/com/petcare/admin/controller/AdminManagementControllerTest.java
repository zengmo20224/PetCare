package com.petcare.admin.controller;

import com.petcare.admin.entity.AdminPermission;
import com.petcare.admin.entity.AdminRole;
import com.petcare.admin.entity.AdminRolePermission;
import com.petcare.admin.entity.AdminUser;
import com.petcare.admin.service.AdminPermissionService;
import com.petcare.admin.service.AdminRolePermissionService;
import com.petcare.admin.service.AdminRoleService;
import com.petcare.admin.service.AdminUserService;
import com.petcare.common.security.JwtTokenService;
import com.petcare.product.entity.Product;
import com.petcare.product.entity.ProductCategory;
import com.petcare.product.service.ProductCategoryService;
import com.petcare.product.service.ProductService;
import com.petcare.service.entity.ServiceCategory;
import com.petcare.service.entity.ServiceItem;
import com.petcare.service.service.ServiceCategoryService;
import com.petcare.service.service.ServiceItemService;
import com.petcare.staff.entity.Staff;
import com.petcare.staff.service.StaffService;
import com.petcare.store.entity.Store;
import com.petcare.store.entity.StoreConfig;
import com.petcare.store.service.StoreConfigService;
import com.petcare.store.service.StoreService;
import java.math.BigDecimal;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminManagementControllerTest {

    private static final String[] PERMISSIONS = {
            "store:info:read", "store:info:update", "store:config:read", "store:config:update",
            "service:item:read", "service:item:create", "service:item:update", "service:item:disable",
            "staff:profile:read", "staff:profile:create", "staff:profile:update", "staff:profile:disable",
            "staff:profile:enable",
            "staff:skill:manage", "staff:schedule:read", "staff:schedule:manage",
            "product:item:read", "product:item:create", "product:item:update", "product:item:disable",
            "product:stock:update", "admin:operation-log:read"
    };

    @Autowired private MockMvc mockMvc;
    @Autowired private AdminUserService adminUserService;
    @Autowired private AdminRoleService adminRoleService;
    @Autowired private AdminPermissionService adminPermissionService;
    @Autowired private AdminRolePermissionService adminRolePermissionService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtTokenService jwtTokenService;
    @Autowired private StoreService storeService;
    @Autowired private StoreConfigService storeConfigService;
    @Autowired private ServiceCategoryService serviceCategoryService;
    @Autowired private ServiceItemService serviceItemService;
    @Autowired private StaffService staffService;
    @Autowired private ProductCategoryService productCategoryService;
    @Autowired private ProductService productService;

    private String token;
    private String noPermissionToken;
    private Long storeId;
    private Long serviceCategoryId;
    private Long secondServiceCategoryId;
    private Long serviceItemId;
    private Long staffId;
    private Long productId;
    private Long productCategoryId;

    @BeforeEach
    void setUp() {
        token = createAdmin("phase9_admin", "PHASE9_MANAGER", PERMISSIONS);
        noPermissionToken = createAdmin("phase9_viewer", "PHASE9_VIEWER", new String[]{"booking:booking:read"});

        Store store = new Store();
        store.setStoreName("原门店");
        store.setStatus("OPEN");
        storeService.save(store);
        storeId = store.getId();

        StoreConfig config = new StoreConfig();
        config.setStoreId(storeId);
        config.setHomeServiceRadiusKm(new BigDecimal("5.00"));
        config.setBookingAdvanceDays(14);
        config.setBookingCancelHours(4);
        config.setTimeSlotMinutes(30);
        config.setAutoConfirmBooking(0);
        config.setContentAutoPublish(1);
        storeConfigService.save(config);

        ServiceCategory serviceCategory = new ServiceCategory();
        serviceCategory.setName("洗护");
        serviceCategory.setStatus("ACTIVE");
        serviceCategory.setSort(0);
        serviceCategoryService.save(serviceCategory);
        serviceCategoryId = serviceCategory.getId();

        ServiceCategory secondServiceCategory = new ServiceCategory();
        secondServiceCategory.setName("喂养");
        secondServiceCategory.setStatus("ACTIVE");
        secondServiceCategory.setSort(1);
        serviceCategoryService.save(secondServiceCategory);
        secondServiceCategoryId = secondServiceCategory.getId();

        ServiceItem item = new ServiceItem();
        item.setCategoryId(serviceCategoryId);
        item.setName("基础洗护");
        item.setPrice(new BigDecimal("99.00"));
        item.setDurationMinutes(60);
        item.setServiceMode("STORE");
        item.setStatus("ON_SALE");
        item.setSort(0);
        serviceItemService.save(item);
        serviceItemId = item.getId();

        Staff staff = new Staff();
        staff.setStoreId(storeId);
        staff.setName("员工甲");
        staff.setRole("GROOMER");
        staff.setStatus("ACTIVE");
        staffService.save(staff);
        staffId = staff.getId();

        ProductCategory productCategory = new ProductCategory();
        productCategory.setName("食品");
        productCategory.setStatus("ACTIVE");
        productCategory.setSort(0);
        productCategoryService.save(productCategory);
        productCategoryId = productCategory.getId();

        Product product = new Product();
        product.setCategoryId(productCategoryId);
        product.setName("宠物零食");
        product.setPrice(new BigDecimal("20.00"));
        product.setStock(10);
        product.setSalesCount(0);
        product.setPickupOnly(1);
        product.setStatus("ON_SALE");
        product.setSort(0);
        productService.save(product);
        productId = product.getId();
    }

    @Test
    void managementEndpointsRequireAuthenticationAndPermission() throws Exception {
        mockMvc.perform(get("/api/v1/admin/stores/" + storeId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/admin/stores/" + storeId)
                        .header("Authorization", bearer(noPermissionToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void storeInfoAndConfigCanBeReadAndUpdated() throws Exception {
        mockMvc.perform(patch("/api/v1/admin/stores/" + storeId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeName":"新门店","phone":"13800000000","status":"OPEN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").value("新门店"));

        mockMvc.perform(put("/api/v1/admin/stores/" + storeId + "/config")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"homeServiceRadiusKm":8.5,"bookingAdvanceDays":21,
                                 "bookingCancelHours":6,"timeSlotMinutes":30,
                                 "autoConfirmBooking":false,"contentAutoPublish":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingAdvanceDays").value(21));

        mockMvc.perform(get("/api/v1/admin/stores/" + storeId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.storeName").value("新门店"));

        mockMvc.perform(get("/api/v1/admin/stores/" + storeId + "/config")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.bookingAdvanceDays").value(21));
    }

    @Test
    void serviceItemsSupportPagedListCreateAndDisable() throws Exception {
        mockMvc.perform(get("/api/v1/admin/service-items?page=1&size=100")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id").value(hasItem(serviceItemId.toString())));

        mockMvc.perform(post("/api/v1/admin/service-items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"上门喂养","serviceMode":"HOME",
                                 "price":120,"durationMinutes":60,"petType":"ALL",
                                 "petSize":"ALL","needAddress":true,"needPet":true,"sort":1}
                                """.formatted(secondServiceCategoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("上门喂养"));

        mockMvc.perform(post("/api/v1/admin/service-items/" + serviceItemId + "/disable")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF_SALE"));

        mockMvc.perform(post("/api/v1/admin/service-items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":999999,"name":"无效分类服务","serviceMode":"STORE",
                                 "price":50,"durationMinutes":30,"needAddress":false,"needPet":true}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("resource_not_found"));
    }

    @Test
    void staffSkillsAndSchedulesCanBeManaged() throws Exception {
        mockMvc.perform(put("/api/v1/admin/staff/" + staffId + "/skills")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serviceCategoryIds\":[%d,%d]}"
                                .formatted(serviceCategoryId, secondServiceCategoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.serviceCategoryIds.length()").value(2));

        mockMvc.perform(post("/api/v1/admin/staff/" + staffId + "/schedules")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":%d,"workDate":"2026-06-10","startTime":"09:00:00",
                                 "endTime":"18:00:00","status":"AVAILABLE","remark":"正常班"}
                                """.formatted(storeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.staffId").value(staffId));

        mockMvc.perform(get("/api/v1/admin/staff/" + staffId + "/schedules?page=1&size=10")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(post("/api/v1/admin/staff/" + staffId + "/schedules")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":%d,"workDate":"2026-06-10","startTime":"17:00:00",
                                 "endTime":"20:00:00","status":"AVAILABLE"}
                                """.formatted(storeId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("state_conflict"));
    }

    @Test
    void staffProfilesAndScheduleUpdatesAreManaged() throws Exception {
        mockMvc.perform(get("/api/v1/admin/staff?page=1&size=100")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[*].id").value(hasItem(staffId.toString())));

        String createdStaffBody = mockMvc.perform(post("/api/v1/admin/staff")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":%d,"name":"员工乙","phone":"13900000000",
                                 "role":"WALKER","description":"负责遛宠"}
                                """.formatted(storeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String createdStaffId = createdStaffBody.replaceAll(".*\"id\":\"?(\\d+)\"?.*", "$1");

        mockMvc.perform(put("/api/v1/admin/staff/" + createdStaffId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":%d,"name":"员工乙更新","role":"FEEDER"}
                                """.formatted(storeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("员工乙更新"));

        String scheduleBody = mockMvc.perform(post("/api/v1/admin/staff/" + staffId + "/schedules")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":%d,"workDate":"2026-06-11","startTime":"09:00:00",
                                 "endTime":"18:00:00","status":"AVAILABLE"}
                                """.formatted(storeId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String parsedScheduleId = scheduleBody.replaceAll(".*\"id\":\"?(\\d+)\"?.*", "$1");

        mockMvc.perform(put("/api/v1/admin/staff/" + staffId + "/schedules/" + parsedScheduleId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":%d,"workDate":"2026-06-11","startTime":"10:00:00",
                                 "endTime":"19:00:00","status":"AVAILABLE"}
                                """.formatted(storeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startTime").value("10:00:00"));

        mockMvc.perform(post("/api/v1/admin/staff/" + createdStaffId + "/disable")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    @Test
    void productsSupportPagedListAndStockUpdate() throws Exception {
        mockMvc.perform(get("/api/v1/admin/products?page=1&size=10")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1));

        mockMvc.perform(put("/api/v1/admin/products/" + productId + "/stock")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stock\":35}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock").value(35));

        mockMvc.perform(put("/api/v1/admin/products/" + productId + "/stock")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stock\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    void serviceAndProductUpdateDisablePathsAreAvailable() throws Exception {
        mockMvc.perform(put("/api/v1/admin/service-items/" + serviceItemId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"升级洗护","serviceMode":"BOTH",
                                 "price":129,"durationMinutes":90,"needAddress":true,"needPet":true,
                                 "imageUrls":["https://example.com/service-a.jpg","https://example.com/service-b.jpg"]}
                                """.formatted(serviceCategoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("升级洗护"))
                .andExpect(jsonPath("$.data.imageUrls.length()").value(2))
                .andExpect(jsonPath("$.data.imageUrls[0]").value("https://example.com/service-a.jpg"));

        String productBody = mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"新商品","price":30,
                                 "pickupOnly":true,"sort":1,
                                 "imageUrls":["https://example.com/product-a.jpg"],
                                 "detailImageUrls":["https://example.com/product-intro-a.jpg"]}
                                """.formatted(productCategoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stock").value(0))
                .andExpect(jsonPath("$.data.imageUrls.length()").value(1))
                .andExpect(jsonPath("$.data.detailImageUrls.length()").value(1))
                .andReturn().getResponse().getContentAsString();
        String createdProductId = productBody.replaceAll(".*\"id\":\"?(\\d+)\"?.*", "$1");

        mockMvc.perform(put("/api/v1/admin/products/" + createdProductId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"新商品更新","price":35,
                                 "pickupOnly":true,"sort":2,
                                 "imageUrls":["https://example.com/product-b.jpg","https://example.com/product-c.jpg"],
                                 "detailImageUrls":["https://example.com/product-intro-b.jpg","https://example.com/product-intro-c.jpg"]}
                                """.formatted(productCategoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新商品更新"))
                .andExpect(jsonPath("$.data.imageUrls.length()").value(2))
                .andExpect(jsonPath("$.data.imageUrls[0]").value("https://example.com/product-b.jpg"))
                .andExpect(jsonPath("$.data.detailImageUrls.length()").value(2))
                .andExpect(jsonPath("$.data.detailImageUrls[0]").value("https://example.com/product-intro-b.jpg"));

        mockMvc.perform(post("/api/v1/admin/products/" + createdProductId + "/disable")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OFF_SALE"));
    }

    @Test
    void serviceDetailImagesAcceptTwentyAndProductCarouselAcceptsFiveAndProductIntroImagesAcceptTwenty()
            throws Exception {
        mockMvc.perform(put("/api/v1/admin/service-items/" + serviceItemId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"二十图洗护","serviceMode":"BOTH",
                                 "price":129,"durationMinutes":90,"needAddress":true,"needPet":true,
                                 "imageUrls":%s}
                                """.formatted(serviceCategoryId, imageUrlsJson(20, "service"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrls.length()").value(20))
                .andExpect(jsonPath("$.data.imageUrls[19]").value("https://example.com/service-20.jpg"));

        mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"五图商品","price":30,
                                 "pickupOnly":true,"sort":1,
                                 "imageUrls":%s,
                                 "detailImageUrls":%s}
                                """.formatted(productCategoryId, imageUrlsJson(5, "product-carousel"),
                                        imageUrlsJson(20, "product-intro"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrls.length()").value(5))
                .andExpect(jsonPath("$.data.imageUrls[4]").value("https://example.com/product-carousel-5.jpg"))
                .andExpect(jsonPath("$.data.detailImageUrls.length()").value(20))
                .andExpect(jsonPath("$.data.detailImageUrls[19]").value("https://example.com/product-intro-20.jpg"));

        mockMvc.perform(put("/api/v1/admin/service-items/" + serviceItemId)
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"二十一图洗护","serviceMode":"BOTH",
                                 "price":129,"durationMinutes":90,"needAddress":true,"needPet":true,
                                 "imageUrls":%s}
                                """.formatted(serviceCategoryId, imageUrlsJson(21, "service"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));

        mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"六图商品","price":30,
                                 "pickupOnly":true,"sort":1,
                                 "imageUrls":%s}
                                """.formatted(productCategoryId, imageUrlsJson(6, "product"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));

        mockMvc.perform(post("/api/v1/admin/products")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"二十一张介绍图商品","price":30,
                                 "pickupOnly":true,"sort":1,
                                 "imageUrls":%s,
                                 "detailImageUrls":%s}
                                """.formatted(productCategoryId, imageUrlsJson(5, "product-carousel"),
                                        imageUrlsJson(21, "product-intro"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    void productCarouselImagesCanBeConfiguredFromAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/admin/product-carousel-images")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"images":[
                                  {"title":"新品推荐","imageUrl":"https://example.com/carousel-1.jpg",
                                   "linkType":"PRODUCT","linkTargetId":%d,"status":"ACTIVE","sort":2},
                                  {"title":"门店精选","imageUrl":"https://example.com/carousel-2.jpg",
                                   "linkType":"NONE","status":"ACTIVE","sort":1},
                                  {"title":"热卖主粮","imageUrl":"https://example.com/carousel-3.jpg",
                                   "linkType":"NONE","status":"ACTIVE","sort":3},
                                  {"title":"护理专区","imageUrl":"https://example.com/carousel-4.jpg",
                                   "linkType":"NONE","status":"ACTIVE","sort":4},
                                  {"title":"到店自提","imageUrl":"https://example.com/carousel-5.jpg",
                                   "linkType":"NONE","status":"ACTIVE","sort":5}
                                ]}
                                """.formatted(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].title").value("门店精选"))
                .andExpect(jsonPath("$.data[1].title").value("新品推荐"))
                .andExpect(jsonPath("$.data[4].title").value("到店自提"));

        mockMvc.perform(get("/api/v1/admin/product-carousel-images")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5))
                .andExpect(jsonPath("$.data[0].imageUrl").value("https://example.com/carousel-2.jpg"));
    }

    @Test
    void productCarouselImagesRejectMoreThanFive() throws Exception {
        mockMvc.perform(put("/api/v1/admin/product-carousel-images")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"images\":%s}".formatted(carouselImagesJson(6))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("validation_error"));
    }

    @Test
    void operationLogsArePagedAndContainImportantWrites() throws Exception {
        mockMvc.perform(put("/api/v1/admin/products/" + productId + "/stock")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stock\":36}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/operation-logs?page=1&size=10&module=product")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].operation").value("update-stock"))
                .andExpect(jsonPath("$.data.items[0].result").value("SUCCESS"));
    }

    @Test
    void failedWriteOperationProducesFailAuditLog() throws Exception {
        mockMvc.perform(post("/api/v1/admin/service-items")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":999999,"name":"无效分类","serviceMode":"STORE",
                                 "price":50,"durationMinutes":30,"needAddress":false,"needPet":true}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/admin/operation-logs?page=1&size=10&module=service")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.items[?(@.operation=='create-item' && @.result=='FAIL')]").exists())
                .andExpect(jsonPath("$.data.items[?(@.operation=='create-item' && @.result=='FAIL')].errorMessage")
                        .isNotEmpty());
    }

    @Test
    void updateNonexistentScheduleReturns404NotConflict() throws Exception {
        mockMvc.perform(put("/api/v1/admin/staff/" + staffId + "/schedules/999999")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeId":%d,"workDate":"2026-06-12","startTime":"09:00:00",
                                 "endTime":"18:00:00","status":"AVAILABLE"}
                                """.formatted(storeId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("resource_not_found"));
    }

    private String createAdmin(String username, String roleCode, String[] permissions) {
        AdminUser admin = new AdminUser();
        admin.setUsername(username);
        admin.setPassword(passwordEncoder.encode("password123456"));
        admin.setRole(roleCode);
        admin.setStatus("ACTIVE");
        adminUserService.save(admin);

        AdminRole role = new AdminRole();
        role.setRoleCode(roleCode);
        role.setRoleName(roleCode);
        role.setStatus("ACTIVE");
        adminRoleService.save(role);

        for (String code : permissions) {
            AdminPermission permission = new AdminPermission();
            permission.setPermissionCode(code);
            permission.setPermissionName(code);
            permission.setModule(code.substring(0, code.indexOf(':')));
            permission.setStatus("ACTIVE");
            adminPermissionService.save(permission);

            AdminRolePermission relation = new AdminRolePermission();
            relation.setRoleId(role.getId());
            relation.setPermissionId(permission.getId());
            adminRolePermissionService.save(relation);
        }
        return jwtTokenService.signAdminToken(admin.getId(), username, roleCode);
    }

    private String bearer(String jwt) {
        return "Bearer " + jwt;
    }

    private String imageUrlsJson(int count, String prefix) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> "\"https://example.com/" + prefix + "-" + i + ".jpg\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    private String carouselImagesJson(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> """
                        {"title":"展示图%d","imageUrl":"https://example.com/carousel-%d.jpg",
                         "linkType":"NONE","status":"ACTIVE","sort":%d}
                        """.formatted(i, i, i))
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    @Test
    void staffCanBeReEnabledAfterDisable() throws Exception {
        // 先禁用
        mockMvc.perform(post("/api/v1/admin/staff/" + staffId + "/disable")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));

        // 禁用后可重新启用
        mockMvc.perform(post("/api/v1/admin/staff/" + staffId + "/enable")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void enableActiveStaffReturnsStateConflict() throws Exception {
        // setUp 中 staff 初始为 ACTIVE，直接启用应返回冲突
        mockMvc.perform(post("/api/v1/admin/staff/" + staffId + "/enable")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isConflict());
    }
}
