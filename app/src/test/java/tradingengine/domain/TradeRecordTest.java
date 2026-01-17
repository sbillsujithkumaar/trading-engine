package tradingengine.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

// Unit tests for Trade record validation and field mapping.
class TradeRecordTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");

    // Ensures null timestamps are rejected.
    @Test
    void constructor_rejectsNullTimestamp() {
        assertThrows(NullPointerException.class,
                () -> new Trade("b", "s", 100, 1, null));
    }

    // Ensures invalid order IDs are rejected.
    @Test
    void constructor_rejectsInvalidOrderIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new Trade("", "s", 100, 1, FIXED_TIME));
        assertThrows(IllegalArgumentException.class,
                () -> new Trade("b", "   ", 100, 1, FIXED_TIME));
    }

    // Ensures constructor assigns fields correctly.
    @Test
    void constructor_setsFields() {
        Trade trade = new Trade("b", "s", 100, 2, FIXED_TIME);

        assertEquals("b", trade.buyOrderId());
        assertEquals("s", trade.sellOrderId());
        assertEquals(100, trade.price());
        assertEquals(2, trade.quantity());
        assertEquals(FIXED_TIME, trade.timestamp());
    }
}
