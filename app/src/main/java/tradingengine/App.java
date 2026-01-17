package tradingengine;

import org.eclipse.jetty.server.Server;
import tradingengine.book.OrderBook;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;
import tradingengine.events.EventDispatcher;
import tradingengine.events.OrderBookEvent;
import tradingengine.events.TradeExecutedEvent;
import tradingengine.matchingengine.MatchingEngine;
import tradingengine.persistence.FileTradeStore;
import tradingengine.websocket.MarketDataBroadcaster;
import tradingengine.websocket.WebSocketServer;

import java.time.Clock;
import java.time.Instant;
import java.nio.file.Path;

public class App {

    public static void main(String[] args) throws Exception {
        // Set up event dispatcher and broadcaster
        EventDispatcher dispatcher = new EventDispatcher();
        MarketDataBroadcaster broadcaster = new MarketDataBroadcaster();

        // Register event handlers
        dispatcher.register(TradeExecutedEvent.class, broadcaster::onTradeExecuted);
        dispatcher.register(OrderBookEvent.class, broadcaster::onOrderBookEvent);

        // Initialize trade store and matching engine
        FileTradeStore tradeStore = new FileTradeStore(Path.of("data", "trades.csv"));
        MatchingEngine engine = new MatchingEngine(new OrderBook(), Clock.systemUTC(), dispatcher, tradeStore);
        
        // Start WebSocket server
        Server server = WebSocketServer.start(broadcaster, 8080);

        System.out.println("WebSocket server running on ws://localhost:8080");

        // Example order submissions to demonstrate functionality
        Instant now = Instant.now();
        engine.submit(new Order(OrderSide.SELL, 101, 5, now));
        engine.submit(new Order(OrderSide.BUY, 101, 5, now.plusMillis(1)));

        // Keep the server running
        server.join();
    }
}
