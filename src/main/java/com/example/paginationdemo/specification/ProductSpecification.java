package com.example.paginationdemo.specification;

import com.example.paginationdemo.entity.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;

/**
 * Composable predicate builders. Each returns null when the corresponding
 * filter isn't supplied, and Specification.where(...).and(...) silently
 * skips null predicates -- so filters combine cleanly without any manual
 * null-checking / branching in the service layer.
 */
public final class ProductSpecification {

    private ProductSpecification() {
    }

    public static Specification<Product> hasCategory(String category) {
        return (root, query, cb) ->
                (category == null || category.isBlank()) ? null : cb.equal(root.get("category"), category);
    }

    public static Specification<Product> priceGreaterThanOrEqual(BigDecimal minPrice) {
        return (root, query, cb) ->
                minPrice == null ? null : cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Product> priceLessThanOrEqual(BigDecimal maxPrice) {
        return (root, query, cb) ->
                maxPrice == null ? null : cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Product> nameContains(String keyword) {
        return (root, query, cb) ->
                (keyword == null || keyword.isBlank())
                        ? null
                        : cb.like(cb.lower(root.get("name")), "%" + keyword.toLowerCase() + "%");
    }

    public static Specification<Product> build(String category, BigDecimal minPrice, BigDecimal maxPrice, String keyword) {
        return Specification
                .where(hasCategory(category))
                .and(priceGreaterThanOrEqual(minPrice))
                .and(priceLessThanOrEqual(maxPrice))
                .and(nameContains(keyword));
    }
}
