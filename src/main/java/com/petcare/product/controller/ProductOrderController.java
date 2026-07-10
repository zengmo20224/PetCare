package com.petcare.product.controller;

import com.petcare.common.api.ApiResponse;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.pagination.PageResponse;
import com.petcare.product.dto.ProductOrderCreateRequest;
import com.petcare.product.dto.ProductOrderDetailResponse;
import com.petcare.product.dto.ProductOrderResponse;
import com.petcare.product.service.ProductOrderApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * User-facing product order endpoints.
 * currentUserId comes from the security context, not the request body.
 * Returns 401 if no user identity is available.
 */
@RestController
@RequestMapping("/api/v1/product-orders")
public class ProductOrderController {

    private final ProductOrderApplicationService orderService;

    public ProductOrderController(ProductOrderApplicationService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProductOrderResponse>> createOrder(
            @Valid @RequestBody ProductOrderCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        Long currentUserId = resolveCurrentUserId();
        ProductOrderResponse response = orderService.createOrder(currentUserId, request, idempotencyKey);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<PageResponse<ProductOrderResponse>>> getMyOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long currentUserId = resolveCurrentUserId();
        PageResponse<ProductOrderResponse> response = orderService.getMyOrders(currentUserId, page, size);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductOrderDetailResponse>> getOrderDetail(@PathVariable Long id) {
        Long currentUserId = resolveCurrentUserId();
        ProductOrderDetailResponse response = orderService.getOrderDetail(currentUserId, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<ApiResponse<ProductOrderResponse>> cancelOrder(@PathVariable Long id) {
        Long currentUserId = resolveCurrentUserId();
        ProductOrderResponse response = orderService.cancelOrder(currentUserId, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Resolves current user ID from the security context.
     */
    private Long resolveCurrentUserId() {
        return com.petcare.common.security.SecurityContextHelper.getCurrentUserId()
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.UNAUTHORIZED, "请先登录"));
    }
}
