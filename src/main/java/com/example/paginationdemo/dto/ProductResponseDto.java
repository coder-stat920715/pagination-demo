package com.example.paginationdemo.dto;

import com.example.paginationdemo.entity.Product;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Immutable projection returned to clients. Never expose the entity directly.
 * Used with Page<T>.map(), Slice<T>.map() and Window<T>.map() so pagination
 * metadata (total count, hasNext, cursors) is preserved automatically.
 */
public record ProductResponseDto(
        Long id,
        String sku,
        String name,
        String category,
        BigDecimal price,
        Instant createdAt
) {
    public static ProductResponseDto from(Product product) {
        return new ProductResponseDto(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getCategory(),
                product.getPrice(),
                product.getCreatedAt()
        );
    }
}
