package com.example.paginationdemo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Product entity.
 *
 * The composite index on (category, created_at, id) is the key to this whole demo:
 * it backs the WHERE category = ? ORDER BY created_at, id pattern used by both the
 * offset-based queries (Page/Slice) AND the keyset/cursor query (Window), so the
 * optimizer can satisfy sorting + filtering without a separate filesort.
 */
@Entity
@Table(
        name = "products",
        indexes = {
                @Index(name = "idx_category_created_id", columnList = "category, created_at, id"),
                @Index(name = "idx_sku", columnList = "sku", unique = true)
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String sku;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
