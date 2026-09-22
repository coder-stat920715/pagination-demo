# Pagination Mastery — Spring Boot 3.x

A single Spring Boot 3.3 / Java 21 application demonstrating **five distinct pagination strategies** side by side, on a 100,000-row H2 dataset, with AOP-based timing instrumentation so you can *measure* — not just describe — the trade-offs between them.

Built as a live-coding reference for Senior Java Engineer interviews focused on pagination, sorting, and query performance.

```
GET /api/products/page     -> Page<T>    offset pagination, with COUNT(*)
GET /api/products/slice    -> Slice<T>   offset pagination, no COUNT(*)
GET /api/products/scroll   -> Window<T>  keyset/cursor pagination (Spring Data 3.1+ Scroll API)
GET /api/products/query    -> Page<T>    custom @Query with explicit countQuery
GET /api/products/filter   -> Page<T>    dynamic filtering via Specification<T>
```

---

## Table of contents

- [Tech stack](#tech-stack)
- [Project structure](#project-structure)
- [Quick start](#quick-start)
- [Domain model & indexing](#domain-model--indexing)
- [Pagination in Spring Data JPA — the mental model](#pagination-in-spring-data-jpa--the-mental-model)
- [Deep dive: `Page<T>`](#deep-dive-paget)
- [Deep dive: `Slice<T>`](#deep-dive-slicet)
- [Deep dive: `Window<T>` (keyset / Scroll API)](#deep-dive-windowt-keyset--scroll-api)
- [Page vs Slice vs Window — decision table](#page-vs-slice-vs-window--decision-table)
- [Custom `@Query` with explicit `countQuery`](#custom-query-with-explicit-countquery)
- [Dynamic filtering with `Specification<T>`](#dynamic-filtering-with-specificationt)
- [Performance instrumentation (AOP)](#performance-instrumentation-aop)
- [API reference](#api-reference)
- [Testing](#testing)
- [Interview talking points / likely follow-ups](#interview-talking-points--likely-follow-ups)

---

## Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 3.3.x |
| Persistence | Spring Data JPA (Hibernate) |
| Database | H2, in-memory |
| API docs | springdoc-openapi (Swagger UI) |
| Boilerplate reduction | Lombok |
| Bulk data loading | `JdbcTemplate` batch inserts |
| Instrumentation | Spring AOP (`@Aspect`) |

## Project structure

```
pagination-demo/
├── pom.xml
├── postman/                              # Postman collection + environment
└── src/main/java/com/example/paginationdemo/
    ├── PaginationDemoApplication.java
    ├── entity/Product.java               # @Table with composite index
    ├── dto/
    │   ├── ProductResponseDto.java       # record, never expose the entity
    │   └── ScrollResponseDto.java        # flattened Window<T> wire format
    ├── repository/ProductRepository.java # Page / Slice / Window / custom countQuery methods
    ├── specification/ProductSpecification.java
    ├── service/
    │   ├── ProductService.java
    │   └── ProductServiceImpl.java       # .map() directly on Page/Slice/Window
    ├── controller/ProductController.java # 5 endpoints, OpenAPI-annotated
    ├── aspect/
    │   ├── ExecutionTimeAspect.java      # @Around timing on the service layer
    │   └── ExecutionTimeHolder.java      # ThreadLocal bridge to response headers
    ├── seeder/DataSeeder.java            # 100k-row batch seeder
    ├── util/CursorCodec.java             # opaque base64 keyset cursor encode/decode
    └── config/OpenApiConfig.java
```

## Quick start

```bash
git clone https://github.com/coder-stat920715/pagination-demo.git
cd pagination-demo
mvn spring-boot:run
```

Wait for:
```
DataSeeder: finished seeding 100000 records in ... ms.
```

Then:
- **Swagger UI:** http://localhost:8080/swagger-ui.html
- **H2 console:** http://localhost:8080/h2-console — JDBC URL `jdbc:h2:mem:paginationdb`, user `sa`, blank password
- **Postman:** import `postman/pagination-demo.postman_collection.json` and `postman/pagination-demo.postman_environment.json`

Categories seeded (use any as `?category=`): `ELECTRONICS, BOOKS, HOME_KITCHEN, SPORTS, TOYS, FASHION, GROCERY, AUTOMOTIVE, BEAUTY, GARDEN`.

## Domain model & indexing

```java
@Entity
@Table(name = "products", indexes = {
    @Index(name = "idx_category_created_id", columnList = "category, created_at, id"),
    @Index(name = "idx_sku", columnList = "sku", unique = true)
})
public class Product {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String sku;
    private String name;
    private String category;
    private BigDecimal price;
    private Instant createdAt;
}
```

The composite index on **`(category, created_at, id)`** is the single most important design decision in this project. It's built specifically to back the query shape used everywhere in this app:

```sql
WHERE category = ? ORDER BY created_at ASC, id ASC
```

This shape shows up in the `Page`, `Slice`, and `Window` queries alike — the filter column leads the index, the sort columns follow it in the same order the `ORDER BY` uses, and `id` is appended as a **tiebreaker** for rows sharing the same `created_at`. That tiebreaker is what makes keyset pagination (below) correct and stable, not just fast.

## Pagination in Spring Data JPA — the mental model

Before comparing the three return types, it helps to separate two orthogonal concerns that people often conflate in interviews:

1. **How do you skip to where you want to be?**
   - **Offset-based**: "skip N rows, then take the next page-size rows" (`OFFSET n LIMIT size`). Simple, supports random access ("jump to page 40"), but the database still has to *locate and discard* every skipped row — cost grows with `n`.
   - **Keyset-based** (a.k.a. cursor / seek pagination): "give me rows that come after this specific row" (`WHERE (created_at, id) > (?, ?) LIMIT size`). The database seeks directly via the index — cost stays flat regardless of how deep you are, but you lose random-access page jumping.

2. **Do you need to know the total count?**
   - If yes, you pay for a `SELECT COUNT(*)` (or an approximation) on top of whichever skip strategy you picked.
   - If no — e.g. an infinite-scroll feed — skip it entirely and save that query.

`Page<T>`, `Slice<T>`, and `Window<T>` are Spring Data's three answers to combining these two concerns:

| Return type | Skip strategy | Count query? |
|---|---|---|
| `Page<T>` | Offset | Yes |
| `Slice<T>` | Offset | No |
| `Window<T>` | Keyset | No (not meaningful for keyset) |

---

## Deep dive: `Page<T>`

**Repository:**
```java
Page<Product> findByCategory(String category, Pageable pageable);
```

**What Spring Data does under the hood:** issues **two** queries —
```sql
SELECT * FROM products WHERE category = ? ORDER BY ... LIMIT ? OFFSET ?;
SELECT COUNT(*) FROM products WHERE category = ?;
```

**What you get back:** `content`, `totalElements`, `totalPages`, `number` (current page), `size`, `first`/`last` flags — everything a classic "page 1 of 47, [1] [2] [3] ... [47]" UI needs.

**The cost model:**
- The `COUNT(*)` query scans (or index-scans) the entire matching row set every single call, even though the count rarely changes between consecutive requests.
- The data query's `OFFSET n` cost is engine-dependent, but broadly: the further into the result set you page, the more rows the engine has to skip over before it can start returning results. Page 1 and page 4000 do not cost the same.

**When to reach for it:** admin dashboards, search results with page numbers, anywhere the UI needs to say "showing 41–60 of 1,204" or render clickable page links. The UX requirement (show a total, allow jumping to an arbitrary page) is what justifies the cost — don't use `Page<T>` just because it's the default/most familiar option.

## Deep dive: `Slice<T>`

**Repository:**
```java
@Query("SELECT p FROM Product p WHERE p.category = :category")
Slice<Product> findByCategorySlice(@Param("category") String category, Pageable pageable);
```

**What Spring Data does under the hood:** issues **one** query, but asks for **`size + 1`** rows instead of `size`:
```sql
SELECT * FROM products WHERE category = ? ORDER BY ... LIMIT (size + 1) OFFSET (page * size);
```
If it gets back `size + 1` rows, it trims the extra one and sets `hasNext = true`. If it gets back `size` or fewer, `hasNext = false`. That single extra row is the entire trick — no count query needed to know whether more data exists.

**What you get back:** `content` and `hasNext` — no `totalElements`, no `totalPages`.

**The cost model:** still offset-based under the hood, so it still has the same `OFFSET n` skip cost as `Page<T>` at depth — the win here is purely **removing the count query**, not removing offset decay. Don't confuse "lighter than Page" with "immune to deep-paging cost."

**When to reach for it:** infinite-scroll feeds, "Load more" buttons, mobile list views — any UI that only ever needs to know "is there more?" and never needs to render a total or a page-number widget.

## Deep dive: `Window<T>` (keyset / Scroll API)

**Repository:**
```java
Window<Product> findByCategoryOrderByCreatedAtAscIdAsc(
    String category, Limit limit, ScrollPosition position);
```

**What Spring Data does under the hood:** translates the `ScrollPosition` into a `WHERE` predicate on the sort columns instead of an `OFFSET`:
```sql
-- first page: no predicate, just take the first N in order
SELECT * FROM products WHERE category = ? ORDER BY created_at ASC, id ASC LIMIT ?;

-- subsequent pages: seek past the last row you saw
SELECT * FROM products
WHERE category = ? AND (created_at, id) > (?, ?)
ORDER BY created_at ASC, id ASC
LIMIT ?;
```
That `(created_at, id) > (?, ?)` predicate is a **keyset predicate**, and it's answerable directly by the `(category, created_at, id)` composite index via an index seek — no skipping, no counting.

**How the cursor works in this project:** `Window<T>` isn't itself a clean JSON wire format, so `ProductController` flattens it into a `ScrollResponseDto<T>` (`content`, `hasNext`, `nextCursor`). The `nextCursor` is produced by `CursorCodec`, which takes the `KeysetScrollPosition` of the last row in the window (its `created_at` + `id` values) and base64-encodes them into an opaque string. The client stores that string and echoes it back as `?cursor=...` on the next call; `CursorCodec.decode` turns it back into a `ScrollPosition` Spring Data can consume. Keeping it opaque means the client never has to know (or be able to tamper with) which columns the keyset is actually built on.

**What you get back:** `content` and `hasNext` (via our flattening), plus an opaque cursor to continue from — **no total count, and no `OFFSET` anywhere in the executed SQL.**

**The cost model:** flat. Page 1 and "page 4000" (i.e., the 4000th consecutive `scroll` call) cost essentially the same, because both are index seeks, not scans-with-skip.

**The trade-off:** you give up random access. There's no way to ask a keyset query for "page 40" directly — you can only walk forward (or backward) from a known cursor. If your UI needs a page-number widget, keyset pagination is the wrong tool no matter how much faster it is.

**When to reach for it:** high-volume feeds, APIs consumed by other services (not humans clicking page links), any dataset large/volatile enough that deep offset pagination becomes a real, measurable latency or database-load problem.

---

## Page vs Slice vs Window — decision table

| Question | Page<T> | Slice<T> | Window<T> |
|---|---|---|---|
| Need a total count / "of N" in the UI? | ✅ yes | ❌ no | ❌ no |
| Need to jump to an arbitrary page number? | ✅ yes | ✅ yes (still offset-based) | ❌ no — sequential only |
| Dataset large/volatile and deep-paging performance matters? | ⚠️ degrades with depth | ⚠️ degrades with depth (data query) | ✅ flat regardless of depth |
| Extra query per request? | `COUNT(*)` every call | none | none |
| Typical UI | Admin table, search results with page numbers | Infinite scroll / load more | Infinite feed, API-to-API pagination, activity/audit logs |
| Underlying SQL mechanism | `OFFSET n LIMIT size` + `COUNT(*)` | `OFFSET n LIMIT size+1` | `WHERE (sortCols) > (?, ?) LIMIT size` |

**The one-sentence version for an interview:** *"`Page` and `Slice` both use `OFFSET` and differ only in whether they also run a `COUNT(*)`; `Window` throws out `OFFSET` entirely in favor of a keyset seek, trading away random-access page jumps for pagination performance that doesn't degrade with depth."*

---

## Custom `@Query` with explicit `countQuery`

**Repository:**
```java
@Query(
    value = "SELECT p FROM Product p WHERE p.category = :category AND p.price BETWEEN :minPrice AND :maxPrice",
    countQuery = "SELECT COUNT(p) FROM Product p WHERE p.category = :category AND p.price BETWEEN :minPrice AND :maxPrice"
)
Page<Product> findByCategoryAndPriceRange(
    @Param("category") String category,
    @Param("minPrice") BigDecimal minPrice,
    @Param("maxPrice") BigDecimal maxPrice,
    Pageable pageable);
```

By default, when a `@Query`-annotated method returns `Page<T>`, Spring Data derives a count query by wrapping your query in `SELECT COUNT(*) FROM (...)`. That derivation breaks down or becomes needlessly expensive once the main query involves a `JOIN`, `DISTINCT`, or `GROUP BY` — the wrapped count would still pay for the join/grouping work just to produce a number. The `countQuery` attribute lets you hand-write a cheaper, semantically-equivalent count query instead.

**Caveat worth raising proactively in an interview:** writing the predicate twice (once in `value`, once in `countQuery`) is a real maintenance risk — if someone edits one and forgets the other, the count and the data silently drift out of sync. In production code, a shared JPQL fragment constant, a QueryDSL/Criteria-based builder, or a repository-level integration test asserting `count == content.size()` on a small dataset are all reasonable mitigations.

Sort is still applied dynamically via the incoming `Pageable`'s `Sort` — the `@Query` only fixes the `WHERE` clause, not the `ORDER BY`.

## Dynamic filtering with `Specification<T>`

**Repository:** no method needed — `JpaSpecificationExecutor<Product>` already contributes `findAll(Specification<T>, Pageable)`.

**Predicate builders:**
```java
public static Specification<Product> hasCategory(String category) {
    return (root, query, cb) ->
        (category == null || category.isBlank()) ? null : cb.equal(root.get("category"), category);
}
```

Each builder returns `null` when its corresponding filter wasn't supplied. `Specification.where(...).and(...)` treats a `null` predicate as "skip this condition" rather than throwing or matching nothing, so any subset of `category` / `minPrice` / `maxPrice` / `keyword` can be present or absent on a given request and the resulting SQL only contains the `WHERE` clauses that actually apply:

```java
public static Specification<Product> build(String category, BigDecimal minPrice, BigDecimal maxPrice, String keyword) {
    return Specification.where(hasCategory(category))
            .and(priceGreaterThanOrEqual(minPrice))
            .and(priceLessThanOrEqual(maxPrice))
            .and(nameContains(keyword));
}
```

This is standard offset pagination (`Page<T>`) underneath — Specifications solve *what gets filtered*, not *how the skip happens*. You could equally combine `Specification` with a `Slice<T>` repository method if you didn't need the count.

---

## Performance instrumentation (AOP)

```java
@Aspect
@Component
public class ExecutionTimeAspect {
    @Around("execution(* com.example.paginationdemo.service..*(..))")
    public Object logExecutionTime(ProceedingJoinPoint joinPoint) throws Throwable {
        StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        Object result = joinPoint.proceed();
        stopWatch.stop();
        // strategy inferred from method name, logged, and stashed for the response header
    }
}
```

One `@Around` advice wraps every `ProductService` method. It:
1. Times the call with `StopWatch`.
2. Infers a strategy label (`OFFSET_WITH_COUNT`, `SLICE_NO_COUNT`, `KEYSET_CURSOR`, `CUSTOM_COUNT_QUERY`, `SPECIFICATION`) from the method name.
3. Logs `[PAGINATION-BENCHMARK] <method> | strategy=<X> | <ms> ms`.
4. Stores the elapsed milliseconds in a `ThreadLocal` (`ExecutionTimeHolder`).

The controller reads that `ThreadLocal` after the service call returns and stamps it onto the response as an `X-Response-Time-Ms` header, then clears it — so nothing leaks across requests on a pooled Tomcat thread. This means every response from this API is self-reporting its own service-layer latency, which is what makes the "deep offset vs. flat keyset" comparison something you can *show*, not just assert.

---

## API reference

| Method & path | Returns | Purpose |
|---|---|---|
| `GET /api/products/page?category=&page=&size=&sort=` | `Page<ProductResponseDto>` | Offset + count |
| `GET /api/products/slice?category=&page=&size=` | `Slice<ProductResponseDto>` | Offset, no count |
| `GET /api/products/scroll?category=&cursor=&size=` | `ScrollResponseDto<ProductResponseDto>` | Keyset/cursor scroll |
| `GET /api/products/query?category=&minPrice=&maxPrice=&page=&size=&sort=` | `Page<ProductResponseDto>` | Custom `@Query` + `countQuery` |
| `GET /api/products/filter?category=&minPrice=&maxPrice=&keyword=&page=&size=` | `Page<ProductResponseDto>` | Dynamic `Specification` filtering |

Full interactive docs (request/response schemas, try-it-out): `/swagger-ui.html`.

## Testing

A Postman collection covering all five endpoints — including a request-chaining setup that auto-captures the keyset cursor between calls so you can repeatedly "scroll" and watch `X-Response-Time-Ms` stay flat — lives in `postman/`. Import both the collection and environment file, point `baseUrl` at your running instance, and see `postman/README` (or the collection's folder descriptions) for the recommended run order.

## Interview talking points / likely follow-ups

- **"Why does `OFFSET` get slower as it gets deeper?"** The engine has to locate and skip every row before the offset, even though none of them are returned — cost is roughly proportional to `offset + limit`, not just `limit`.
- **"Why is `id` part of the sort/index even though we sort by `created_at`?"** `created_at` alone isn't guaranteed unique; without a tiebreaker, keyset pagination can skip or repeat rows that share a timestamp. `id` makes the sort — and therefore the keyset — deterministic.
- **"Can keyset pagination jump to page 40?"** No — that's the fundamental trade-off for its flat performance. It only supports sequential forward/backward traversal from a known cursor.
- **"Why hand-write a `countQuery` instead of trusting the derived one?"** Once the main query has a `JOIN`, `DISTINCT`, or `GROUP BY`, Spring Data's auto-derived count wraps and re-executes that same expensive shape just to produce a number — a hand-written count query can skip the join/grouping entirely if the count doesn't actually depend on it.
- **"How would you avoid `COUNT(*)` becoming a bottleneck at real scale?"** Options worth naming: cache the count with a short TTL, use an approximate count from database statistics (e.g., Postgres `pg_class.reltuples`), or redesign the UI around `Slice`/`Window` so you never need an exact total in the first place.
- **"Why does the seeder use raw `JdbcTemplate` instead of `repository.saveAll()`?"** Loading 100k managed entities into the JPA persistence context would blow up heap usage and dirty-checking cost; `JdbcTemplate.batchUpdate` writes directly via JDBC batching, bypassing the persistence context entirely — the standard technique for real bulk-load jobs.
