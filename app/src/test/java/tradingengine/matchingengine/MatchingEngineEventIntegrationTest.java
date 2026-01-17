package tradingengine.matchingengine;

import org.junit.jupiter.api.Test;
import tradingengine.book.OrderBook;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;
import tradingengine.domain.Trade;
import tradingengine.events.EventDispatcher;
import tradingengine.events.OrderBookEvent;
import tradingengine.events.OrderBookEventType;
import tradingengine.events.TradeExecutedEvent;
import tradingengine.events.listeners.CapturingEventListener;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Integration tests that validate event emission and ordering.
class MatchingEngineEventIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private static Order order(OrderSide side, long price, long quantity) {
        return new Order(side, price, quantity, Instant.now(FIXED_CLOCK));
    }

    // Ensures trade events are emitted in execution order with deterministic timestamps.
    @Test
    void tradeEventsAreEmittedInOrder() {
        EventDispatcher dispatcher = new EventDispatcher();
        CapturingEventListener<TradeExecutedEvent> tradeListener = new CapturingEventListener<>();
        dispatcher.register(TradeExecutedEvent.class, tradeListener);

        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, dispatcher);

        engine.submit(order(OrderSide.SELL, 100, 5));
        engine.submit(order(OrderSide.SELL, 100, 5));
        List<Trade> trades = engine.submit(order(OrderSide.BUY, 100, 8));

        List<TradeExecutedEvent> events = tradeListener.events();
        assertEquals(2, events.size());
        assertEquals(trades.get(0), events.get(0).trade());
        assertEquals(trades.get(1), events.get(1).trade());
        assertEquals(FIXED_INSTANT, events.get(0).timestamp());
        assertEquals(FIXED_INSTANT, events.get(1).timestamp());
    }

    // Ensures cancelling an order emits a cancel event with order book context.
    @Test
    void cancelEmitsOrderBookEvent() {
        EventDispatcher dispatcher = new EventDispatcher();
        CapturingEventListener<OrderBookEvent> bookListener = new CapturingEventListener<>();
        dispatcher.register(OrderBookEvent.class, bookListener);

        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, dispatcher);

        Order buy = order(OrderSide.BUY, 100, 10);
        engine.submit(buy);

        assertTrue(engine.cancel(buy.getId()));

        List<OrderBookEvent> events = bookListener.events();
        OrderBookEvent cancelEvent = events.stream()
                .filter(event -> event.type() == OrderBookEventType.CANCEL)
                .findFirst()
                .orElseThrow();

        assertEquals(OrderSide.BUY, cancelEvent.side());
        assertEquals(100, cancelEvent.price());
        assertEquals(FIXED_INSTANT, cancelEvent.timestamp());
    }
}
