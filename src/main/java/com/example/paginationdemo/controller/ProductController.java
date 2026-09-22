package com.example.paginationdemo.controller;
 
import com.example.paginationdemo.aspect.ExecutionTimeHolder;
import com.example.paginationdemo.dto.ProductResponseDto;
import com.example.paginationdemo.dto.ScrollResponseDto;
import com.example.paginationdemo.service.ProductService;
import com.example.paginationdemo.util.CursorCodec;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.KeysetScrollPosition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Slice;
import org.springframework.data.domain.Window;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
 
import java.math.BigDecimal;
 
@RestController
@RequiredArgsConstructor
@Tag(name = "Products - Pagination Strategies", description = "Five pagination strategies, side by side")
public class ProductController {
 
    private static final String TIMING_HEADER = "X-Response-Time-Ms";
 
    private final ProductService productService;
 
    // ------------------------------------------------------------------
    // 1) Standard Page<T> -- offset pagination with total count
    // ------------------------------------------------------------------
    @GetMapping("/api/products/page")
    @Operation(
            summary = "Standard offset pagination (Page<T>)",
            description = "Classic page-number UI. Issues a SELECT plus a separate SELECT COUNT(*) " +
                    "so the client can render total pages / total items. Cost grows with OFFSET depth."
    )
    public ResponseEntity<Page<ProductResponseDto>> getProductsPage(
            @Parameter(description = "Category to filter by") @RequestParam String category,
            @ParameterObject Pageable pageable) {
 
        Page<ProductResponseDto> result = productService.getProductsPage(category, pageable);
        return withTimingHeader(result);
    }
 
    // ------------------------------------------------------------------
    // 2) Lightweight Slice<T> -- no COUNT(*), infinite scroll pattern
    // ------------------------------------------------------------------
    @GetMapping("/api/products/slice")
    @Operation(
            summary = "Lightweight infinite-scroll pagination (Slice<T>)",
            description = "Fetches size + 1 rows to derive hasNext() without ever running a COUNT(*) query. " +
                    "Ideal for 'Load more' / infinite-scroll UIs that don't need a total count."
    )
    public ResponseEntity<Slice<ProductResponseDto>> getProductsSlice(
            @Parameter(description = "Category to filter by") @RequestParam String category,
            @ParameterObject Pageable pageable) {
 
        Slice<ProductResponseDto> result = productService.getProductsSlice(category, pageable);
        return withTimingHeader(result);
    }
 
    // ------------------------------------------------------------------
    // 3) Scroll API / keyset pagination -- Window<T> + cursor
    // ------------------------------------------------------------------
    @GetMapping("/api/products/scroll")
    @Operation(
            summary = "Keyset / cursor pagination via the Scroll API (Window<T>)",
            description = "Spring Data 3.1+ Scroll API. Omit 'cursor' for the first page; " +
                    "pass back the 'nextCursor' from the previous response to continue. " +
                    "No OFFSET is ever used, so latency stays flat no matter how deep you scroll."
    )
    public ResponseEntity<ScrollResponseDto<ProductResponseDto>> getProductsScroll(
            @Parameter(description = "Category to filter by") @RequestParam String category,
            @Parameter(description = "Opaque cursor from a previous response's nextCursor field. Omit for page 1.")
            @RequestParam(required = false) String cursor,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size) {
 
        ScrollPosition position = (cursor == null || cursor.isBlank())
                ? ScrollPosition.keyset()
                : CursorCodec.decode(cursor);
 
        Window<ProductResponseDto> window = productService.getProductsWindow(category, position, size);
 
        String nextCursor = null;
        if (window.hasNext() && !window.isEmpty()) {
            ScrollPosition candidate = window.positionAt(window.getContent().size() - 1);
            if (candidate instanceof KeysetScrollPosition keysetPosition) {
                nextCursor = CursorCodec.encode(keysetPosition);
            }
        }
 
        ScrollResponseDto<ProductResponseDto> body =
                new ScrollResponseDto<>(window.getContent(), window.hasNext(), nextCursor);
 
        return withTimingHeader(body);
    }
 
    // ------------------------------------------------------------------
    // 4) Custom @Query with explicit countQuery + dynamic sort
    // ------------------------------------------------------------------
    @GetMapping("/api/products/query")
    @Operation(
            summary = "Custom JPQL query with an explicit countQuery override",
            description = "Filters by category and price range using a hand-written JPQL query and a " +
                    "hand-written, cheaper countQuery. Sort is still applied dynamically from the Pageable."
    )
    public ResponseEntity<Page<ProductResponseDto>> getProductsByCustomQuery(
            @Parameter(description = "Category to filter by") @RequestParam String category,
            @Parameter(description = "Minimum price (inclusive)") @RequestParam BigDecimal minPrice,
            @Parameter(description = "Maximum price (inclusive)") @RequestParam BigDecimal maxPrice,
            @ParameterObject Pageable pageable) {
 
        Page<ProductResponseDto> result =
                productService.getProductsByCustomQuery(category, minPrice, maxPrice, pageable);
        return withTimingHeader(result);
    }
 
    // ------------------------------------------------------------------
    // 5) Dynamic filtering with JpaSpecificationExecutor
    // ------------------------------------------------------------------
    @GetMapping("/api/products/filter")
    @Operation(
            summary = "Dynamic filtering + pagination (Specification API)",
            description = "Combines any subset of category / price range / name keyword filters, built as " +
                    "composable Specification<Product> predicates, with standard Pageable offset pagination."
    )
    public ResponseEntity<Page<ProductResponseDto>> getProductsFiltered(
            @Parameter(description = "Category to filter by (optional)") @RequestParam(required = false) String category,
            @Parameter(description = "Minimum price (optional)") @RequestParam(required = false) BigDecimal minPrice,
            @Parameter(description = "Maximum price (optional)") @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "Case-insensitive substring match on name (optional)") @RequestParam(required = false) String keyword,
            @ParameterObject Pageable pageable) {
 
        Page<ProductResponseDto> result =
                productService.getProductsFiltered(category, minPrice, maxPrice, keyword, pageable);
        return withTimingHeader(result);
    }
 
    /**
     * Reads the elapsed time the ExecutionTimeAspect stashed for this request's
     * service call and stamps it onto the response as X-Response-Time-Ms,
     * then clears the ThreadLocal so nothing leaks to the next request handled
     * by this pooled thread.
     */
    private <T> ResponseEntity<T> withTimingHeader(T body) {
        Long elapsedMs = ExecutionTimeHolder.get();
        ExecutionTimeHolder.clear();
        return ResponseEntity.ok()
                .header(TIMING_HEADER, String.valueOf(elapsedMs == null ? -1 : elapsedMs))
                .body(body);
    }
}