package tradingengine.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TradeTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void constructor_rejectsNullTimestamp() {
        assertThrows(NullPointerException.class,
                () -> new Trade("b", "s", 100, 1, null));
    }

    @Test
    void constructor_rejectsInvalidOrderIds() {
        assertThrows(IllegalArgumentException.class,
                () -> new Trade("", "s", 100, 1, FIXED_TIME));
        assertThrows(IllegalArgumentException.class,
                () -> new Trade("b", "   ", 100, 1, FIXED_TIME));
    }

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
