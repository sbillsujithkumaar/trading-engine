package tradingengine.websocket.messages;

/**
 * Order book update message sent to WebSocket clients.
 */
public record OrderBookMessage(
        String type,
        String side,
        long price,
        String action
) {
}
