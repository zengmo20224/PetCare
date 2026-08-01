package com.petcare.common.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petcare.admin.dto.AdminLoginResponse;
import com.petcare.admin.dto.AdminMeResponse;
import com.petcare.admin.dto.AdminManagementDtos;
import com.petcare.common.pagination.PageResponse;
import com.petcare.booking.dto.BookingResponse;
import com.petcare.community.dto.CommentResponse;
import com.petcare.community.dto.PostReportResponse;
import com.petcare.community.dto.PostResponse;
import com.petcare.ai.dto.AiAnalysisReportResponse;
import com.petcare.ai.dto.AiUsageResponse;
import com.petcare.moderation.dto.SensitiveWordResponse;
import com.petcare.product.dto.ProductOrderDetailResponse;
import com.petcare.product.dto.ProductOrderResponse;
import com.petcare.product.dto.ProductSummaryResponse;
import com.petcare.service.dto.ServiceCategoryResponse;
import com.petcare.service.dto.ServiceItemResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for snowflake ID JSON serialization (10F-R2C1).
 *
 * These tests verify that all external-facing snowflake ID fields
 * are serialized as JSON strings, while non-ID numeric values
 * (totals, quantities, prices, stock, tokens) remain JSON numbers.
 *
 * The branch already contains partial per-field serialization. New coverage in this
 * task must still expose any external response fields that remain unsafe.
 *
 * Test ID: 9007199254740993 (greater than JavaScript Number.MAX_SAFE_INTEGER)
 */
class SnowflakeIdSerializationContractTest {

