package com.example.paginationdemo.service;

import com.example.paginationdemo.dto.ProductResponseDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Window;

import java.math.BigDecimal;

public interface ProductService {

    Page<ProductResponseDto> getProductsPage(String category, Pageable pageable);

    Slice<ProductResponseDto> getProductsSlice(String category, Pageable pageable);

    Window<ProductResponseDto> getProductsWindow(String category, ScrollPosition position, int size);

    Page<ProductResponseDto> getProductsByCustomQuery(
            String category, BigDecimal minPrice, BigDecimal maxPrice, Pageable pageable);

    Page<ProductResponseDto> getProductsFiltered(
            String category, BigDecimal minPrice, BigDecimal maxPrice, String keyword, Pageable pageable);
}
