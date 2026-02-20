package tradingengine.analytics;

import java.time.Instant;

/**
 * Immutable analytics snapshot at one point in time.
 *
 * <p>This is the row shape we persist in {@code analytics.csv} and return from
 * {@code GET /api/analytics}.
 *
 * @param timestamp      snapshot creation time (UTC)
 * @param totalTrades    total number of trades seen so far
 * @param totalVolume    total traded quantity so far
 * @param avgTradePrice  weighted average trade price (0.0 when no trades)
 * @param bestBid        current best bid price, null when bid side is empty
 * @param bestAsk        current best ask price, null when ask side is empty
 * @param openOrders     total number of currently open orders in the book
 */
public record AnalyticsSnapshot(
        Instant timestamp,
        long totalTrades,
        long totalVolume,
        double avgTradePrice,
        Long bestBid,
        Long bestAsk,
        long openOrders
) {}
