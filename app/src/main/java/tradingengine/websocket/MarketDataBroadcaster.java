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

    public void register(ClientSession client) {
        clients.add(client);
    }

    public void unregister(ClientSession client) {
        clients.remove(client);
    }

    public void onTradeExecuted(TradeExecutedEvent event) {
        TradeMessage message = toTradeMessage(event);
        broadcast(MessageSerializer.toJson(message));
    }

    public void onOrderBookEvent(OrderBookEvent event) {
        OrderBookMessage message = toOrderBookMessage(event);
        broadcast(MessageSerializer.toJson(message));
    }

    TradeMessage toTradeMessage(TradeExecutedEvent event) {
        return new TradeMessage(
                MessageType.TRADE.name(),
                event.trade().price(),
                event.trade().quantity(),
                event.trade().timestamp()
        );
    }

    OrderBookMessage toOrderBookMessage(OrderBookEvent event) {
        return new OrderBookMessage(
                MessageType.BOOK_UPDATE.name(),
                event.side().name(),
                event.price(),
                event.type().name()
        );
    }

    private void broadcast(String json) {
        for (ClientSession client : clients) {
            client.send(json);
        }
    }
}
