package tradingengine.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests for TradeConstraints validation rules.
class TradeConstraintsValidationTest {

    // Ensures order id validation rejects null or blank strings.
    @Test
    void requireValidOrderId_rejectsNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> TradeConstraints.requireValidOrderId(null, "Buy"));
        assertThrows(IllegalArgumentException.class, () -> TradeConstraints.requireValidOrderId("", "Buy"));
        assertThrows(IllegalArgumentException.class, () -> TradeConstraints.requireValidOrderId("   ", "Sell"));
    }

    // Ensures trade price validation rejects non-positive values.
    @Test
    void requireValidPrice_rejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> TradeConstraints.requireValidPrice(0));
        assertThrows(IllegalArgumentException.class, () -> TradeConstraints.requireValidPrice(-10));
    }

    // Ensures trade quantity validation rejects non-positive values.
    @Test
    void requireValidQuantity_rejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> TradeConstraints.requireValidQuantity(0));
        assertThrows(IllegalArgumentException.class, () -> TradeConstraints.requireValidQuantity(-3));
    }

    // Ensures valid trade inputs are accepted unchanged.
    @Test
    void requireValidValues_acceptsPositive() {
        assertEquals("id", TradeConstraints.requireValidOrderId("id", "Buy"));
        assertEquals(10L, TradeConstraints.requireValidPrice(10));
        assertEquals(7L, TradeConstraints.requireValidQuantity(7));
    }
}
