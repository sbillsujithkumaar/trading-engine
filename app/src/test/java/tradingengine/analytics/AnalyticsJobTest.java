package tradingengine.analytics;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tradingengine.book.OrderBookSide;
import tradingengine.domain.Trade;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for one-cycle ETL execution in AnalyticsJob.
 */
class AnalyticsJobTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-01-01T04:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    // Rationale: The job should correctly orchestrate one full ETL cycle
    // (extract -> transform -> load) and persist expected analytics values.
    @Test
    void runOnceExtractsTransformsAndPersistsSnapshot() throws IOException {
        Path csvPath = tempDir.resolve("analytics.csv");
        AnalyticsStore store = new AnalyticsStore(csvPath);

        AnalyticsJob.SnapshotProvider provider = new AnalyticsJob.SnapshotProvider() {
            @Override
            // Eclipse infers @NonNull element types from List.of(...); suppress widening warning
            // when returning to the interface's unannotated generic signature.
            @SuppressWarnings("null")
            public List<OrderBookSide.LevelSnapshot> currentBids() {
                return List.of(new OrderBookSide.LevelSnapshot(100, 7, 2, List.of()));
            }

            @Override
            // Eclipse infers @NonNull element types from List.of(...); suppress widening warning
            // when returning to the interface's unannotated generic signature.
            @SuppressWarnings("null")
            public List<OrderBookSide.LevelSnapshot> currentAsks() {
                return List.of(new OrderBookSide.LevelSnapshot(101, 3, 1, List.of()));
            }

            @Override
            // Eclipse infers @NonNull element types from List.of(...); suppress widening warning
            // when returning to the interface's unannotated generic signature.
            @SuppressWarnings("null")
            public List<Trade> currentTrades() {
                return List.of(
                        new Trade("b1", "s1", 100, 5, Instant.parse("2026-01-01T03:59:00Z")),
                        new Trade("b2", "s2", 102, 5, Instant.parse("2026-01-01T03:59:30Z"))
                );
            }
        };

        AnalyticsJob job = new AnalyticsJob(store, provider, FIXED_CLOCK);
        job.runOnce();

        List<String> lines = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        assertEquals(2, lines.size());

        String[] cols = lines.get(1).split(",", -1);
        assertEquals("2026-01-01T04:00:00Z", cols[0]);
        assertEquals("2", cols[1]);
        assertEquals("10", cols[2]);
        assertEquals("101.00000", cols[3]);
        assertEquals("100", cols[4]);
        assertEquals("101", cols[5]);
        assertEquals("3", cols[6]);
    }
}
