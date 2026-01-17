package tradingengine.websocket.messages;

import java.time.Instant;

/**
 * Trade message sent to WebSocket clients.
 */
public record TradeMessage(
        String type,
        long price,
        long quantity,
        Instant timestamp
) {
}
