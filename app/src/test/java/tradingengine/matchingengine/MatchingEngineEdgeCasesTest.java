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

// Edge-case scenarios that should never break matching logic.
class MatchingEngineEdgeCasesTest {

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

    // Ensures a lone SELL in an empty book produces no trades.
    @Test
    void submitSellWithEmptyBookProducesNoTrades() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, new EventDispatcher(), tradeStore());

        List<Trade> trades = engine.submit(order(OrderSide.SELL, 100, 10));

        assertTrue(trades.isEmpty());
    }

    // Ensures partial fill when incoming BUY is larger than resting SELL.
    @Test
    void buyLargerThanSellPartialFill() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, new EventDispatcher(), tradeStore());

        Order sell = order(OrderSide.SELL, 100, 5);
        engine.submit(sell);

        Order buy = order(OrderSide.BUY, 100, 10);
        List<Trade> trades = engine.submit(buy);

        assertEquals(1, trades.size());
        assertEquals(5, trades.get(0).quantity());

        assertFalse(sell.isActive());
        assertTrue(buy.isActive());
        assertEquals(5, buy.getRemainingQty());
    }

    // Ensures partial fill when incoming SELL is larger than resting BUY.
    @Test
    void sellLargerThanBuyPartialFill() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, new EventDispatcher(), tradeStore());

        Order buy = order(OrderSide.BUY, 100, 5);
        engine.submit(buy);

        Order sell = order(OrderSide.SELL, 100, 10);
        List<Trade> trades = engine.submit(sell);

        assertEquals(1, trades.size());
        assertEquals(5, trades.get(0).quantity());

        assertFalse(buy.isActive());
        assertTrue(sell.isActive());
        assertEquals(5, sell.getRemainingQty());
    }

    // Ensures FILLED orders do not match again.
    @Test
    void filledOrdersNeverMatchAgain() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, new EventDispatcher(), tradeStore());

        Order sell = order(OrderSide.SELL, 100, 2);
        engine.submit(sell);

        Order buy = order(OrderSide.BUY, 100, 2);
        List<Trade> firstTrades = engine.submit(buy);

        assertEquals(1, firstTrades.size());
        assertFalse(sell.isActive());
        assertFalse(buy.isActive());

        List<Trade> secondTrades = engine.submit(order(OrderSide.BUY, 100, 1));

        assertTrue(secondTrades.isEmpty());
    }

    // Ensures CANCELLED orders do not match again.
    @Test
    void cancelledOrdersNeverMatchAgain() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, new EventDispatcher(), tradeStore());

        Order sell = order(OrderSide.SELL, 100, 2);
        engine.submit(sell);
        engine.cancel(sell.getId());

        List<Trade> trades = engine.submit(order(OrderSide.BUY, 100, 1));

        assertTrue(trades.isEmpty());
        assertFalse(sell.isActive());
    }
}
