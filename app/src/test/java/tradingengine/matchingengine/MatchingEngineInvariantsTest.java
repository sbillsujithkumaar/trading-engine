package tradingengine.matchingengine;

import org.junit.jupiter.api.Test;
import tradingengine.book.OrderBook;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;
import tradingengine.domain.Trade;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Invariant checks that must always hold across matches.
class MatchingEngineInvariantsTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private static Order order(OrderSide side, long price, long quantity) {
        return new Order(side, price, quantity, Instant.now(FIXED_CLOCK));
    }

    // Ensures no trade is emitted with a non-positive quantity.
    @Test
    void noTradeHasNonPositiveQuantity() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

        engine.submit(order(OrderSide.SELL, 100, 5));
        List<Trade> trades = engine.submit(order(OrderSide.BUY, 100, 5));

        trades.forEach(trade -> assertTrue(trade.quantity() > 0));
    }

    // Ensures order quantities never drop below zero after matching.
    @Test
    void remainingQuantityNeverNegative() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

        Order sell = order(OrderSide.SELL, 100, 5);
        engine.submit(sell);

        Order buy = order(OrderSide.BUY, 100, 10);
        engine.submit(buy);

        assertTrue(buy.getRemainingQty() >= 0);
        assertTrue(sell.getRemainingQty() >= 0);
    }

    // Ensures total executed quantity never exceeds submitted quantity.
    @Test
    void totalExecutedNeverExceedsSubmitted() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

        long submittedQty = 0;
        long executedQty = 0;

        Order sell = order(OrderSide.SELL, 100, 10);
        submittedQty += 10;
        engine.submit(sell);

        List<Trade> trades = engine.submit(order(OrderSide.BUY, 100, 7));
        submittedQty += 7;

        for (Trade trade : trades) {
            executedQty += trade.quantity();
        }

        assertTrue(executedQty <= submittedQty);
    }
}
