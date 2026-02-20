package tradingengine.analytics;

import tradingengine.book.OrderBookSide;
import tradingengine.domain.Trade;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * ETL runner for analytics.
 *
 * <p>On each run:
 * 1) Extract snapshots from the engine
 * 2) Transform into aggregate metrics
 * 3) Load the result into analytics.csv
 */
public final class AnalyticsJob {

    private final AnalyticsStore store;
    private final SnapshotProvider snapshotProvider;
    private final Clock clock;

    /**
     * @param store analytics persistence layer
     * @param snapshotProvider source of current book/trade snapshots
     * @param clock clock used to timestamp generated snapshots
     */
    public AnalyticsJob(AnalyticsStore store, SnapshotProvider snapshotProvider, Clock clock) {
        this.store = Objects.requireNonNull(store, "store must not be null");
        this.snapshotProvider = Objects.requireNonNull(snapshotProvider, "snapshotProvider must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * Executes one ETL cycle.
     */
    public void runOnce() throws IOException {
        // Extract current engine state.
        List<OrderBookSide.LevelSnapshot> bids = snapshotProvider.currentBids();
        List<OrderBookSide.LevelSnapshot> asks = snapshotProvider.currentAsks();
        List<Trade> trades = snapshotProvider.currentTrades();

        // Transform + load.
        AnalyticsSnapshot snapshot = AnalyticsCalculator.compute(
                bids,
                asks,
                trades,
                Instant.now(clock)
        );
        store.writeLatest(snapshot);
    }

    /**
     * Small interface so analytics does not depend directly on servlet classes.
     */
    public interface SnapshotProvider {
        /** @return current bid levels in best-price order */
        List<OrderBookSide.LevelSnapshot> currentBids();

        /** @return current ask levels in best-price order */
        List<OrderBookSide.LevelSnapshot> currentAsks();

        /** @return current trade history snapshot */
        List<Trade> currentTrades();
    }
}
