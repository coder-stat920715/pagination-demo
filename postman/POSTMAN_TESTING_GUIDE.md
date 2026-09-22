# Postman Testing Guide — Pagination Mastery Demo

Two files accompany this guide:
- `pagination-demo.postman_collection.json`
- `pagination-demo.postman_environment.json`

## 1. Setup

**1.1 Start the app**
```
cd pagination-demo
mvn spring-boot:run
```
Wait for the log line `DataSeeder: finished seeding 100000 records in ... ms.` before testing — hitting endpoints before seeding completes will just return empty/partial pages, not errors.

**1.2 Import into Postman**
1. Open Postman → **Import** (top left).
2. Drag in both JSON files, or select them via the file picker.
3. Confirm you now see:
   - A collection: **Spring Boot 3.x Pagination Mastery Demo**
   - An environment: **Pagination Demo - Local**

**1.3 Select the environment**
Top-right environment dropdown → choose **Pagination Demo - Local**. This sets `baseUrl = http://localhost:8080` and `category = ELECTRONICS`. Change `category` in the environment if you want to test a different one (valid values from the seeder: `ELECTRONICS, BOOKS, HOME_KITCHEN, SPORTS, TOYS, FASHION, GROCERY, AUTOMOTIVE, BEAUTY, GARDEN`).

**1.4 Sanity check**
Run **0. Health & Docs → Swagger UI**. Expect `200`. If you get a connection error, the app isn't up yet or is on a different port.

## 2. Folder-by-folder walkthrough

### Folder 1 — Offset Pagination (`Page<T>`)
| Request | What to check |
|---|---|
| 1.1 Page 0 (shallow) | Response body has `content`, `totalElements`, `totalPages`, `number`, `size`. Note the `X-Response-Time-Ms` header value (Postman → Response → Headers tab) — call it **T1**. |
| 1.2 Page 400 (deep) | Same shape, but note `X-Response-Time-Ms` again — call it **T2**. On H2 in-memory this difference may be small (few ms) since everything is RAM-resident; the important thing to *explain* is that on a real disk-backed Postgres/MySQL table, **T2 grows with offset depth** because the engine still has to walk/skip every row before it. Open the Postman Console (`View → Show Postman Console`) to see both logged automatically by the test script. |
| 1.3 size=0 | Documents how your app handles a degenerate page size — either Spring's default `Pageable` kicks in (200, size defaults to 20) or you get a 400. Either is a valid finding to discuss in an interview; just know which one your app does. |

**Interview talking point:** run 1.1 and 1.2 back-to-back, read the two `X-Response-Time-Ms` values out loud, then immediately run Folder 3 (keyset) to contrast.

### Folder 2 — Lightweight Pagination (`Slice<T>`)
| Request | What to check |
|---|---|
| 2.1 Slice page 0 | Body has `content` and `hasNext` — **no `totalElements` or `totalPages`**. The test script asserts this explicitly; a green check here is your proof there's no `COUNT(*)` in play. |
| 2.2 Slice page 1 | Confirms the next chunk continues where 2.1 left off (compare first `id` in 2.2's content to the last `id` in 2.1's content — should be adjacent). |

**Interview talking point:** toggle `org.hibernate.SQL: DEBUG` in `application.yml`, restart, and tail the console while running 1.1 vs 2.1 side by side — 1.1 logs two SELECTs (data + count), 2.1 logs one.

### Folder 3 — Keyset / Scroll Pagination (`Window<T>`)
This is the one that needs to run **in order** because request 3.2 depends on a cursor captured from 3.1.

1. Run **3.1 Initial scroll**. Its test script auto-captures `nextCursor` from the response body into the collection variable `nextCursor` — you'll see it logged in the Postman Console.
2. Run **3.2 Next scroll**. It uses `{{nextCursor}}` in the query string automatically. Its test script re-captures the *new* `nextCursor` for further chaining.
3. **Repeat 3.2 by hand 5–10 times** (just hit Send again each time — the script keeps re-capturing the cursor), watching `X-Response-Time-Ms` in the Postman Console. It should stay roughly flat across repetitions, unlike Folder 1's offset degradation.
4. Run **3.3 Bad cursor** to confirm malformed cursors fail loudly (400/500) instead of silently returning the wrong page — an important correctness property to call out live.

**Automating the "stays flat" proof:** select the **3. Keyset / Scroll Pagination** folder → **Run** (Collection Runner) → set iterations to 20 → Run. Because 3.2 keeps consuming and re-emitting `nextCursor`, this effectively deep-scrolls 20×20=400 rows in. Open the Runner's results and compare the `X-Response-Time-Ms` column across iterations — flat line vs. Folder 1's climb is your visual proof.

### Folder 4 — Custom `@Query` with explicit `countQuery`
| Request | What to check |
|---|---|
| 4.1 Price range 10–500 | Test script asserts every returned `price` is within `[10, 500]` — proves the JPQL predicate is correct, not just the count query. |
| 4.2 Narrow band 1999–2000 | Likely a small or empty `content` array with `totalElements` still accurate — proves the hand-written `countQuery` isn't just returning a stale/wrong number. |
| 4.3 Missing `minPrice` | Expect `400` since it's a required `@RequestParam`. Good moment to mention `@RequestParam(required = false)` vs required, and how you'd add `@Valid`/`@Min` constraints for more robust validation. |

### Folder 5 — Dynamic Filtering (`Specification<T>`)
| Request | What to check |
|---|---|
| 5.1 Category only | Baseline — same shape as `/page` since it's still `Page<T>` under the hood. |
| 5.2 Category + price range | Two predicates ANDed together. |
| 5.3 Keyword only, no category | Proves `hasCategory(null)` correctly returns `null` and gets skipped by `Specification.where(...).and(...)` rather than throwing or filtering everything out. |
| 5.4 All filters combined | Test script asserts every item matches **both** the category and the price band simultaneously. |
| 5.5 No filters at all | Degenerates to an unfiltered, paginated `SELECT * FROM products` — confirms the Specification composition doesn't break when everything is `null`. |

## 3. What "done" looks like

Run the whole collection top to bottom: **Collection Runner → select the collection root → Run**. All test assertions across all five folders should pass (green). If anything fails:
- **Connection refused** → app isn't running or wrong port in `baseUrl`.
- **Empty `content` everywhere** → seeder hasn't finished; check the startup logs.
- **3.2 fails / `cursor` param literally shows `{{nextCursor}}`** → 3.1 wasn't run first in the same environment; re-run 3.1, then 3.2.
- **400 on 4.3** → expected, that's the test case working correctly.

## 4. Live-demo sequencing (ties back to the interview guide)

For the actual interview, don't run every request — cherry-pick this order from the collection:
1. **1.1** → show `Page<T>` shape and timing.
2. **2.1** → show `Slice<T>` shape (no count) and timing, contrast with 1.1.
3. **1.2** then **3.1 → 3.2 (repeated a few times)** → the flat-vs-climbing timing contrast is the headline moment.
4. **4.1** → custom countQuery + dynamic sort.
5. **5.4** → composable Specification filtering.

Keep the Postman Console open (`View → Show Postman Console`, or `Alt/Opt+Ctrl+C`) throughout so the `X-Response-Time-Ms` `console.log` lines from each test script are visible to whoever's watching your screen.
