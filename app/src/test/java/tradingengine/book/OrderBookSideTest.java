package tradingengine.book;

import org.junit.jupiter.api.Test;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookSideTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void fifoPreservedAtSamePrice() {
        OrderBookSide side = new OrderBookSide(Long::compareTo);

        Order o1 = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);
        Order o2 = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);

        side.addOrder(o1);
        side.addOrder(o2);

        assertSame(o1, side.peekBestOrder());

        o1.execute(10);
        side.removeHeadOrderIfInactive();

        assertSame(o2, side.peekBestOrder());
    }

    @Test
    void peekBestOrder_throwsWhenEmpty() {
        OrderBookSide side = new OrderBookSide(Long::compareTo);

        assertThrows(IllegalStateException.class, side::peekBestOrder);
    }

    @Test
    void bestPrice_throwsWhenEmpty() {
        OrderBookSide side = new OrderBookSide(Long::compareTo);

        assertThrows(java.util.NoSuchElementException.class, side::bestPrice);
    }

    @Test
    void removeHeadOrderIfInactive_keepsActiveOrder() {
        OrderBookSide side = new OrderBookSide(Long::compareTo);

        Order order = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);
        side.addOrder(order);

        side.removeHeadOrderIfInactive();

        assertSame(order, side.peekBestOrder());
    }
}
