package com.example.paginationdemo.repository;

import com.example.paginationdemo.entity.Product;
import org.springframework.data.domain.Limit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Window;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    // ------------------------------------------------------------------
    // 1) STANDARD Page<T>: derived query, Spring Data issues a second
    //    "SELECT COUNT(*)" query automatically to populate totalElements/
    //    totalPages. Correct for page 1..N UIs with page numbers, but the
    //    COUNT(*) gets expensive on large tables and OFFSET gets slower
    //    the deeper you page (DB still has to walk/skip the earlier rows).
    // ------------------------------------------------------------------
    Page<Product> findByCategory(String category, Pageable pageable);

    // ------------------------------------------------------------------
    // 2) LIGHTWEIGHT Slice<T>: same OFFSET mechanics as Page, but Spring
    //    Data fetches (size + 1) rows and uses the extra row purely to
    //    answer hasNext() -- no COUNT(*) query at all. Perfect for
    //    infinite-scroll / "Load more" UIs that never show a total.
    // ------------------------------------------------------------------
    @Query("SELECT p FROM Product p WHERE p.category = :category")
    Slice<Product> findByCategorySlice(@Param("category") String category, Pageable pageable);

    // ------------------------------------------------------------------
    // 3) SCROLL API / KEYSET PAGINATION (Spring Data 3.1+): instead of
    //    OFFSET n, the DB is given the last-seen (created_at, id) tuple
    //    and asked for rows strictly after it via a WHERE predicate that
    //    hits the (category, created_at, id) index directly. Performance
    //    stays flat regardless of how deep you scroll -- no OFFSET decay.
    //    Limit is dynamic (interview talking point: compare to hard-coded
    //    "findFirst50By..." naming-convention limits).
    // ------------------------------------------------------------------
    Window<Product> findByCategoryOrderByCreatedAtAscIdAsc(
            String category, Limit limit, ScrollPosition position);

    // ------------------------------------------------------------------
    // 4) CUSTOM @Query WITH EXPLICIT countQuery: when the derived/default
    //    count query would be inefficient or incorrect (e.g. it involves
    //    a JOIN, GROUP BY or DISTINCT that shouldn't be repeated for the
    //    count), you hand-write a cheaper, semantically-equivalent count.
    //    Sort is still applied dynamically via the incoming Pageable.
    // ------------------------------------------------------------------
    @Query(
            value = "SELECT p FROM Product p " +
                    "WHERE p.category = :category AND p.price BETWEEN :minPrice AND :maxPrice",
            countQuery = "SELECT COUNT(p) FROM Product p " +
                    "WHERE p.category = :category AND p.price BETWEEN :minPrice AND :maxPrice"
    )
    Page<Product> findByCategoryAndPriceRange(
            @Param("category") String category,
            @Param("minPrice") BigDecimal minPrice,
            @Param("maxPrice") BigDecimal maxPrice,
            Pageable pageable);

    // ------------------------------------------------------------------
    // 5) DYNAMIC FILTERING: no method needed here -- JpaSpecificationExecutor
    //    already contributes findAll(Specification<Product>, Pageable).
    //    See ProductSpecification for the composable predicate builders.
    // ------------------------------------------------------------------
}
