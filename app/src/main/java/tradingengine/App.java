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
import tradingengine.persistence.CommandLog;
import tradingengine.persistence.FileTradeStore;
import tradingengine.websocket.MarketDataBroadcaster;
import tradingengine.websocket.WebSocketServer;

import java.nio.file.Path;
import java.time.Clock;

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

        String dataDir = System.getenv().getOrDefault("DATA_DIR", "data");
        Path commandsPath = Path.of(dataDir, "commands.log");
        Path tradesPath = Path.of(dataDir, "trades.csv");

        CommandLog commandLog = new CommandLog(commandsPath);
        FileTradeStore tradeStore = new FileTradeStore(tradesPath);

        // MatchingEngine holds the core order book + matching rules.
        MatchingEngine engine = new MatchingEngine(
                new OrderBook(),
                Clock.systemUTC(),
                dispatcher,
                tradeStore,
                commandLog
        );

        // Verify command log integrity before replaying any state.
        commandLog.verifyChainOrThrow();

        // Rebuild trade history from the command log on every boot.
        tradeStore.clear();
        engine.setReplayMode(true);
        try {
            for (CommandLog.Record record : commandLog.readAll()) {
                if (record.type == CommandLog.Type.ORDER) {
                    Order replayOrder = new Order(
                            record.orderId,
                            OrderSide.valueOf(record.side),
                            record.price,
                            record.quantity,
                            record.timestamp
                    );
                    engine.submit(replayOrder);
                } else if (record.type == CommandLog.Type.CANCEL) {
                    engine.cancel(record.cancelOrderId);
                }
            }
        } finally {
            engine.setReplayMode(false);
        }

        // Shared runtime state read by ops endpoints and dev APIs.
        EngineRuntime runtime = new EngineRuntime(engine, broadcaster);

        Server server = WebSocketServer.start(runtime, 8080);
        runtime.setReady(true);

        System.out.println("UI: http://localhost:8080/ui");
        System.out.println("Ops: /health /ready /metrics");
        System.out.println("APIs: POST /api/order, POST /api/cancel");
        System.out.println("WebSocket: ws://localhost:8080/ws");

        server.join();
    }
}
