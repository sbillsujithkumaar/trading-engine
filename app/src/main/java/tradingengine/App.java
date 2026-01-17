package tradingengine;

import org.eclipse.jetty.server.Server;
import tradingengine.book.OrderBook;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;
import tradingengine.events.EventDispatcher;
import tradingengine.events.OrderBookEvent;
import tradingengine.events.TradeExecutedEvent;
import tradingengine.matchingengine.MatchingEngine;
import tradingengine.websocket.MarketDataBroadcaster;
import tradingengine.websocket.WebSocketServer;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class App {

    public static void main(String[] args) throws Exception {
        int intervalSeconds = 2;
        if (args.length > 0) {
            try {
                intervalSeconds = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                intervalSeconds = 2;
            }
        }

        EventDispatcher dispatcher = new EventDispatcher();
        MarketDataBroadcaster broadcaster = new MarketDataBroadcaster();

        dispatcher.register(TradeExecutedEvent.class, broadcaster::onTradeExecuted);
        dispatcher.register(OrderBookEvent.class, broadcaster::onOrderBookEvent);

        MatchingEngine engine = new MatchingEngine(new OrderBook(), Clock.systemUTC(), dispatcher);
        Server server = WebSocketServer.start(broadcaster, 8080);

        System.out.println("WebSocket server running on ws://localhost:8080");

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            Instant now = Instant.now();
            engine.submit(new Order(OrderSide.SELL, 101, 5, now));
            engine.submit(new Order(OrderSide.BUY, 101, 5, now.plusMillis(1)));
        }, 0, intervalSeconds, TimeUnit.SECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::shutdownNow));

        server.join();
    }
}
