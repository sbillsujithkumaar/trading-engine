package tradingengine;

import org.eclipse.jetty.server.Server;
import tradingengine.analytics.AnalyticsJob;
import tradingengine.analytics.AnalyticsStore;
import tradingengine.book.OrderBook;
import tradingengine.book.OrderBookSide;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;
import tradingengine.domain.Trade;
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
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
        Path analyticsPath = Path.of(dataDir, "analytics.csv");

        CommandLog commandLog = new CommandLog(commandsPath);
        FileTradeStore tradeStore = new FileTradeStore(tradesPath);
        AnalyticsStore analyticsStore = new AnalyticsStore(analyticsPath);

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

        AnalyticsJob analyticsJob = new AnalyticsJob(
                analyticsStore,
                new AnalyticsJob.SnapshotProvider() {
                    @Override
                    public List<OrderBookSide.LevelSnapshot> currentBids() {
                        return runtime.engine().getBook().buySide().snapshotLevels();
                    }

                    @Override
                    public List<OrderBookSide.LevelSnapshot> currentAsks() {
                        return runtime.engine().getBook().sellSide().snapshotLevels();
                    }

                    @Override
                    public List<Trade> currentTrades() {
                        return runtime.engine().tradeHistory();
                    }
                },
                Clock.systemUTC()
        );

        Runnable runAnalyticsSafely = () -> {
            try {
                analyticsJob.runOnce();
            } catch (Exception e) {
                System.err.println("Analytics job failed: " + e.getMessage());
                e.printStackTrace(System.err);
            }
        };

        // Ensure a snapshot exists on startup before readiness flips.
        runAnalyticsSafely.run();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "analytics-job");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(runAnalyticsSafely, 30, 30, TimeUnit.SECONDS);

        Server server = WebSocketServer.start(runtime, analyticsStore, 8080);
        runtime.setReady(true);

        System.out.println("UI: http://localhost:8080/ui");
        System.out.println("Ops: /health /ready /metrics");
        System.out.println("APIs: POST /api/order, POST /api/cancel, GET /api/book, GET /api/trades, GET /api/analytics");
        System.out.println("WebSocket: ws://localhost:8080/ws");

        try {
            server.join();
        } finally {
            runtime.setReady(false);
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                scheduler.shutdownNow();
            }
        }
    }
}
