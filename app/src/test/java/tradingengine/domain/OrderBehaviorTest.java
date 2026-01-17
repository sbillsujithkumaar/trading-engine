package tradingengine.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests for Order state transitions and validation.
class OrderBehaviorTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");

    // Ensures exact execution fills the order and updates status.
    @Test
    void execute_exactFill_marksFilled() {
        Order order = new Order(OrderSide.BUY, 100, 10, FIXED_TIME);

        long filled = order.execute(10);

        assertEquals(10, filled);
        assertEquals(0, order.getRemainingQty());
        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertFalse(order.isActive());
    }

    // Ensures partial execution updates remaining quantity and status.
    @Test
    void execute_partialFill_marksPartiallyFilled() {
        Order order = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);

        long filled = order.execute(4);

        assertEquals(4, filled);
        assertEquals(6, order.getRemainingQty());
        assertEquals(OrderStatus.PARTIALLY_FILLED, order.getStatus());
        assertTrue(order.isActive());
    }

    // Ensures executions never drive remaining quantity below zero.
    @Test
    void execute_neverMakesQuantityNegative() {
        Order order = new Order(OrderSide.BUY, 100, 5, FIXED_TIME);

        long filled = order.execute(100);

        assertEquals(5, filled);
        assertEquals(0, order.getRemainingQty());
        assertEquals(OrderStatus.FILLED, order.getStatus());
    }

    // Ensures BUY orders match at or below their limit price.
    @Test
    void buyCanMatchLowerOrEqualPrice() {
        Order buy = new Order(OrderSide.BUY, 100, 10, FIXED_TIME);

        assertTrue(buy.canMatch(100));
        assertTrue(buy.canMatch(90));
        assertFalse(buy.canMatch(110));
    }

    // Ensures SELL orders match at or above their limit price.
    @Test
    void sellCanMatchHigherOrEqualPrice() {
        Order sell = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);

        assertTrue(sell.canMatch(100));
        assertTrue(sell.canMatch(110));
        assertFalse(sell.canMatch(90));
    }

    // Ensures invalid execution quantities are rejected.
    @Test
    void execute_rejectsNonPositiveQuantity() {
        Order order = new Order(OrderSide.BUY, 100, 10, FIXED_TIME);

        assertThrows(IllegalArgumentException.class, () -> order.execute(0));
        assertThrows(IllegalArgumentException.class, () -> order.execute(-1));
    }

    // Ensures null timestamps are rejected at construction.
    @Test
    void constructor_rejectsNullTimestamp() {
        assertThrows(NullPointerException.class, () -> new Order(OrderSide.BUY, 100, 10, null));
    }

    // Ensures the provided timestamp is stored unchanged.
    @Test
    void constructor_storesProvidedTimestamp() {
        Order order = new Order(OrderSide.SELL, 100, 10, FIXED_TIME);

        assertEquals(FIXED_TIME, order.getTimestamp());
    }

    // Ensures order side is immutable across state transitions.
    @Test
    void orderSideNeverChanges() {
        Order order = new Order(OrderSide.BUY, 100, 10, FIXED_TIME);

        order.execute(5);

        assertEquals(OrderSide.BUY, order.getSide());
    }

    // Ensures cancel marks the order inactive and cancelled.
    @Test
    void cancelMarksOrderInactive() {
        Order order = new Order(OrderSide.BUY, 100, 10, FIXED_TIME);

        order.cancel();

        assertFalse(order.isActive());
        assertEquals(OrderStatus.CANCELLED, order.getStatus());
    }

    // Ensures cancelling a filled order is a no-op.
    @Test
    void cancelFilledOrderIsNoOp() {
        Order order = new Order(OrderSide.SELL, 100, 1, FIXED_TIME);

        order.execute(1);

        order.cancel();

        assertEquals(OrderStatus.FILLED, order.getStatus());
        assertFalse(order.isActive());
    }

    // Ensures cancelled orders cannot be executed.
    @Test
    void executeCancelledOrderThrows() {
        Order order = new Order(OrderSide.SELL, 100, 1, FIXED_TIME);

        order.cancel();

        assertThrows(IllegalStateException.class, () -> order.execute(1));
    }
}
