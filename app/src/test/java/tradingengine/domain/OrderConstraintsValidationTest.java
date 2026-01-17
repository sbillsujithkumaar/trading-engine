package tradingengine.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests for OrderConstraints validation rules.
class OrderConstraintsValidationTest {

    // Ensures side validation rejects null.
    @Test
    void requireValidSide_rejectsNull() {
        assertThrows(IllegalArgumentException.class, () -> OrderConstraints.requireValidSide(null));
    }

    // Ensures price validation rejects non-positive values.
    @Test
    void requireValidPrice_rejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> OrderConstraints.requireValidPrice(0));
        assertThrows(IllegalArgumentException.class, () -> OrderConstraints.requireValidPrice(-1));
    }

    // Ensures quantity validation rejects non-positive values.
    @Test
    void requireValidQuantity_rejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> OrderConstraints.requireValidQuantity(0));
        assertThrows(IllegalArgumentException.class, () -> OrderConstraints.requireValidQuantity(-5));
    }

    // Ensures valid inputs are accepted unchanged.
    @Test
    void requireValidValues_acceptsPositive() {
        assertEquals(OrderSide.BUY, OrderConstraints.requireValidSide(OrderSide.BUY));
        assertEquals(10L, OrderConstraints.requireValidPrice(10));
        assertEquals(7L, OrderConstraints.requireValidQuantity(7));
    }
}
