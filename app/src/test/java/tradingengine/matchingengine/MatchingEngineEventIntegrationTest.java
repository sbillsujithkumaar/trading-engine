package tradingengine.matchingengine;

import org.junit.jupiter.api.Test;
import tradingengine.book.OrderBook;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;
import tradingengine.domain.Trade;
import tradingengine.events.EngineEvent;
import tradingengine.events.EventDispatcher;
import tradingengine.events.OrderBookEvent;
import tradingengine.events.OrderBookEventType;
import tradingengine.events.TradeExecutedEvent;
import tradingengine.events.listeners.CapturingEventListener;
import tradingengine.persistence.FileTradeStore;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Integration tests that validate event emission and ordering.
@SuppressWarnings("null")
class MatchingEngineEventIntegrationTest {

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

    // Ensures trade events are emitted in execution order with deterministic timestamps.
    @Test
    void tradeEventsAreEmittedInOrder() {
        EventDispatcher dispatcher = new EventDispatcher();
        CapturingEventListener<TradeExecutedEvent> tradeListener = new CapturingEventListener<>();
        dispatcher.register(TradeExecutedEvent.class, tradeListener);

        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, dispatcher, tradeStore());

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

        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, dispatcher, tradeStore());

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

    // Ensures adding a resting order emits a book add event.
    @Test
    void addEventEmittedWhenOrderRests() {
        EventDispatcher dispatcher = new EventDispatcher();
        CapturingEventListener<OrderBookEvent> bookListener = new CapturingEventListener<>();
        dispatcher.register(OrderBookEvent.class, bookListener);

        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, dispatcher, tradeStore());

        Order buy = order(OrderSide.BUY, 101, 5);
        engine.submit(buy);

        List<OrderBookEvent> events = bookListener.events();
        assertEquals(1, events.size());
        OrderBookEvent addEvent = events.get(0);
        assertEquals(OrderBookEventType.ADD, addEvent.type());
        assertEquals(OrderSide.BUY, addEvent.side());
        assertEquals(101, addEvent.price());
        assertEquals(FIXED_INSTANT, addEvent.timestamp());
    }

    // Ensures a filled resting order emits a book remove event.
    @Test
    void removeEventEmittedOnFill() {
        EventDispatcher dispatcher = new EventDispatcher();
        CapturingEventListener<OrderBookEvent> bookListener = new CapturingEventListener<>();
        dispatcher.register(OrderBookEvent.class, bookListener);

        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, dispatcher, tradeStore());

        engine.submit(order(OrderSide.SELL, 100, 4));
        engine.submit(order(OrderSide.BUY, 100, 4));

        OrderBookEvent removeEvent = bookListener.events().stream()
                .filter(event -> event.type() == OrderBookEventType.REMOVE)
                .findFirst()
                .orElseThrow();

        assertEquals(OrderSide.SELL, removeEvent.side());
        assertEquals(100, removeEvent.price());
        assertEquals(FIXED_INSTANT, removeEvent.timestamp());
    }

    // Ensures a wildcard listener receives all event types in order.
    @Test
    void wildcardListenerReceivesAllEventsInOrder() {
        EventDispatcher dispatcher = new EventDispatcher();
        CapturingEventListener<EngineEvent> allListener = new CapturingEventListener<>();
        dispatcher.register(EngineEvent.class, allListener);

        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, dispatcher, tradeStore());

        engine.submit(order(OrderSide.SELL, 100, 5));
        engine.submit(order(OrderSide.BUY, 100, 5));

        List<EngineEvent> events = allListener.events();
        assertEquals(3, events.size());
        assertTrue(events.get(0) instanceof OrderBookEvent);
        assertTrue(events.get(1) instanceof TradeExecutedEvent);
        assertTrue(events.get(2) instanceof OrderBookEvent);
    }

    // Ensures no trade events are emitted when no match occurs.
    @Test
    void noTradeEventWhenNoMatchOccurs() {
        EventDispatcher dispatcher = new EventDispatcher();
        CapturingEventListener<TradeExecutedEvent> tradeListener = new CapturingEventListener<>();
        dispatcher.register(TradeExecutedEvent.class, tradeListener);

        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK, dispatcher, tradeStore());

        engine.submit(order(OrderSide.BUY, 99, 5));
        engine.submit(order(OrderSide.SELL, 105, 5));

        assertTrue(tradeListener.events().isEmpty());
    }
}