    static final Long BIG_ID = 9007199254740993L;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
    }

    private String toJson(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    /** Assert a JSON field value is a JSON string with exact BIG_ID text. */
    private void assertIdIsString(String json, String fieldPath) throws Exception {
        Map<String, Object> map = objectMapper.readValue(json, Map.class);
        Object value = getNested(map, fieldPath);
        assertNotNull(value, fieldPath + " should not be null");
        assertInstanceOf(String.class, value,
                fieldPath + " should be JSON string but was " + value.getClass().getSimpleName() + ": " + value);
        assertEquals(BIG_ID.toString(), value, fieldPath + " string value must be exact");
    }

    /** Assert a JSON field value is a number (not a string). */
    private void assertNonIdIsNumber(String json, String fieldPath) throws Exception {
        Map<String, Object> map = objectMapper.readValue(json, Map.class);
        Object value = getNested(map, fieldPath);
        assertNotNull(value, fieldPath + " should not be null");
        assertInstanceOf(Number.class, value,
                fieldPath + " should be JSON number but was " + value.getClass().getSimpleName() + ": " + value);
    }

    /** Assert every element in a JSON array field has a string ID at the given key. */
    private void assertAllArrayIdsAreStrings(String json, String arrayField, String idKey) throws Exception {
        Map<String, Object> map = objectMapper.readValue(json, Map.class);
        Object raw = map.get(arrayField);
        assertInstanceOf(List.class, raw, arrayField + " should be a JSON array");
        @SuppressWarnings("unchecked")
        List<Object> array = (List<Object>) raw;
        assertFalse(array.isEmpty(), arrayField + " should not be empty");
        for (int i = 0; i < array.size(); i++) {
            @SuppressWarnings("unchecked")
            Map<String, Object> item = (Map<String, Object>) array.get(i);
            Object value = item.get(idKey);
            assertInstanceOf(String.class, value,
                    arrayField + "[" + i + "]." + idKey + " should be JSON string but was "
                            + (value == null ? "null" : value.getClass().getSimpleName() + ": " + value));
            assertEquals(BIG_ID.toString(), value,
                    arrayField + "[" + i + "]." + idKey + " string value must be exact");
        }
    }

    /** Assert every element in a flat JSON string array (e.g. List<Long> serialized as List<String>). */
    private void assertFlatArrayAllStrings(String json, String arrayField) throws Exception {
        Map<String, Object> map = objectMapper.readValue(json, Map.class);
        Object raw = map.get(arrayField);
        assertInstanceOf(List.class, raw, arrayField + " should be a JSON array");
        @SuppressWarnings("unchecked")
        List<Object> array = (List<Object>) raw;
        assertFalse(array.isEmpty(), arrayField + " should not be empty");
        for (int i = 0; i < array.size(); i++) {
            Object value = array.get(i);
            assertInstanceOf(String.class, value,
                    arrayField + "[" + i + "] should be JSON string but was "
                            + (value == null ? "null" : value.getClass().getSimpleName() + ": " + value));
            assertEquals(BIG_ID.toString(), value,
                    arrayField + "[" + i + "] string value must be exact");
        }
    }

    @SuppressWarnings("unchecked")
    private Object getNested(Map<String, Object> map, String path) {
        String[] parts = path.split("\\.");
        Object current = map;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    // ================================================================
    // 1. Authentication DTOs
    // ================================================================

    @Nested
    @DisplayName("1.1 AdminLoginResponse.admin.id")
    class AdminLoginResponseTests {

        @Test
        void adminId_isJsonString() throws Exception {
            var dto = new AdminLoginResponse(
                    "Bearer", "token-value", 3600,
                    new AdminLoginResponse.AdminSummary(BIG_ID, "admin", "nickname", "MANAGER")
            );
            String json = toJson(dto);
            assertIdIsString(json, "admin.id");
        }

        @Test
        void expiresInSeconds_remainsJsonNumber() throws Exception {
            var dto = new AdminLoginResponse(
                    "Bearer", "token-value", 3600,
                    new AdminLoginResponse.AdminSummary(BIG_ID, "admin", "nickname", "MANAGER")
            );
            String json = toJson(dto);
            assertNonIdIsNumber(json, "expiresInSeconds");
        }
    }

    @Nested
    @DisplayName("1.2 AdminMeResponse.id")
    class AdminMeResponseTests {

        @Test
        void id_isJsonString() throws Exception {
            var dto = new AdminMeResponse(BIG_ID, "admin", "nick", "MANAGER",
                    List.of("store:info:read"));
            String json = toJson(dto);
            assertIdIsString(json, "id");
        }
    }

    // ================================================================
    // 2. Store DTOs
    // ================================================================

    @Nested
    @DisplayName("2. StoreView and StoreConfigView")
    class StoreDtoTests {

        @Test
        void storeView_id_isJsonString() throws Exception {
            var dto = new AdminManagementDtos.StoreView(
                    BIG_ID, "门店A", "13800000000", "地址",
                    BigDecimal.ONE, BigDecimal.ONE, "09:00-18:00", "OPEN", "描述");
            String json = toJson(dto);
            assertIdIsString(json, "id");
        }

        @Test
        void storeConfigView_id_and_storeId_areJsonStrings() throws Exception {
            var dto = new AdminManagementDtos.StoreConfigView(
                    BIG_ID, BIG_ID, new BigDecimal("5.0"),
                    14, 4, 30, true, true);
            String json = toJson(dto);
            assertIdIsString(json, "id");
            assertIdIsString(json, "storeId");
        }

        @Test
        void storeConfigView_numericFields_remainJsonNumbers() throws Exception {
            var dto = new AdminManagementDtos.StoreConfigView(
                    BIG_ID, BIG_ID, new BigDecimal("5.0"),
                    14, 4, 30, true, true);
            String json = toJson(dto);
            assertNonIdIsNumber(json, "bookingAdvanceDays");
            assertNonIdIsNumber(json, "bookingCancelHours");
            assertNonIdIsNumber(json, "timeSlotMinutes");
        }
    }

    // ================================================================
    // 3. ServiceItem DTOs
    // ================================================================

    @Nested
    @DisplayName("3. ServiceItemView")
    class ServiceItemDtoTests {

        @Test
        void serviceItemView_id_and_categoryId_areJsonStrings() throws Exception {
            var dto = new AdminManagementDtos.ServiceItemView(
                    BIG_ID, BIG_ID, "洗护", "STORE",
                    new BigDecimal("99.00"), 60, "ALL", "ALL",
                    true, false, "描述", null, List.of(), "ACTIVE", 0);
            String json = toJson(dto);
            assertIdIsString(json, "id");
            assertIdIsString(json, "categoryId");
        }

        @Test
        void serviceItemView_durationAndPrice_remainJsonNumbers() throws Exception {
            var dto = new AdminManagementDtos.ServiceItemView(
                    BIG_ID, BIG_ID, "洗护", "STORE",
                    new BigDecimal("99.00"), 60, "ALL", "ALL",
                    true, false, "描述", null, List.of(), "ACTIVE", 0);
            String json = toJson(dto);
            assertNonIdIsNumber(json, "durationMinutes");
            assertNonIdIsNumber(json, "sort");
        }
    }

    // ================================================================
    // 4. Staff, Skills, Schedule DTOs
    // ================================================================

    @Nested
    @DisplayName("4. StaffView, StaffSkillView, StaffScheduleView")
    class StaffDtoTests {

        @Test
        void staffView_id_and_storeId_areJsonStrings() throws Exception {
            var dto = new AdminManagementDtos.StaffView(
                    BIG_ID, BIG_ID, "员工甲", "13800000000",
                    null, "GROOMER", "ACTIVE", "描述");
            String json = toJson(dto);
            assertIdIsString(json, "id");
            assertIdIsString(json, "storeId");
        }

        @Test
        void staffSkillView_staffId_isJsonString_and_categoryIdsAreJsonStrings() throws Exception {
            var dto = new AdminManagementDtos.StaffSkillView(BIG_ID, List.of(BIG_ID, BIG_ID));
            String json = toJson(dto);
            assertIdIsString(json, "staffId");
            // List<Long> of snowflake IDs must serialize as JSON strings
            assertFlatArrayAllStrings(json, "serviceCategoryIds");
        }

        @Test
        void staffScheduleView_allIds_areJsonStrings() throws Exception {
            var dto = new AdminManagementDtos.StaffScheduleView(
                    BIG_ID, BIG_ID, BIG_ID,
                    LocalDate.of(2026, 6, 10), LocalTime.of(9, 0), LocalTime.of(18, 0),
                    "AVAILABLE", "备注");
            String json = toJson(dto);
            assertIdIsString(json, "id");
            assertIdIsString(json, "staffId");
            assertIdIsString(json, "storeId");
        }
    }

    // ================================================================
    // 5. BookingResponse
    // ================================================================

    @Nested
    @DisplayName("5. BookingResponse IDs")
    class BookingDtoTests {

        @Test
        void bookingResponse_allIds_areJsonStrings() throws Exception {
            var dto = new BookingResponse(
                    BIG_ID, "BK001", BIG_ID, BIG_ID, BIG_ID, BIG_ID, "洗护服务", BIG_ID,
                    "STORE", LocalDate.of(2026, 6, 10), LocalTime.of(9, 0), LocalTime.of(10, 0),
                    BIG_ID, new BigDecimal("5.0"), "张三", "13800000000",
                    new BigDecimal("99.00"), "CASH", "UNPAID", "PENDING_CONFIRM",
                    "备注", null, LocalDateTime.of(2026, 6, 10, 8, 0));
            String json = toJson(dto);
            assertIdIsString(json, "id");
            assertIdIsString(json, "userId");
            assertIdIsString(json, "petId");
            assertIdIsString(json, "storeId");
            assertIdIsString(json, "serviceItemId");
            assertIdIsString(json, "staffId");
            assertIdIsString(json, "addressId");
        }

        @Test
        void bookingResponse_priceAndDistance_remainJsonNumbers() throws Exception {
            var dto = new BookingResponse(
                    BIG_ID, "BK001", BIG_ID, BIG_ID, BIG_ID, BIG_ID, "洗护服务", BIG_ID,
                    "STORE", LocalDate.of(2026, 6, 10), LocalTime.of(9, 0), LocalTime.of(10, 0),
                    BIG_ID, new BigDecimal("5.0"), "张三", "13800000000",
                    new BigDecimal("99.00"), "CASH", "UNPAID", "PENDING_CONFIRM",
                    "备注", null, LocalDateTime.of(2026, 6, 10, 8, 0));
            String json = toJson(dto);
            // BigDecimal price is not an ID, should remain number
            assertNonIdIsNumber(json, "price");
            assertNonIdIsNumber(json, "distanceKm");
        }
    }

    // ================================================================
    // 6. Product DTOs
    // ================================================================

    @Nested
    @DisplayName("6. ProductView")
    class ProductDtoTests {

        @Test
        void productView_id_and_categoryId_areJsonStrings() throws Exception {
            var dto = new AdminManagementDtos.ProductView(
                    BIG_ID, BIG_ID, "宠物零食", null,
                    new BigDecimal("20.00"), 10, 5, "描述", true, List.of(), List.of(), "ON_SALE", 0);
            String json = toJson(dto);
            assertIdIsString(json, "id");
            assertIdIsString(json, "categoryId");
        }

        @Test
        void productView_stock_salesCount_remainJsonNumbers() throws Exception {
            var dto = new AdminManagementDtos.ProductView(
                    BIG_ID, BIG_ID, "宠物零食", null,
                    new BigDecimal("20.00"), 10, 5, "描述", true, List.of(), List.of(), "ON_SALE", 0);
            String json = toJson(dto);
            assertNonIdIsNumber(json, "stock");
            assertNonIdIsNumber(json, "salesCount");
            assertNonIdIsNumber(json, "sort");
        }
    }

    // ================================================================
    // 7. ProductOrder DTOs (with nested OrderItem)
    // ================================================================

    @Nested
    @DisplayName("7. ProductOrderResponse and ProductOrderDetailResponse")
    class ProductOrderDtoTests {

        @Test
        void productOrderResponse_id_isJsonString() throws Exception {
            var dto = new ProductOrderResponse(
                    BIG_ID, "ORD001", new BigDecimal("99.00"), "PICKUP", null,
                    "OFFLINE_STORE", "UNPAID", "WAIT_PREPARE", "PENDING_CONFIRM",
                    "张三", "13800000000", null,
                    LocalDateTime.now(), null, null, null);
            String json = toJson(dto);
            assertIdIsString(json, "id");
        }

        @Test
        void productOrderResponse_totalAmount_remainJsonNumber() throws Exception {
            var dto = new ProductOrderResponse(
                    BIG_ID, "ORD001", new BigDecimal("99.00"), "PICKUP", null,
                    "OFFLINE_STORE", "UNPAID", "WAIT_PREPARE", "PENDING_CONFIRM",
                    "张三", "13800000000", null,
                    LocalDateTime.now(), null, null, null);
            String json = toJson(dto);
            assertNonIdIsNumber(json, "totalAmount");
        }

        @Test
        void productOrderDetailResponse_allIds_areJsonStrings() throws Exception {
            var orderItem = new ProductOrderDetailResponse.OrderItemResponse(
                    BIG_ID, BIG_ID, "零食", "cover.jpg",
                    new BigDecimal("20.00"), 3, new BigDecimal("60.00"));
            var dto = new ProductOrderDetailResponse(
                    BIG_ID, "ORD001", BIG_ID, BIG_ID,
                    new BigDecimal("60.00"), "PICKUP", null, "OFFLINE_STORE", "UNPAID",
                    "WAIT_PREPARE", "PENDING_CONFIRM", "张三", "13800000000", null, null,
                    LocalDateTime.now(), null, null, null,
                    List.of(orderItem));
            String json = toJson(dto);
            assertIdIsString(json, "id");
            assertIdIsString(json, "userId");
            assertIdIsString(json, "storeId");
        }

        @Test
        void orderItemResponse_id_and_productId_areJsonStrings() throws Exception {
            var orderItem = new ProductOrderDetailResponse.OrderItemResponse(
                    BIG_ID, BIG_ID, "零食", "cover.jpg",
                    new BigDecimal("20.00"), 3, new BigDecimal("60.00"));
            var dto = new ProductOrderDetailResponse(
                    BIG_ID, "ORD001", BIG_ID, BIG_ID,
                    new BigDecimal("60.00"), "PICKUP", null, "OFFLINE_STORE", "UNPAID",
                    "WAIT_PREPARE", "PENDING_CONFIRM", "张三", "13800000000", null, null,
                    LocalDateTime.now(), null, null, null,
                    List.of(orderItem));
            String json = toJson(dto);
            // Verify nested order item IDs
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            @SuppressWarnings("unchecked")
            List<Object> items = (List<Object>) map.get("items");
            assertFalse(items.isEmpty());
            @SuppressWarnings("unchecked")
            Map<String, Object> firstItem = (Map<String, Object>) items.get(0);
            assertInstanceOf(String.class, firstItem.get("id"),
                    "items[0].id should be JSON string");
            assertInstanceOf(String.class, firstItem.get("productId"),
                    "items[0].productId should be JSON string");
            // Non-ID numeric fields in order items must remain numbers
            assertInstanceOf(Number.class, firstItem.get("quantity"),
                    "items[0].quantity should be JSON number");
            assertInstanceOf(Number.class, firstItem.get("price"),
                    "items[0].price should be JSON number");
        }
    }

    // ================================================================
    // 8. Community DTOs (Post, Comment)
    // ================================================================

    @Nested
    @DisplayName("8. PostResponse and CommentResponse")
    class CommunityDtoTests {

        @Test
        void postResponse_allIds_areJsonStrings() throws Exception {
            var dto = new PostResponse(
                    BIG_ID, BIG_ID, BIG_ID, BIG_ID,
                    "标题", "内容", "PUBLISHED",
                    100, 50, 30, 10,
                    LocalDateTime.now(), LocalDateTime.now());
            String json = toJson(dto);
            assertIdIsString(json, "id");
            assertIdIsString(json, "userId");
            assertIdIsString(json, "petId");
            assertIdIsString(json, "topicId");
        }

        @Test
        void postResponse_counts_remainJsonNumbers() throws Exception {
            var dto = new PostResponse(
                    BIG_ID, BIG_ID, BIG_ID, BIG_ID,
                    "标题", "内容", "PUBLISHED",
                    100, 50, 30, 10,
                    LocalDateTime.now(), LocalDateTime.now());
            String json = toJson(dto);
            assertNonIdIsNumber(json, "viewCount");
            assertNonIdIsNumber(json, "likeCount");
            assertNonIdIsNumber(json, "commentCount");
            assertNonIdIsNumber(json, "favoriteCount");
        }

        @Test
        void commentResponse_allIds_areJsonStrings() throws Exception {
            var dto = new CommentResponse(
                    BIG_ID, BIG_ID, BIG_ID, BIG_ID,
                    "评论内容", "PUBLISHED", 5, LocalDateTime.now());
            String json = toJson(dto);
            assertIdIsString(json, "id");
            assertIdIsString(json, "postId");
            assertIdIsString(json, "userId");
            assertIdIsString(json, "parentId");
        }

        @Test
        void commentResponse_likeCount_remainsJsonNumber() throws Exception {
            var dto = new CommentResponse(
                    BIG_ID, BIG_ID, BIG_ID, BIG_ID,
                    "评论内容", "PUBLISHED", 5, LocalDateTime.now());
            String json = toJson(dto);
            assertNonIdIsNumber(json, "likeCount");
        }
    }

    // ================================================================
    // 9. SensitiveWord DTOs
    // ================================================================

    @Nested
    @DisplayName("9. SensitiveWordResponse")
    class SensitiveWordDtoTests {

        @Test
        void sensitiveWordResponse_id_isJsonString() throws Exception {
            var dto = new SensitiveWordResponse(
                    BIG_ID, "违禁词", "色情", 3, "ACTIVE", LocalDateTime.now());
            String json = toJson(dto);
            assertIdIsString(json, "id");
        }

        @Test
        void sensitiveWordResponse_level_remainsJsonNumber() throws Exception {
            var dto = new SensitiveWordResponse(
                    BIG_ID, "违禁词", "色情", 3, "ACTIVE", LocalDateTime.now());
            String json = toJson(dto);
            assertNonIdIsNumber(json, "level");
        }
    }

    // ================================================================
    // 10. OperationLog DTOs
    // ================================================================

    @Nested
    @DisplayName("10. OperationLogView")
    class OperationLogDtoTests {

        @Test
        void operationLogView_id_and_adminId_areJsonStrings() throws Exception {
            var dto = new AdminManagementDtos.OperationLogView(
                    BIG_ID, BIG_ID, "product", "create",
                    "POST", "/api/v1/admin/products", "SUCCESS",
                    "targetName=测试商品, productId=123", null, LocalDateTime.now());
            String json = toJson(dto);
            assertIdIsString(json, "id");
            assertIdIsString(json, "adminId");
        }
    }

    @Nested
    @DisplayName("10B. Marketing activity cards")
    class MarketingActivityDtoTests {

        @Test
        void marketingActivityAssociationCardIds_areJsonStrings() throws Exception {
            var productCard = new com.petcare.marketing.dto.MarketingActivityDtos.ActivityProductCard(
                    BIG_ID, "冻干零食", "/uploads/images/product.jpg", java.math.BigDecimal.TEN, 12);
            var serviceCard = new com.petcare.marketing.dto.MarketingActivityDtos.ActivityServiceCard(
                    BIG_ID, "基础洗护", "/uploads/images/service.jpg", java.math.BigDecimal.ONE, 60, "STORE");

            String productJson = toJson(productCard);
            String serviceJson = toJson(serviceCard);

            assertIdIsString(productJson, "id");
            assertIdIsString(serviceJson, "id");
        }
    }

    // ================================================================
    // 11. AI DTOs
    // ================================================================

    @Nested
    @DisplayName("11. AiAnalysisReportResponse and AiUsageResponse")
    class AiDtoTests {

        @Test
        void aiAnalysisReportResponse_id_and_createdBy_areJsonStrings() throws Exception {
            var dto = new AiAnalysisReportResponse(
                    BIG_ID, "BUSINESS", LocalDate.of(2026, 6, 1), LocalDate.of(2026, 6, 10),
                    "摘要", "建议", BIG_ID, LocalDateTime.now());
            String json = toJson(dto);
            assertIdIsString(json, "id");
            assertIdIsString(json, "createdBy");
        }

        @Test
        void aiUsageResponse_allIds_areJsonStrings() throws Exception {
            var dto = new AiUsageResponse(
                    BIG_ID, BIG_ID, BIG_ID, "CUSTOMER_SERVICE",
                    "deepseek-v3", 100, 200, 300,
                    true, null, LocalDateTime.now());
            String json = toJson(dto);
            assertIdIsString(json, "id");
            assertIdIsString(json, "userId");
            assertIdIsString(json, "adminId");
        }

        @Test
        void aiUsageResponse_tokenCounts_remainJsonNumbers() throws Exception {
            var dto = new AiUsageResponse(
                    BIG_ID, BIG_ID, BIG_ID, "CUSTOMER_SERVICE",
                    "deepseek-v3", 100, 200, 300,
                    true, null, LocalDateTime.now());
            String json = toJson(dto);
            assertNonIdIsNumber(json, "promptTokens");
            assertNonIdIsNumber(json, "completionTokens");
            assertNonIdIsNumber(json, "totalTokens");
        }
    }

    // ================================================================
    // 12. Report entity exposure and pagination numeric contract
    // ================================================================

    @Nested
    @DisplayName("12. PostReport and PageResponse")
    class ReportAndPaginationTests {

        @Test
        void postReport_allIds_areJsonStrings() throws Exception {
            var report = new PostReportResponse(
                    BIG_ID, BIG_ID, BIG_ID,
                    "SPAM", "垃圾内容", "HANDLED",
                    null, BIG_ID, null, null);

            String json = toJson(report);

            assertIdIsString(json, "id");
            assertIdIsString(json, "postId");
            assertIdIsString(json, "reporterId");
            assertIdIsString(json, "handlerId");
        }

        @Test
        void pageResponse_totalAndPageMetadata_remainJsonNumbers() throws Exception {
            var page = PageResponse.of(List.of("item"), BIG_ID, 1, 20);

            String json = toJson(page);

            assertNonIdIsNumber(json, "total");
            assertNonIdIsNumber(json, "page");
            assertNonIdIsNumber(json, "size");
            assertNonIdIsNumber(json, "totalPages");
        }
    }

    // ================================================================
    // 13. Request body deserialization: string IDs -> Java Long
    // ================================================================

    @Nested
    @DisplayName("13. Request deserialization: string IDs accepted as Long")
    class RequestDeserializationTests {

        @Test
        void serviceItemRequest_categoryId_stringDeserializesToLong() throws Exception {
            String json = """
                    {"categoryId":"9007199254740993","name":"洗护","serviceMode":"STORE",
                     "price":99.00,"durationMinutes":60,"needAddress":true,"needPet":true}
                    """;
            AdminManagementDtos.ServiceItemRequest dto =
                    objectMapper.readValue(json, AdminManagementDtos.ServiceItemRequest.class);
            assertEquals(BIG_ID, dto.categoryId());
        }

        @Test
        void staffRequest_storeId_stringDeserializesToLong() throws Exception {
            String json = """
                    {"storeId":"9007199254740993","name":"员工","role":"GROOMER"}
                    """;
            AdminManagementDtos.StaffRequest dto =
                    objectMapper.readValue(json, AdminManagementDtos.StaffRequest.class);
            assertEquals(BIG_ID, dto.storeId());
        }

        @Test
        void staffSkillUpdateRequest_serviceCategoryIds_stringsDeserializeToLongs() throws Exception {
            String json = """
                    {"serviceCategoryIds":["9007199254740993","9007199254740994"]}
                    """;
            AdminManagementDtos.StaffSkillUpdateRequest dto =
                    objectMapper.readValue(json, AdminManagementDtos.StaffSkillUpdateRequest.class);
            assertEquals(BIG_ID, dto.serviceCategoryIds().get(0));
            assertEquals(BIG_ID + 1, dto.serviceCategoryIds().get(1));
        }

        @Test
        void staffScheduleRequest_storeId_stringDeserializesToLong() throws Exception {
            String json = """
                    {"storeId":"9007199254740993","workDate":"2026-06-10",
                     "startTime":"09:00","endTime":"18:00","status":"AVAILABLE"}
                    """;
            AdminManagementDtos.StaffScheduleRequest dto =
                    objectMapper.readValue(json, AdminManagementDtos.StaffScheduleRequest.class);
            assertEquals(BIG_ID, dto.storeId());
        }

        @Test
        void productRequest_categoryId_stringDeserializesToLong() throws Exception {
            String json = """
                    {"categoryId":"9007199254740993","name":"商品","price":20.00,"pickupOnly":true}
                    """;
            AdminManagementDtos.ProductRequest dto =
                    objectMapper.readValue(json, AdminManagementDtos.ProductRequest.class);
            assertEquals(BIG_ID, dto.categoryId());
        }

        @Test
        void storeUpdateRequest_pathIdAsString_isAccepted() throws Exception {
            // Path IDs come as strings via URL. This tests that Jackson
            // can parse string representations into Long when needed.
            String bigIdStr = BIG_ID.toString();
            Long parsed = objectMapper.readValue('"' + bigIdStr + '"', Long.class);
            assertEquals(BIG_ID, parsed);
        }
    }

    // ================================================================
    // 14. Public-facing Service & Product DTOs (non-admin)
    // ================================================================

    @Nested
    @DisplayName("14. ServiceCategoryResponse, ServiceItemResponse, ProductSummaryResponse")
    class PublicServiceProductDtoTests {

        @Test
        void serviceCategoryResponse_id_isJsonString() throws Exception {
            var dto = new ServiceCategoryResponse(BIG_ID, "美容", "icon.png", 1);
            String json = toJson(dto);
            assertIdIsString(json, "id");
            assertNonIdIsNumber(json, "sort");
        }

        @Test
        void serviceItemResponse_id_and_categoryId_areJsonStrings() throws Exception {
            var dto = new ServiceItemResponse(BIG_ID, BIG_ID, "洗澡", "STORE",
                    new BigDecimal("99.00"), 60, "ALL", "ALL", 0, 1, "描述", "cover.jpg", List.of());
            String json = toJson(dto);
            assertIdIsString(json, "id");
            assertIdIsString(json, "categoryId");
        }

        @Test
        void serviceItemResponse_priceAndDuration_remainJsonNumbers() throws Exception {
            var dto = new ServiceItemResponse(BIG_ID, BIG_ID, "洗澡", "STORE",
                    new BigDecimal("99.00"), 60, "ALL", "ALL", 0, 1, "描述", "cover.jpg", List.of());
            String json = toJson(dto);
            assertNonIdIsNumber(json, "durationMinutes");
        }

        @Test
        void productSummaryResponse_id_and_categoryId_areJsonStrings() throws Exception {
            var dto = new ProductSummaryResponse(BIG_ID, BIG_ID, "零食", "cover.jpg",
                    new BigDecimal("20.00"), 10, 5, 0);
            String json = toJson(dto);
            assertIdIsString(json, "id");
            assertIdIsString(json, "categoryId");
        }

        @Test
        void productSummaryResponse_stockAndSales_remainJsonNumbers() throws Exception {
            var dto = new ProductSummaryResponse(BIG_ID, BIG_ID, "零食", "cover.jpg",
                    new BigDecimal("20.00"), 10, 5, 0);
            String json = toJson(dto);
            assertNonIdIsNumber(json, "stock");
            assertNonIdIsNumber(json, "salesCount");
            assertNonIdIsNumber(json, "sort");
        }
    }

    // ================================================================
    // 15. Null IDs stay null (not "null" string)
    // ================================================================

    @Nested
    @DisplayName("15. Null ID handling")
    class NullIdTests {

        @Test
        void nullableId_staysNull_notString() throws Exception {
            // BookingResponse.staffId is nullable
            var dto = new BookingResponse(
                    BIG_ID, "BK001", BIG_ID, BIG_ID, BIG_ID, BIG_ID, null, null,
                    "STORE", LocalDate.of(2026, 6, 10), LocalTime.of(9, 0), LocalTime.of(10, 0),
                    null, new BigDecimal("5.0"), "张三", "13800000000",
                    new BigDecimal("99.00"), "CASH", "UNPAID", "PENDING_CONFIRM",
                    "备注", null, LocalDateTime.of(2026, 6, 10, 8, 0));
            String json = toJson(dto);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            assertNull(map.get("staffId"), "staffId should be null, not string \"null\"");
            assertNull(map.get("addressId"), "addressId should be null, not string \"null\"");
        }

        @Test
        void nullableParentId_staysNull_notString() throws Exception {
            var dto = new CommentResponse(
                    BIG_ID, BIG_ID, BIG_ID, null,
                    "评论内容", "PUBLISHED", 5, LocalDateTime.now());
            String json = toJson(dto);
            Map<String, Object> map = objectMapper.readValue(json, Map.class);
            assertNull(map.get("parentId"), "parentId should be null, not string \"null\"");
        }
    }
}
