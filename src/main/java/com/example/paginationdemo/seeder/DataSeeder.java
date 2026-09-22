package com.example.paginationdemo.seeder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Seeds 100,000 synthetic Product rows on startup using raw JdbcTemplate
 * batch inserts -- deliberately bypassing the JPA persistence context/
 * EntityManager, which would otherwise blow up heap usage and slow to a
 * crawl flushing/dirty-checking 100k managed entities. This is the same
 * technique you'd reach for in a real bulk-load job.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    private static final List<String> CATEGORIES = List.of(
            "ELECTRONICS", "BOOKS", "HOME_KITCHEN", "SPORTS", "TOYS",
            "FASHION", "GROCERY", "AUTOMOTIVE", "BEAUTY", "GARDEN"
    );

    private static final int TOTAL_RECORDS = 100_000;
    private static final int BATCH_SIZE = 1_000;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Long existing = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Long.class);
        if (existing != null && existing > 0) {
            log.info("DataSeeder: {} products already present, skipping seed.", existing);
            return;
        }

        log.info("DataSeeder: seeding {} products in batches of {}...", TOTAL_RECORDS, BATCH_SIZE);
        long startedAt = System.currentTimeMillis();

        final String sql = "INSERT INTO products (sku, name, category, price, created_at) VALUES (?, ?, ?, ?, ?)";
        final Instant baseTime = Instant.now().minus(365, ChronoUnit.DAYS);
        final int totalBatches = TOTAL_RECORDS / BATCH_SIZE;

        for (int batch = 0; batch < totalBatches; batch++) {
            final int batchOffset = batch * BATCH_SIZE;

            jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    int recordIndex = batchOffset + i;
                    ThreadLocalRandom random = ThreadLocalRandom.current();

                    String category = CATEGORIES.get(random.nextInt(CATEGORIES.size()));
                    String sku = "SKU-" + String.format("%08d", recordIndex);
                    String name = category + " Product #" + recordIndex;
                    BigDecimal price = BigDecimal.valueOf(random.nextDouble(5.0, 2000.0))
                            .setScale(2, RoundingMode.HALF_UP);
                    // Spread creation timestamps out so ORDER BY created_at produces
                    // a realistic, evenly-distributed keyset for the scroll demo.
                    Instant createdAt = baseTime.plusSeconds(recordIndex * 30L);

                    ps.setString(1, sku);
                    ps.setString(2, name);
                    ps.setString(3, category);
                    ps.setBigDecimal(4, price);
                    ps.setTimestamp(5, Timestamp.from(createdAt));
                }

                @Override
                public int getBatchSize() {
                    return BATCH_SIZE;
                }
            });

            if (batch % 20 == 0) {
                log.info("DataSeeder: {} / {} records inserted...", (batch + 1) * BATCH_SIZE, TOTAL_RECORDS);
            }
        }

        long elapsedMs = System.currentTimeMillis() - startedAt;
        log.info("DataSeeder: finished seeding {} records in {} ms.", TOTAL_RECORDS, elapsedMs);
    }
}
