package tradingengine.analytics;

import tradingengine.book.OrderBookSide;
import tradingengine.domain.Trade;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Transform step of the analytics pipeline.
 *
 * <p>Takes in-memory snapshots from the engine and computes one aggregate summary row.
 */
public final class AnalyticsCalculator {

    /** Utility class; no instances needed. */
    private AnalyticsCalculator() {
    }

    /**
     * Builds one analytics snapshot from current order book + trades.
     *
     * @param bids current bid levels in priority order (best first)
     * @param asks current ask levels in priority order (best first)
     * @param trades full trade history used for aggregate totals
     * @param timestamp snapshot timestamp supplied by caller
     * @return computed analytics summary
     */
    public static AnalyticsSnapshot compute(
            List<OrderBookSide.LevelSnapshot> bids,
            List<OrderBookSide.LevelSnapshot> asks,
            List<Trade> trades,
            Instant timestamp
    ) {
        Objects.requireNonNull(bids, "bids must not be null");
        Objects.requireNonNull(asks, "asks must not be null");
        Objects.requireNonNull(trades, "trades must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");

        // Basic trade aggregates.
        long totalTrades = trades.size();
        long totalVolume = 0L;
        double notional = 0.0;
        for (Trade trade : trades) {
            long qty = trade.quantity();
            totalVolume += qty;
            notional += ((double) trade.price()) * qty;
        }

        // Weighted average price = sum(price * qty) / sum(qty).
        double avgTradePrice = (totalVolume == 0L) ? 0.0 : (notional / (double) totalVolume);

        // Best prices come from the first level because snapshots are already sorted.
        Long bestBid = bids.isEmpty() ? null : bids.get(0).price();
        Long bestAsk = asks.isEmpty() ? null : asks.get(0).price();

        // Open order count is the sum of level counts across both sides.
        long openOrders = 0L;
        for (OrderBookSide.LevelSnapshot level : bids) {
            openOrders += level.count();
        }
        for (OrderBookSide.LevelSnapshot level : asks) {
            openOrders += level.count();
        }

        return new AnalyticsSnapshot(
                timestamp,
                totalTrades,
                totalVolume,
                avgTradePrice,
                bestBid,
                bestAsk,
                openOrders
        );
    }
}
