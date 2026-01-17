package tradingengine.events;

import tradingengine.domain.Trade;

import java.time.Instant;
import java.util.Objects;

/**
 * Emitted whenever a trade is successfully executed.
 */
public record TradeExecutedEvent(
        Trade trade,
        Instant timestamp
) implements EngineEvent {

    public TradeExecutedEvent {
        Objects.requireNonNull(trade, "trade must not be null");
        Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
