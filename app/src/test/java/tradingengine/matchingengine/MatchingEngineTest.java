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

class MatchingEngineTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    private static Order order(OrderSide side, long price, long quantity) {
        return new Order(side, price, quantity, Instant.now(FIXED_CLOCK));
    }

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

    @Test
    void emptyBookProducesNoTrades() {
        MatchingEngine engine = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

        Order buy = order(OrderSide.BUY, 100, 10);
        List<Trade> trades = engine.submit(buy);

        assertTrue(trades.isEmpty());
        assertTrue(buy.isActive());
    }

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

    @Test
    void deterministicExecutionProducesSameTradeValues() {
        MatchingEngine engine1 = new MatchingEngine(new OrderBook(), FIXED_CLOCK);
        MatchingEngine engine2 = new MatchingEngine(new OrderBook(), FIXED_CLOCK);

        Order b1 = order(OrderSide.BUY, 100, 10);
        Order s1 = order(OrderSide.SELL, 100, 10);
        engine1.submit(b1);
        List<Trade> t1 = engine1.submit(s1);

        Order b2 = order(OrderSide.BUY, 100, 10);
        Order s2 = order(OrderSide.SELL, 100, 10);
        engine2.submit(b2);
        List<Trade> t2 = engine2.submit(s2);

        assertEquals(tradeSignatures(t1), tradeSignatures(t2));
    }

    private static List<String> tradeSignatures(List<Trade> trades) {
        List<String> signatures = new ArrayList<>();
        for (Trade trade : trades) {
            signatures.add(trade.price() + ":" + trade.quantity() + ":" + trade.timestamp());
        }
        return signatures;
    }
}
