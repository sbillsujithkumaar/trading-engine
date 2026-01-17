package tradingengine.matchingengine;

import org.junit.jupiter.api.Test;
import tradingengine.book.OrderBook;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;
import tradingengine.domain.Trade;
import tradingengine.events.EventDispatcher;
import tradingengine.persistence.FileTradeStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Integration tests for cancellation behavior in the matching flow.
class MatchingEngineCancelIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private static FileTradeStore tradeStore() {
        try {
            return new FileTradeStore(Files.createTempFile("trades", ".csv"));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Order order(OrderSide side, long price, long quantity) {
        return new Order(side, price, quantity, Instant.now(FIXED_CLOCK));
    }

    // Ensures cancel before any match prevents trades.
    @Test
    void cancelBeforeMatchProducesNoTrades() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, new EventDispatcher(), tradeStore());

        Order buy = order(OrderSide.BUY, 100, 10);
        engine.submit(buy);

        assertTrue(engine.cancel(buy.getId()));

        List<Trade> trades = engine.submit(order(OrderSide.SELL, 100, 10));

        assertTrue(trades.isEmpty());
        assertFalse(buy.isActive());
    }

    // Ensures cancelling after a partial fill prevents further matches.
    @Test
    void cancelAfterPartialFillPreventsFurtherMatches() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, new EventDispatcher(), tradeStore());

        Order buy = order(OrderSide.BUY, 100, 10);
        engine.submit(buy);

        List<Trade> firstTrades = engine.submit(order(OrderSide.SELL, 100, 4));

        assertEquals(1, firstTrades.size());
        assertTrue(buy.isActive());
        assertEquals(6, buy.getRemainingQty());

        assertTrue(engine.cancel(buy.getId()));

        List<Trade> secondTrades = engine.submit(order(OrderSide.SELL, 100, 6));

        assertTrue(secondTrades.isEmpty());
        assertFalse(buy.isActive());
    }

    // Ensures repeated cancel calls are safe and do not throw.
    @Test
    void cancelIsIdempotent() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, new EventDispatcher(), tradeStore());

        Order buy = order(OrderSide.BUY, 100, 10);
        engine.submit(buy);

        assertTrue(engine.cancel(buy.getId()));
        assertFalse(engine.cancel(buy.getId()));
    }
}
