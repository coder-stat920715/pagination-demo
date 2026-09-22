package com.example.paginationdemo.service;

import com.example.paginationdemo.dto.ProductResponseDto;
import com.example.paginationdemo.repository.ProductRepository;
import com.example.paginationdemo.specification.ProductSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Window;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    @Override
    public Page<ProductResponseDto> getProductsPage(String category, Pageable pageable) {
        // .map() on Page<T> returns a new Page<R> that still carries
        // totalElements/totalPages -- no need to touch .getContent().
        return productRepository.findByCategory(category, pageable)
                .map(ProductResponseDto::from);
    }

    @Override
    public Slice<ProductResponseDto> getProductsSlice(String category, Pageable pageable) {
        return productRepository.findByCategorySlice(category, pageable)
                .map(ProductResponseDto::from);
    }

    @Override
    public Window<ProductResponseDto> getProductsWindow(String category, ScrollPosition position, int size) {
        return productRepository
                .findByCategoryOrderByCreatedAtAscIdAsc(category, Limit.of(size), position)
                .map(ProductResponseDto::from);
    }

    @Override
    public Page<ProductResponseDto> getProductsByCustomQuery(
            String category, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable) {
        return productRepository.findByCategoryAndPriceRange(category, minPrice, maxPrice, pageable)
                .map(ProductResponseDto::from);
    }

    @Override
    public Page<ProductResponseDto> getProductsFiltered(
            String category, BigDecimal minPrice, BigDecimal maxPrice, String keyword, Pageable pageable) {
        var spec = ProductSpecification.build(category, minPrice, maxPrice, keyword);
        return productRepository.findAll(spec, pageable)
                .map(ProductResponseDto::from);
    }
}
