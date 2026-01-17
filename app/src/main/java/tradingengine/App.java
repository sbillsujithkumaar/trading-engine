package tradingengine;

import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;
import tradingengine.domain.Trade;
import tradingengine.matchingengine.MatchingEngine;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

public class App {

    public static void main(String[] args) {
        MatchingEngine engine = new MatchingEngine(
                new tradingengine.book.OrderBook(),
                Clock.systemUTC()
        );

        Instant now = Instant.now();

        // Submit SELL first (resting)
        engine.submit(new Order(OrderSide.SELL, 101, 5, now));

        // Submit BUY that crosses
        List<Trade> trades = engine.submit(
                new Order(OrderSide.BUY, 101, 3, now.plusSeconds(1))
        );

        trades.forEach(System.out::println);
    }
}
