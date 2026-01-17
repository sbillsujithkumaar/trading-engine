package tradingengine.book;

import org.junit.jupiter.api.Test;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests for OrderBook validation and basic queries.
class OrderBookBehaviorTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");

    // Ensures null orders are rejected.
    @Test
    void addOrder_rejectsNull() {
        OrderBook book = new OrderBook();

        assertThrows(NullPointerException.class, () -> book.addOrder(null));
    }

    // Ensures inactive orders are rejected.
    @Test
    void addOrder_rejectsInactiveOrder() {
        OrderBook book = new OrderBook();
        Order order = new Order(OrderSide.BUY, 100, 1, FIXED_TIME);

        order.execute(1);

        assertFalse(order.isActive());
        assertThrows(IllegalArgumentException.class, () -> book.addOrder(order));
    }

    // Ensures best bid access fails on empty book.
    @Test
    void bestBid_throwsWhenEmpty() {
        OrderBook book = new OrderBook();

        assertThrows(IllegalStateException.class, book::bestBid);
    }

    // Ensures cancelling an unknown id returns false.
    @Test
    void cancelOrder_returnsFalseForUnknownId() {
        OrderBook book = new OrderBook();

        assertFalse(book.cancelOrder("unknown"));
    }

    // Ensures cancelling the best order updates bestBid immediately.
    @Test
    void cancelOrder_updatesBestBidImmediately() {
        OrderBook book = new OrderBook();

        Order bestBid = new Order(OrderSide.BUY, 101, 1, FIXED_TIME);
        Order nextBid = new Order(OrderSide.BUY, 100, 1, FIXED_TIME);

        book.addOrder(bestBid);
        book.addOrder(nextBid);

        assertEquals(101, book.bestBid());
        assertTrue(book.cancelOrder(bestBid.getId()));
        assertEquals(100, book.bestBid());
    }

    // Ensures cancelling the best ask updates the SELL side top-of-book immediately.
    @Test
    void cancelOrder_updatesBestAskImmediately() {
        OrderBook book = new OrderBook();

        Order bestAsk = new Order(OrderSide.SELL, 99, 1, FIXED_TIME);
        Order nextAsk = new Order(OrderSide.SELL, 101, 1, FIXED_TIME);

        book.addOrder(bestAsk);
        book.addOrder(nextAsk);

        assertEquals(99, book.sellSide().bestPrice());
        assertTrue(book.cancelOrder(bestAsk.getId()));
        assertEquals(101, book.sellSide().bestPrice());
    }

    // Ensures cancelling a non-best order does not change the current best bid.
    @Test
    void cancelOrder_nonBestDoesNotChangeBestBid() {
        OrderBook book = new OrderBook();

        Order bestBid = new Order(OrderSide.BUY, 101, 1, FIXED_TIME);
        Order lowerBid = new Order(OrderSide.BUY, 100, 1, FIXED_TIME);

        book.addOrder(bestBid);
        book.addOrder(lowerBid);

        assertEquals(101, book.bestBid());
        assertTrue(book.cancelOrder(lowerBid.getId()));
        assertEquals(101, book.bestBid());
    }
}
