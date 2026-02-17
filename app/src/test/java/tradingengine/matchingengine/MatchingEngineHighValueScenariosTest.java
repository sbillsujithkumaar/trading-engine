package tradingengine.matchingengine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tradingengine.book.OrderBook;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;
import tradingengine.domain.OrderStatus;
import tradingengine.domain.Trade;
import tradingengine.events.EngineEvent;
import tradingengine.events.EventDispatcher;
import tradingengine.events.listeners.CapturingEventListener;
import tradingengine.persistence.CommandLog;
import tradingengine.persistence.FileTradeStore;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// High-value scenarios that combine matching behavior with persistence/event expectations.
class MatchingEngineHighValueScenariosTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    private MatchingEngine newEngineWithWal(EventDispatcher dispatcher) {
        return new MatchingEngine(
                new OrderBook(),
                FIXED_CLOCK,
                dispatcher,
                new FileTradeStore(tempDir.resolve("trades.csv")),
                new CommandLog(tempDir.resolve("commands.log"))
        );
    }

    private CommandLog commandLog() {
        return new CommandLog(tempDir.resolve("commands.log"));
    }

    private static Order order(OrderSide side, long price, long quantity) {
        return new Order(side, price, quantity, Instant.now(FIXED_CLOCK));
    }

    // Rationale: Verifies the most fundamental case where one incoming order exactly fills one resting order.
    @Test
    void exactMatchAtSamePriceFillsBothOrdersAndClearsLevel() {
        MatchingEngine engine = newEngineWithWal(new EventDispatcher());

        Order restingSell = order(OrderSide.SELL, 100, 5);
        engine.submit(restingSell);

        Order incomingBuy = order(OrderSide.BUY, 100, 5);
        List<Trade> trades = engine.submit(incomingBuy);

        assertEquals(1, trades.size());
        assertEquals(100, trades.get(0).price());
        assertEquals(5, trades.get(0).quantity());
        assertEquals(OrderStatus.FILLED, restingSell.getStatus());
        assertEquals(OrderStatus.FILLED, incomingBuy.getStatus());
        assertNull(engine.getBook().sellSide().peekBestOrderOrNull());
    }

    // Rationale: Ensures partial fills preserve a correct remainder on the same price level with valid status.
    @Test
    void partialFillLeavesRestingRemainderOnBook() {
        MatchingEngine engine = newEngineWithWal(new EventDispatcher());

        Order restingSell = order(OrderSide.SELL, 100, 10);
        engine.submit(restingSell);

        Order incomingBuy = order(OrderSide.BUY, 100, 4);
        List<Trade> trades = engine.submit(incomingBuy);

        assertEquals(1, trades.size());
        assertEquals(4, trades.get(0).quantity());
        assertEquals(6, restingSell.getRemainingQty());
        assertEquals(OrderStatus.PARTIALLY_FILLED, restingSell.getStatus());
        assertTrue(restingSell.isActive());
    }

    // Rationale: Confirms FIFO fairness at one price level when an incoming order must sweep multiple resting orders.
    @Test
    void incomingOrderSweepsFifoWithinSamePriceLevel() {
        MatchingEngine engine = newEngineWithWal(new EventDispatcher());

        Order olderSell = order(OrderSide.SELL, 100, 3);
        Order newerSell = order(OrderSide.SELL, 100, 4);
        engine.submit(olderSell);
        engine.submit(newerSell);

        Order incomingBuy = order(OrderSide.BUY, 100, 6);
        List<Trade> trades = engine.submit(incomingBuy);

        assertEquals(2, trades.size());
        assertEquals(3, trades.get(0).quantity());
        assertEquals(3, trades.get(1).quantity());
        assertEquals(olderSell.getId(), trades.get(0).sellOrderId());
        assertEquals(newerSell.getId(), trades.get(1).sellOrderId());
        assertEquals(1, newerSell.getRemainingQty());
    }

    // Rationale: Confirms best-price-first matching across levels and resting-price execution semantics.
    @Test
    void incomingOrderCrossesMultiplePriceLevelsBestPriceFirst() {
        MatchingEngine engine = newEngineWithWal(new EventDispatcher());

        engine.submit(order(OrderSide.SELL, 100, 2));
        engine.submit(order(OrderSide.SELL, 101, 3));
        engine.submit(order(OrderSide.SELL, 102, 5));

        List<Trade> trades = engine.submit(order(OrderSide.BUY, 102, 6));

        assertEquals(3, trades.size());
        assertEquals(100, trades.get(0).price());
        assertEquals(2, trades.get(0).quantity());
        assertEquals(101, trades.get(1).price());
        assertEquals(3, trades.get(1).quantity());
        assertEquals(102, trades.get(2).price());
        assertEquals(1, trades.get(2).quantity());
    }

    // Rationale: Ensures price priority outranks arrival time when orders are at different prices.
    @Test
    void betterPriceMatchesBeforeOlderWorsePrice() {
        MatchingEngine engine = newEngineWithWal(new EventDispatcher());

        Order olderWorseSell = order(OrderSide.SELL, 101, 5);
        Order newerBetterSell = order(OrderSide.SELL, 100, 5);
        engine.submit(olderWorseSell);
        engine.submit(newerBetterSell);

        List<Trade> trades = engine.submit(order(OrderSide.BUY, 101, 5));

        assertEquals(1, trades.size());
        assertEquals(100, trades.get(0).price());
        assertEquals(newerBetterSell.getId(), trades.get(0).sellOrderId());
    }

    // Rationale: Verifies non-crossing input creates resting liquidity instead of accidental executions.
    @Test
    void noMatchCreatesRestingOrder() {
        MatchingEngine engine = newEngineWithWal(new EventDispatcher());

        Order buy = order(OrderSide.BUY, 99, 5);
        List<Trade> trades = engine.submit(buy);

        assertTrue(trades.isEmpty());
        assertTrue(buy.isActive());
        assertEquals(99, engine.getBook().bestBid());
    }

    // Rationale: Unknown cancels should be harmless and must not pollute the command log with failed CANCEL records.
    @Test
    void cancelUnknownOrderDoesNotAppendCancelCommand() {
        MatchingEngine engine = newEngineWithWal(new EventDispatcher());

        boolean cancelled = engine.cancel("missing-order-id");

        assertFalse(cancelled);
        assertTrue(commandLog().readAll().isEmpty());
    }

    // Rationale: During recovery replay, external event streams should stay quiet while trades are still rebuilt.
    @Test
    @SuppressWarnings("null")
    void replayModeSuppressesEventsButStillPersistsTrades() {
        EventDispatcher dispatcher = new EventDispatcher();
        CapturingEventListener<EngineEvent> allEvents = new CapturingEventListener<>();
        dispatcher.register(EngineEvent.class, allEvents);

        MatchingEngine engine = newEngineWithWal(dispatcher);
        engine.setReplayMode(true);

        engine.submit(order(OrderSide.SELL, 100, 5));
        engine.submit(order(OrderSide.BUY, 100, 5));

        assertTrue(allEvents.events().isEmpty());
        assertEquals(1, engine.tradeHistory().size());
        assertTrue(commandLog().readAll().isEmpty());
    }
}
