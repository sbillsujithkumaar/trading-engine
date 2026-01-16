package tradingengine.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OrderConstraintsTest {

    @Test
    void requireValidSide_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> OrderConstraints.requireValidSide(null));
    }

    @Test
    void requireValidPrice_rejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> OrderConstraints.requireValidPrice(0));
        assertThrows(IllegalArgumentException.class, () -> OrderConstraints.requireValidPrice(-1));
    }

    @Test
    void requireValidQuantity_rejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> OrderConstraints.requireValidQuantity(0));
        assertThrows(IllegalArgumentException.class, () -> OrderConstraints.requireValidQuantity(-5));
    }

    @Test
    void requireValidValues_acceptsPositive() {
        assertEquals(OrderSide.BUY, OrderConstraints.requireValidSide(OrderSide.BUY));
        assertEquals(10L, OrderConstraints.requireValidPrice(10));
        assertEquals(7L, OrderConstraints.requireValidQuantity(7));
    }
}
