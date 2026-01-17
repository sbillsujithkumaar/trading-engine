package tradingengine.events.listeners;

import tradingengine.events.OrderBookEvent;
import tradingengine.events.TradeExecutedEvent;

/**
 * Simple listener that logs events to stdout.
 */
public final class LoggingEventListener {

    /**
     * Log a trade execution to stdout.
     *
     * @param event the trade event
     */
    public void onTradeExecuted(TradeExecutedEvent event) {
        System.out.println("TRADE: " + event.trade());
    }

    /**
     * Log a book update to stdout.
     *
     * @param event the book event
     */
    public void onOrderBookEvent(OrderBookEvent event) {
        System.out.println("BOOK: " + event.type() + " " + event.side() + " @" + event.price());
    }
}
