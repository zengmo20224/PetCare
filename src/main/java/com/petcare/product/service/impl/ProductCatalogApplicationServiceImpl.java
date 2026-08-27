package com.petcare.product.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.common.exception.BusinessException;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.common.exception.ErrorCode;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.common.pagination.PageResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.product.dto.ProductCategoryResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.product.dto.ProductCarouselImageResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.product.dto.ProductDetailResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.product.dto.ProductSummaryResponse;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.product.entity.Product;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.product.entity.ProductCarouselImage;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.product.entity.ProductCategory;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.product.entity.ProductDetailImage;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.product.entity.ProductImage;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.product.mapper.ProductCarouselImageMapper;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.product.mapper.ProductCategoryMapper;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.product.mapper.ProductDetailImageMapper;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.product.mapper.ProductImageMapper;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.product.mapper.ProductMapper;
import com.petcare.common.util.SqlLikeUtils;
import com.petcare.product.service.ProductCatalogApplicationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Implementation of product catalog query service.
 * Only returns ON_SALE products and ACTIVE categories that are not deleted.
 */
@Service
public class ProductCatalogApplicationServiceImpl implements ProductCatalogApplicationService {

    private final ProductCategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final ProductImageMapper imageMapper;
    private final ProductDetailImageMapper detailImageMapper;
    private final ProductCarouselImageMapper carouselImageMapper;

    public ProductCatalogApplicationServiceImpl(
            ProductCategoryMapper categoryMapper,
            ProductMapper productMapper,
            ProductImageMapper imageMapper,
            ProductDetailImageMapper detailImageMapper,
            ProductCarouselImageMapper carouselImageMapper) {
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
        this.imageMapper = imageMapper;
        this.detailImageMapper = detailImageMapper;
        this.carouselImageMapper = carouselImageMapper;
    }

    @Override
    public List<ProductCategoryResponse> listCategories() {
        LambdaQueryWrapper<ProductCategory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductCategory::getStatus, "ACTIVE")
               .eq(ProductCategory::getDeleted, 0)
               .orderByAsc(ProductCategory::getSort);

        return categoryMapper.selectList(wrapper).stream()
                .map(c -> new ProductCategoryResponse(
                        c.getId(), c.getName(), c.getIconUrl(), c.getSort()))
                .toList();
    }

    @Override
    public PageResponse<ProductSummaryResponse> listProducts(Long categoryId, String keyword, int page, int size) {
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Product::getStatus, "ON_SALE")
               .eq(Product::getDeleted, 0);
        if (categoryId != null) {
            wrapper.eq(Product::getCategoryId, categoryId);
        }
        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            wrapper.and(w -> w.like(Product::getName, SqlLikeUtils.escape(normalizedKeyword))
                    .or()
                    .like(Product::getDescription, SqlLikeUtils.escape(normalizedKeyword)));
        }
        wrapper.orderByAsc(Product::getSort);

        IPage<Product> result = productMapper.selectPage(
                new Page<>(page, size), wrapper);

        List<ProductSummaryResponse> items = result.getRecords().stream()
                .map(p -> new ProductSummaryResponse(
                        p.getId(), p.getCategoryId(), p.getName(),
                        p.getCoverUrl(), p.getPrice(), p.getStock(),
                        p.getSalesCount(), p.getSort()))
                .toList();

        return PageResponse.of(items, result.getTotal(), page, size);
    }

    @Override
    public ProductDetailResponse getProductDetail(Long productId) {
        Product product = productMapper.selectById(productId);
        if (product == null || product.getDeleted() == 1
                || !"ON_SALE".equals(product.getStatus())) {
            throw new BusinessException(ErrorCode.PRODUCT_NOT_FOUND, "商品不存在或已下架");
        }

        String categoryName = null;
        if (product.getCategoryId() != null) {
            ProductCategory cat = categoryMapper.selectById(product.getCategoryId());
            if (cat != null) {
                categoryName = cat.getName();
            }
        }

        LambdaQueryWrapper<ProductImage> imgWrapper = new LambdaQueryWrapper<>();
        imgWrapper.eq(ProductImage::getProductId, productId)
                  .orderByAsc(ProductImage::getSort);
        List<String> imageUrls = imageMapper.selectList(imgWrapper).stream()
                .map(ProductImage::getImageUrl)
                .toList();

        LambdaQueryWrapper<ProductDetailImage> detailImgWrapper = new LambdaQueryWrapper<>();
        detailImgWrapper.eq(ProductDetailImage::getProductId, productId)
                .orderByAsc(ProductDetailImage::getSort);
        List<String> detailImageUrls = detailImageMapper.selectList(detailImgWrapper).stream()
                .map(ProductDetailImage::getImageUrl)
                .toList();

        return new ProductDetailResponse(
                product.getId(), product.getCategoryId(), categoryName,
                product.getName(), product.getCoverUrl(), product.getPrice(),
                product.getStock(), product.getSalesCount(), product.getDescription(),
                product.getPickupOnly(), imageUrls, detailImageUrls);
    }

    @Override
    public List<ProductCarouselImageResponse> listCarouselImages() {
        LambdaQueryWrapper<ProductCarouselImage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProductCarouselImage::getStatus, "ACTIVE")
               .eq(ProductCarouselImage::getDeleted, 0)
               .orderByAsc(ProductCarouselImage::getSort)
               .orderByAsc(ProductCarouselImage::getCreateTime);

        return carouselImageMapper.selectList(wrapper).stream()
                .map(this::toCarouselImageResponse)
                .toList();
    }

    private ProductCarouselImageResponse toCarouselImageResponse(ProductCarouselImage image) {
        return new ProductCarouselImageResponse(
                image.getId(),
                image.getTitle(),
                image.getImageUrl(),
                image.getLinkType(),
                image.getLinkTargetId(),
                image.getStatus(),
                image.getSort());
    }
}
