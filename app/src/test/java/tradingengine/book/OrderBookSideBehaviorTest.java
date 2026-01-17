package tradingengine.book;

import org.junit.jupiter.api.Test;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests for price-time priority behavior on one book side.
class OrderBookSideBehaviorTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");

    // Ensures FIFO ordering within the same price level.
    @Test
    void fifoPreservedAtSamePrice() {
        OrderBookSide side = new OrderBookSide(Long::compareTo);

        Order o1 = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);
        Order o2 = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);

        side.addRestingOrder(o1);
        side.addRestingOrder(o2);

        assertSame(o1, side.peekBestOrderOrNull());

        o1.execute(10);
        side.removeBestOrderIfInactive();

        assertSame(o2, side.peekBestOrderOrNull());
    }

    // Ensures peeking on empty side returns null.
    @Test
    void peekBestOrderOrNull_returnsNullWhenEmpty() {
        OrderBookSide side = new OrderBookSide(Long::compareTo);

        assertNull(side.peekBestOrderOrNull());
    }

    // Ensures bestPrice fails fast when the side is empty.
    @Test
    void bestPrice_throwsWhenEmpty() {
        OrderBookSide side = new OrderBookSide(Long::compareTo);

        assertThrows(java.util.NoSuchElementException.class, side::bestPrice);
    }

    // Ensures active orders are not removed by cleanup.
    @Test
    void removeBestOrderIfInactive_keepsActiveOrder() {
        OrderBookSide side = new OrderBookSide(Long::compareTo);

        Order order = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);
        side.addRestingOrder(order);

        assertNull(side.removeBestOrderIfInactive());
        assertSame(order, side.peekBestOrderOrNull());
    }

    // Ensures cancelling the best order removes it immediately.
    @Test
    void cancelRemovesFromTopImmediately() {
        OrderBookSide side = new OrderBookSide(Long::compareTo);

        Order order = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);
        side.addRestingOrder(order);

        assertTrue(side.cancelOrderById(order.getId(), new OrderLocator(OrderSide.SELL, 100)));
        assertNull(side.peekBestOrderOrNull());
    }

    // Ensures FIFO remains intact after cancelling a middle order.
    @Test
    void fifoPreservedAfterCancelAtSamePrice() {
        OrderBookSide side = new OrderBookSide(Long::compareTo);

        Order o1 = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);
        Order o2 = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);
        Order o3 = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);

        side.addRestingOrder(o1);
        side.addRestingOrder(o2);
        side.addRestingOrder(o3);

        assertTrue(side.cancelOrderById(o2.getId(), new OrderLocator(OrderSide.SELL, 100)));
        assertSame(o1, side.peekBestOrderOrNull());

        o1.execute(10);
        side.removeBestOrderIfInactive();

        assertSame(o3, side.peekBestOrderOrNull());
    }

    // Ensures cancelling a non-best price level does not affect the best level.
    @Test
    void cancelNonBestPriceLevelDoesNotChangeBest() {
        OrderBookSide side = new OrderBookSide(Long::compareTo);

        Order best = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);
        Order higher = new Order(OrderSide.SELL, 101, 10, FIXED_TIME);

        side.addRestingOrder(best);
        side.addRestingOrder(higher);

        assertSame(best, side.peekBestOrderOrNull());
        assertTrue(side.cancelOrderById(higher.getId(), new OrderLocator(OrderSide.SELL, 101)));
        assertSame(best, side.peekBestOrderOrNull());
    }
}
