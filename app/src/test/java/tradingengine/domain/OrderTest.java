package tradingengine.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void execute_exactFill_marksFilled() {
        Order order = new Order(OrderSide.BUY, 100, 10, FIXED_TIME);

        long filled = order.execute(10);

        assertEquals(10, filled);
        assertEquals(0, order.getRemainingQty());
        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertFalse(order.isActive());
    }

    @Test
    void execute_partialFill_marksPartiallyFilled() {
        Order order = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);

        long filled = order.execute(4);

        assertEquals(4, filled);
        assertEquals(6, order.getRemainingQty());
        assertEquals(OrderStatus.PARTIALLY_FILLED, order.getStatus());
        assertTrue(order.isActive());
    }

    @Test
    void execute_neverMakesQuantityNegative() {
        Order order = new Order(OrderSide.BUY, 100, 5, FIXED_TIME);

        long filled = order.execute(100);

        assertEquals(5, filled);
        assertEquals(0, order.getRemainingQty());
        assertEquals(OrderStatus.FILLED, order.getStatus());
    }

    @Test
    void buyCanMatchLowerOrEqualPrice() {
        Order buy = new Order(OrderSide.BUY, 100, 10, FIXED_TIME);

        assertTrue(buy.canMatch(100));
        assertTrue(buy.canMatch(90));
        assertFalse(buy.canMatch(110));
    }

    @Test
    void sellCanMatchHigherOrEqualPrice() {
        Order sell = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);

        assertTrue(sell.canMatch(100));
        assertTrue(sell.canMatch(110));
        assertFalse(sell.canMatch(90));
    }

    @Test
    void execute_rejectsNonPositiveQuantity() {
        Order order = new Order(OrderSide.BUY, 100, 10, FIXED_TIME);

        assertThrows(IllegalArgumentException.class, () -> order.execute(0));
        assertThrows(IllegalArgumentException.class, () -> order.execute(-1));
    }

    @Test
    void constructor_rejectsNullTimestamp() {
        assertThrows(NullPointerException.class, () -> new Order(OrderSide.BUY, 100, 10, null));
    }

    @Test
    void constructor_storesProvidedTimestamp() {
        Order order = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);

        assertEquals(FIXED_TIME, order.getTimestamp());
    }
}
