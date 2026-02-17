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

    @Test
    void snapshotLevels_includesOrdersInFifoOrder() {
        OrderBookSide side = new OrderBookSide(Long::compareTo);

        Order o1 = new Order(OrderSide.SELL, 100, 3, FIXED_TIME);
        Order o2 = new Order(OrderSide.SELL, 100, 7, FIXED_TIME);
        side.addRestingOrder(o1);
        side.addRestingOrder(o2);

        var levels = side.snapshotLevels();
        assertEquals(1, levels.size());
        var level = levels.get(0);
        assertEquals(2, level.orders().size());
        assertEquals(o1.getId(), level.orders().get(0).orderId());
        assertEquals(3L, level.orders().get(0).qty());
        assertEquals(o2.getId(), level.orders().get(1).orderId());
        assertEquals(7L, level.orders().get(1).qty());
    }

    @Test
    void snapshotLevels_aggregatesMatchOrders() {
        OrderBookSide side = new OrderBookSide(Long::compareTo);

        Order o1 = new Order(OrderSide.SELL, 100, 4, FIXED_TIME);
        Order o2 = new Order(OrderSide.SELL, 100, 6, FIXED_TIME);
        side.addRestingOrder(o1);
        side.addRestingOrder(o2);

        var level = side.snapshotLevels().get(0);
        long sum = level.orders().stream().mapToLong(OrderBookSide.RestingOrderSnapshot::qty).sum();

        assertEquals(sum, level.qty());
        assertEquals(level.orders().size(), level.count());
    }

    @Test
    void snapshotLevels_skipsInactiveOrZeroQtyOrders() {
        OrderBookSide side = new OrderBookSide(Long::compareTo);

        Order cancelled = new Order(OrderSide.SELL, 100, 5, FIXED_TIME);
        Order active = new Order(OrderSide.SELL, 100, 9, FIXED_TIME);
        side.addRestingOrder(cancelled);
        side.addRestingOrder(active);
        cancelled.cancel();

        var level = side.snapshotLevels().get(0);
        assertEquals(1, level.count());
        assertEquals(9L, level.qty());
        assertEquals(active.getId(), level.orders().get(0).orderId());
    }

    @Test
    void snapshotLevels_preservesPricePriority() {
        OrderBookSide buySide = new OrderBookSide(java.util.Comparator.reverseOrder());
        buySide.addRestingOrder(new Order(OrderSide.BUY, 101, 1, FIXED_TIME));
        buySide.addRestingOrder(new Order(OrderSide.BUY, 103, 1, FIXED_TIME));

        var buyLevels = buySide.snapshotLevels();
        assertEquals(103L, buyLevels.get(0).price());
        assertEquals(101L, buyLevels.get(1).price());

        OrderBookSide sellSide = new OrderBookSide(java.util.Comparator.naturalOrder());
        sellSide.addRestingOrder(new Order(OrderSide.SELL, 104, 1, FIXED_TIME));
        sellSide.addRestingOrder(new Order(OrderSide.SELL, 102, 1, FIXED_TIME));

        var sellLevels = sellSide.snapshotLevels();
        assertEquals(102L, sellLevels.get(0).price());
        assertEquals(104L, sellLevels.get(1).price());
    }
}
