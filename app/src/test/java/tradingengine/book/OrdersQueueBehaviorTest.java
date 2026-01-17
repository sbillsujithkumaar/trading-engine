package tradingengine.book;

import org.junit.jupiter.api.Test;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests for FIFO behavior within a single price level.
class OrdersQueueBehaviorTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");

    // Ensures FIFO order is preserved on peek and remove.
    @Test
    void fifoOrderingIsPreserved() {
        OrdersQueue level = new OrdersQueue();

        Order o1 = new Order(OrderSide.BUY, 100, 1, FIXED_TIME);
        Order o2 = new Order(OrderSide.BUY, 100, 1, FIXED_TIME);

        level.add(o1);
        level.add(o2);

        assertSame(o1, level.peekOldest());
        assertSame(o1, level.removeOldest());
        assertSame(o2, level.peekOldest());
    }

    // Ensures removal by id deletes the correct order.
    @Test
    void removeByIdRemovesCorrectOrder() {
        OrdersQueue level = new OrdersQueue();

        Order o1 = new Order(OrderSide.SELL, 100, 1, FIXED_TIME);
        Order o2 = new Order(OrderSide.SELL, 100, 1, FIXED_TIME);

        level.add(o1);
        level.add(o2);

        assertSame(o1, level.removeById(o1.getId()));
        assertSame(o2, level.peekOldest());
    }

    // Ensures empty level returns null on peek/remove.
    @Test
    void emptyLevelReturnsNull() {
        OrdersQueue level = new OrdersQueue();

        assertNull(level.peekOldest());
        assertNull(level.removeOldest());
        assertTrue(level.isEmpty());
    }
}
