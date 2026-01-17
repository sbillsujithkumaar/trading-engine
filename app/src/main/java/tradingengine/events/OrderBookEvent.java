package tradingengine.events;

import tradingengine.domain.OrderSide;

import java.time.Instant;
import java.util.Objects;

/**
 * Emitted when the visible state of the order book changes.
 */
public record OrderBookEvent(
        OrderSide side,
        long price,
        OrderBookEventType type,
        Instant timestamp
) implements EngineEvent {

    public OrderBookEvent {
        Objects.requireNonNull(side, "side must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
        if (price <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
    }
}
