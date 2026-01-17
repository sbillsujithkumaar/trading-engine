package tradingengine.matchingengine;

import org.junit.jupiter.api.Test;
import tradingengine.book.OrderBook;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;
import tradingengine.domain.OrderStatus;
import tradingengine.domain.Trade;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Integration tests covering core matching behavior end-to-end.
class MatchingEngineIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private static Order order(OrderSide side, long price, long quantity) {
        return new Order(side, price, quantity, Instant.now(FIXED_CLOCK));
    }

    // Ensures exact quantity matches create a single trade and fill both orders.
    @Test
    void exactMatchProducesSingleTrade() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

        Order buy = order(OrderSide.BUY, 100, 10);
        Order sell = order(OrderSide.SELL, 100, 10);

        engine.submit(buy);
        List<Trade> trades = engine.submit(sell);

        assertEquals(1, trades.size());
        Trade trade = trades.get(0);

        assertEquals(10, trade.quantity());
        assertEquals(100, trade.price());
        assertEquals(FIXED_INSTANT, trade.timestamp());
        assertFalse(buy.isActive());
        assertFalse(sell.isActive());
    }

    // Ensures one incoming order can match multiple resting orders.
    @Test
    void incomingOrderMatchesMultipleRestingOrders() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

        Order s1 = order(OrderSide.SELL, 100, 5);
        Order s2 = order(OrderSide.SELL, 100, 5);

        engine.submit(s1);
        engine.submit(s2);

        Order buy = order(OrderSide.BUY, 100, 8);
        List<Trade> trades = engine.submit(buy);

        assertEquals(2, trades.size());
        assertEquals(5, trades.get(0).quantity());
        assertEquals(3, trades.get(1).quantity());
        assertFalse(s1.isActive());
        assertTrue(s2.isActive());
        assertEquals(2, s2.getRemainingQty());
        assertEquals(OrderStatus.PARTIALLY_FILLED, s2.getStatus());
    }

    // Ensures FIFO ordering is respected at the same price level.
    @Test
    void fifoIsRespectedAtSamePrice() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

        Order s1 = order(OrderSide.SELL, 100, 5);
        Order s2 = order(OrderSide.SELL, 100, 5);

        engine.submit(s1);
        engine.submit(s2);

        Order buy = order(OrderSide.BUY, 100, 5);
        List<Trade> trades = engine.submit(buy);

        assertEquals(1, trades.size());
        assertEquals(s1.getId(), trades.get(0).sellOrderId());
    }

    // Ensures an empty opposite book produces no trades.
    @Test
    void emptyBookProducesNoTrades() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

        Order buy = order(OrderSide.BUY, 100, 10);
        List<Trade> trades = engine.submit(buy);

        assertTrue(trades.isEmpty());
        assertTrue(buy.isActive());
    }

    // Ensures trades execute at the resting order price.
    @Test
    void tradeExecutesAtRestingPrice() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

        Order restingSell = order(OrderSide.SELL, 100, 5);
        engine.submit(restingSell);

        Order incomingBuy = order(OrderSide.BUY, 105, 5);
        List<Trade> trades = engine.submit(incomingBuy);

        assertEquals(1, trades.size());
        assertEquals(100, trades.get(0).price());
        assertEquals(restingSell.getId(), trades.get(0).sellOrderId());
    }

    // Ensures non-crossing prices do not trade.
    @Test
    void nonCrossingOrdersDoNotTrade() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

        Order buy = order(OrderSide.BUY, 100, 5);
        engine.submit(buy);

        Order sell = order(OrderSide.SELL, 110, 5);
        List<Trade> trades = engine.submit(sell);

        assertTrue(trades.isEmpty());
        assertTrue(buy.isActive());
        assertTrue(sell.isActive());
    }

    // Ensures incoming remainder rests and can match later.
    @Test
    void incomingRemainderIsRestedAndMatchesLater() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

        Order restingSell = order(OrderSide.SELL, 100, 5);
        engine.submit(restingSell);

        Order incomingBuy = order(OrderSide.BUY, 100, 8);
        List<Trade> firstTrades = engine.submit(incomingBuy);

        assertEquals(1, firstTrades.size());
        assertEquals(5, firstTrades.get(0).quantity());
        assertTrue(incomingBuy.isActive());
        assertEquals(3, incomingBuy.getRemainingQty());

        Order incomingSell = order(OrderSide.SELL, 100, 3);
        List<Trade> secondTrades = engine.submit(incomingSell);

        assertEquals(1, secondTrades.size());
        assertFalse(incomingBuy.isActive());
        assertFalse(incomingSell.isActive());
    }

    // Ensures identical sequences produce identical trade signatures.
    @Test
    void sameSequenceProducesSameTrades() {
        MatchingEngine engine1 = new MatchingEngine(new OrderBook(), FIXED_CLOCK);
        MatchingEngine engine2 = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

        List<Trade> t1 = new ArrayList<>();
        t1.addAll(engine1.submit(order(OrderSide.BUY, 100, 10)));
        t1.addAll(engine1.submit(order(OrderSide.SELL, 100, 10)));
        t1.addAll(engine1.submit(order(OrderSide.SELL, 101, 5)));
        t1.addAll(engine1.submit(order(OrderSide.BUY, 105, 5)));

        List<Trade> t2 = new ArrayList<>();
        t2.addAll(engine2.submit(order(OrderSide.BUY, 100, 10)));
        t2.addAll(engine2.submit(order(OrderSide.SELL, 100, 10)));
        t2.addAll(engine2.submit(order(OrderSide.SELL, 101, 5)));
        t2.addAll(engine2.submit(order(OrderSide.BUY, 105, 5)));

        assertEquals(tradeSignatures(t1), tradeSignatures(t2));
    }

    // Ensures determinism across repeated runs.
    @Test
    void repeatedRunsAreDeterministic() {
        List<String> expected = null;

        for (int i = 0; i < 50; i++) {
            MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

            List<Trade> trades = new ArrayList<>();
            trades.addAll(engine.submit(order(OrderSide.BUY, 100, 10)));
            trades.addAll(engine.submit(order(OrderSide.SELL, 100, 10)));

            List<String> signature = tradeSignatures(trades);
            if (expected == null) {
                expected = signature;
            } else {
                assertEquals(expected, signature);
            }
        }
    }

    // Ensures best price levels are matched before worse prices.
    @Test
    void bestPriceLevelIsMatchedFirst() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

        Order higherAsk = order(OrderSide.SELL, 101, 5);
        Order bestAsk = order(OrderSide.SELL, 100, 5);
        engine.submit(higherAsk);
        engine.submit(bestAsk);

        Order incomingBuy = order(OrderSide.BUY, 105, 5);
        List<Trade> trades = engine.submit(incomingBuy);

        assertEquals(1, trades.size());
        assertEquals(100, trades.get(0).price());
        assertEquals(bestAsk.getId(), trades.get(0).sellOrderId());
    }

    // Ensures large incoming orders sweep multiple price levels in order.
    @Test
    void largeIncomingOrderSweepsMultiplePriceLevels() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

        Order s1 = order(OrderSide.SELL, 100, 5);
        Order s2 = order(OrderSide.SELL, 101, 7);
        engine.submit(s1);
        engine.submit(s2);

        Order buy = order(OrderSide.BUY, 105, 12);
        List<Trade> trades = engine.submit(buy);

        assertEquals(2, trades.size());
        assertEquals(5, trades.get(0).quantity());
        assertEquals(100, trades.get(0).price());
        assertEquals(7, trades.get(1).quantity());
        assertEquals(101, trades.get(1).price());
        assertFalse(buy.isActive());
        assertFalse(s1.isActive());
        assertFalse(s2.isActive());
    }

    private static List<String> tradeSignatures(List<Trade> trades) {
        List<String> signatures = new ArrayList<>();
        for (Trade trade : trades) {
            signatures.add(trade.price() + ":" + trade.quantity() + ":" + trade.timestamp());
        }
        return signatures;
    }
}
