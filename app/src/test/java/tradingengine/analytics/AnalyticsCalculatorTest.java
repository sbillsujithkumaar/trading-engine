package tradingengine.analytics;

import org.junit.jupiter.api.Test;
import tradingengine.book.OrderBookSide;
import tradingengine.domain.Trade;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for analytics transformation logic.
 */
class AnalyticsCalculatorTest {

    // Rationale: Main happy-path check should validate weighted average, top-of-book,
    // and open-order counting in one deterministic scenario.
    @Test
    void computeAggregatesFromBookAndTrades() {
        List<OrderBookSide.LevelSnapshot> bids = List.of(
                level(101, 7, 2),
                level(100, 3, 1)
        );
        List<OrderBookSide.LevelSnapshot> asks = List.of(
                level(102, 4, 1)
        );
        List<Trade> trades = List.of(
                trade("b1", "s1", 100, 5, "2026-01-01T00:00:00Z"),
                trade("b2", "s2", 101, 3, "2026-01-01T00:00:01Z")
        );

        Instant ts = Instant.parse("2026-01-01T01:00:00Z");
        AnalyticsSnapshot snapshot = AnalyticsCalculator.compute(bids, asks, trades, ts);

        assertEquals(ts, snapshot.timestamp());
        assertEquals(2L, snapshot.totalTrades());
        assertEquals(8L, snapshot.totalVolume());
        assertEquals(100.375, snapshot.avgTradePrice(), 1e-9);
        assertEquals(101L, snapshot.bestBid());
        assertEquals(102L, snapshot.bestAsk());
        assertEquals(4L, snapshot.openOrders());
    }

    // Rationale: Empty inputs are a core edge case; analytics should return safe defaults
    // instead of throwing or producing invalid values.
    @Test
    void emptyTradesAndBookProducesZerosAndNullTopOfBook() {
        Instant ts = Instant.parse("2026-01-01T02:00:00Z");
        AnalyticsSnapshot snapshot = AnalyticsCalculator.compute(List.of(), List.of(), List.of(), ts);

        assertEquals(ts, snapshot.timestamp());
        assertEquals(0L, snapshot.totalTrades());
        assertEquals(0L, snapshot.totalVolume());
        assertEquals(0.0, snapshot.avgTradePrice(), 0.0);
        assertNull(snapshot.bestBid());
        assertNull(snapshot.bestAsk());
        assertEquals(0L, snapshot.openOrders());
    }

    // Rationale: openOrders is defined as number of orders, so we assert it is derived
    // from level counts and not from quantity totals.
    @Test
    void openOrdersComesFromLevelCountsAcrossBothSides() {
        List<OrderBookSide.LevelSnapshot> bids = List.of(
                level(101, 9, 3),
                level(100, 5, 2)
        );
        List<OrderBookSide.LevelSnapshot> asks = List.of(
                level(102, 1, 1)
        );

        AnalyticsSnapshot snapshot = AnalyticsCalculator.compute(
                bids,
                asks,
                List.of(),
                Instant.parse("2026-01-01T03:00:00Z")
        );

        assertEquals(6L, snapshot.openOrders());
    }

    // Helper for compact level-snapshot test setup.
    private static OrderBookSide.LevelSnapshot level(long price, long qty, int count) {
        return new OrderBookSide.LevelSnapshot(price, qty, count, List.of());
    }

    // Helper for compact trade test setup.
    private static Trade trade(String buyId, String sellId, long price, long qty, String ts) {
        return new Trade(buyId, sellId, price, qty, Instant.parse(ts));
    }
}
