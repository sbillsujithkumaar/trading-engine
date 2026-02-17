package tradingengine;

import org.eclipse.jetty.server.Server;
import tradingengine.book.OrderBook;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;
import tradingengine.events.EventDispatcher;
import tradingengine.events.OrderBookEvent;
import tradingengine.events.TradeExecutedEvent;
import tradingengine.matchingengine.MatchingEngine;
import tradingengine.ops.EngineRuntime;
import tradingengine.persistence.FileTradeStore;
import tradingengine.websocket.MarketDataBroadcaster;
import tradingengine.websocket.WebSocketServer;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;

/**
 * Entry point that wires together:
 * - core engine
 * - event listeners
 * - persistence
 * - server interfaces
 */
public class App {

    public static void main(String[] args) throws Exception {
        EventDispatcher dispatcher = new EventDispatcher();

        // Turns engine events into messages for connected WS clients.
        MarketDataBroadcaster broadcaster = new MarketDataBroadcaster();
        dispatcher.register(TradeExecutedEvent.class, broadcaster::onTradeExecuted);
        dispatcher.register(OrderBookEvent.class, broadcaster::onOrderBookEvent);

        // TradeStore persists executed trades to disk (existing behaviour).
        FileTradeStore tradeStore = new FileTradeStore(Path.of("data", "trades.csv"));

        // MatchingEngine holds the core order book + matching rules.
        MatchingEngine engine = new MatchingEngine(new OrderBook(), Clock.systemUTC(), dispatcher, tradeStore);

        // Shared runtime state read by ops endpoints and dev APIs.
        EngineRuntime runtime = new EngineRuntime(engine, broadcaster);

        Server server = WebSocketServer.start(runtime, 8080);

        System.out.println("UI: http://localhost:8080/ui");
        System.out.println("Ops: /health /ready /metrics");
        System.out.println("APIs: POST /api/order, POST /api/cancel");
        System.out.println("WebSocket: ws://localhost:8080/ws");

        // Small bootstrap demo so UI has something to show on first run.
        Instant now = Instant.now();
        engine.submit(new Order(OrderSide.SELL, 101, 5, now));
        engine.submit(new Order(OrderSide.BUY, 101, 5, now.plusMillis(1)));

        server.join();
    }
}