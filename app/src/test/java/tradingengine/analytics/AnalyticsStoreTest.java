package tradingengine.analytics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for analytics CSV persistence behavior.
 */
class AnalyticsStoreTest {

    private static final String HEADER = "timestamp,totalTrades,totalVolume,avgTradePrice,bestBid,bestAsk,openOrders";

    @TempDir
    Path tempDir;

    // Rationale: Writer should emit a stable CSV contract (header + one row) so downstream
    // consumers can parse fields deterministically.
    @Test
    void writeLatestCreatesSingleRowCsv() throws IOException {
        Path csvPath = tempDir.resolve("analytics.csv");
        AnalyticsStore store = new AnalyticsStore(csvPath);

        AnalyticsSnapshot snapshot = new AnalyticsSnapshot(
                Instant.parse("2026-01-01T00:00:00Z"),
                2L,
                8L,
                100.375,
                101L,
                102L,
                4L
        );

        store.writeLatest(snapshot);

        List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());
        assertEquals(HEADER, lines.get(0));

        String[] cols = lines.get(1).split(",", -1);
        assertEquals(7, cols.length);
        assertEquals("2026-01-01T00:00:00Z", cols[0]);
        assertEquals("2", cols[1]);
        assertEquals("8", cols[2]);
        assertEquals("100.37500", cols[3]);
        assertEquals("101", cols[4]);
        assertEquals("102", cols[5]);
        assertEquals("4", cols[6]);
    }

    // Rationale: Empty book sides must remain semantically empty in CSV (blank field), not
    // be converted to misleading numeric defaults like 0.
    @Test
    void nullableTopOfBookIsWrittenAsEmptyCells() throws IOException {
        Path csvPath = tempDir.resolve("analytics.csv");
        AnalyticsStore store = new AnalyticsStore(csvPath);

        store.writeLatest(new AnalyticsSnapshot(
                Instant.parse("2026-01-01T00:00:00Z"),
                0L,
                0L,
                0.0,
                null,
                null,
                0L
        ));

        List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        String[] cols = lines.get(1).split(",", -1);
        assertEquals("", cols[4]);
        assertEquals("", cols[5]);
    }

    // Rationale: API layer needs a clear "not available yet" signal when no snapshot file
    // exists, so this should return Optional.empty().
    @Test
    void readLatestCsvReturnsEmptyWhenFileDoesNotExist() {
        AnalyticsStore store = new AnalyticsStore(tempDir.resolve("missing.csv"));
        assertTrue(store.readLatestCsv().isEmpty());
    }

    // Rationale: Snapshot file should represent only the latest analytics state, so each
    // write must replace prior content rather than append history rows.
    @Test
    void rewriteReplacesPreviousSnapshot() throws IOException {
        Path csvPath = tempDir.resolve("analytics.csv");
        AnalyticsStore store = new AnalyticsStore(csvPath);

        AnalyticsSnapshot first = new AnalyticsSnapshot(
                Instant.parse("2026-01-01T00:00:00Z"),
                1L,
                5L,
                100.0,
                100L,
                101L,
                1L
        );
        AnalyticsSnapshot second = new AnalyticsSnapshot(
                Instant.parse("2026-01-01T00:00:30Z"),
                2L,
                10L,
                101.0,
                101L,
                102L,
                2L
        );

        store.writeLatest(first);
        store.writeLatest(second);

        String csv = store.readLatestCsv().orElseThrow();
        assertFalse(csv.contains("2026-01-01T00:00:00Z"));
        assertTrue(csv.contains("2026-01-01T00:00:30Z"));
    }
}
