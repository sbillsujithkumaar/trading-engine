package tradingengine.websocket;

import org.junit.jupiter.api.Test;
import tradingengine.domain.OrderSide;
import tradingengine.domain.Trade;
import tradingengine.events.OrderBookEvent;
import tradingengine.events.OrderBookEventType;
import tradingengine.events.TradeExecutedEvent;
import tradingengine.websocket.messages.MessageType;
import tradingengine.websocket.messages.OrderBookMessage;
import tradingengine.websocket.messages.TradeMessage;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

// Unit tests for WebSocket message mapping.
class MarketDataBroadcasterTest {

    // Ensures trade events map to TRADE messages with correct fields.
    @Test
    void tradeEventMapsToTradeMessage() {
        MarketDataBroadcaster broadcaster = new MarketDataBroadcaster();
        Trade trade = new Trade("b1", "s1", 100, 5, Instant.parse("2026-01-01T00:00:00Z"));
        TradeExecutedEvent event = new TradeExecutedEvent(trade, trade.timestamp());

        TradeMessage message = broadcaster.toTradeMessage(event);

        assertEquals(MessageType.TRADE.name(), message.type());
        assertEquals(100, message.price());
        assertEquals(5, message.quantity());
        assertEquals(trade.timestamp(), message.timestamp());
    }

    // Ensures book events map to BOOK_UPDATE messages with correct fields.
    @Test
    void bookEventMapsToOrderBookMessage() {
        MarketDataBroadcaster broadcaster = new MarketDataBroadcaster();
        OrderBookEvent event = new OrderBookEvent(
                OrderSide.BUY,
                101,
                OrderBookEventType.ADD,
                Instant.parse("2026-01-01T00:00:00Z")
        );

        OrderBookMessage message = broadcaster.toOrderBookMessage(event);

        assertEquals(MessageType.BOOK_UPDATE.name(), message.type());
        assertEquals("BUY", message.side());
        assertEquals(101, message.price());
        assertEquals(OrderBookEventType.ADD.name(), message.action());
    }
}
