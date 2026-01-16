package tradingengine.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TradeConstraintsTest {

    @Test
    void requireValidOrderId_rejectsNullOrBlank() {
        assertThrows(IllegalArgumentException.class, () -> TradeConstraints.requireValidOrderId(null, "Buy"));
        assertThrows(IllegalArgumentException.class, () -> TradeConstraints.requireValidOrderId("", "Buy"));
        assertThrows(IllegalArgumentException.class, () -> TradeConstraints.requireValidOrderId("   ", "Sell"));
    }

    @Test
    void requireValidPrice_rejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> TradeConstraints.requireValidPrice(0));
        assertThrows(IllegalArgumentException.class, () -> TradeConstraints.requireValidPrice(-10));
    }

    @Test
    void requireValidQuantity_rejectsNonPositive() {
        assertThrows(IllegalArgumentException.class, () -> TradeConstraints.requireValidQuantity(0));
        assertThrows(IllegalArgumentException.class, () -> TradeConstraints.requireValidQuantity(-3));
    }

    @Test
    void requireValidValues_acceptsPositive() {
        assertEquals("id", TradeConstraints.requireValidOrderId("id", "Buy"));
        assertEquals(10L, TradeConstraints.requireValidPrice(10));
        assertEquals(7L, TradeConstraints.requireValidQuantity(7));
    }
}
