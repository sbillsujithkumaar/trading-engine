package tradingengine.websocket;

import tradingengine.events.OrderBookEvent;
import tradingengine.events.TradeExecutedEvent;
import tradingengine.websocket.messages.MessageType;
import tradingengine.websocket.messages.OrderBookMessage;
import tradingengine.websocket.messages.TradeMessage;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Broadcasts engine events to all connected WebSocket clients.
 */
public class MarketDataBroadcaster {

    private final Set<ClientSession> clients = new CopyOnWriteArraySet<>();

    /** Registers a new client session to receive market data.
     * @param client the client session to register
     */
    public void register(ClientSession client) {
        clients.add(client);
    }

    /** Unregisters a client session from receiving market data.
     * @param client the client session to unregister
     */
    public void unregister(ClientSession client) {
        clients.remove(client);
    }

    /** Handles a trade executed event and broadcasts it to all clients.
     * @param event the trade executed event
     */
    public void onTradeExecuted(TradeExecutedEvent event) {
        TradeMessage message = toTradeMessage(event);
        broadcast(MessageSerializer.toJson(message));
    }

    /** Handles an order book event and broadcasts it to all clients.
     * @param event the order book event
     */
    public void onOrderBookEvent(OrderBookEvent event) {
        OrderBookMessage message = toOrderBookMessage(event);
        broadcast(MessageSerializer.toJson(message));
    }

    /** Converts a TradeExecutedEvent to a TradeMessage.
     * @param event the trade executed event
     * @return the corresponding trade message
     */
    TradeMessage toTradeMessage(TradeExecutedEvent event) {
        return new TradeMessage(
                MessageType.TRADE.name(),
                event.trade().price(),
                event.trade().quantity(),
                event.trade().timestamp()
        );
    }

    /** Converts an OrderBookEvent to an OrderBookMessage.
     * @param event the order book event
     * @return the corresponding order book message
     */
    OrderBookMessage toOrderBookMessage(OrderBookEvent event) {
        return new OrderBookMessage(
                MessageType.BOOK_UPDATE.name(),
                event.side().name(),
                event.price(),
                event.type().name()
        );
    }

    /** Broadcasts a JSON message to all connected clients.
     * @param json the JSON message to broadcast
     */
    private void broadcast(String json) {
        for (ClientSession client : clients) {
            client.send(json);
        }
    }

    /**
     * Used by /metrics to expose how many WS clients are connected.
     * Kept here so ops code doesn't need to touch the internal clients set directly.
     */
    public int connectedClients() {
        return clients.size();
    }
}
