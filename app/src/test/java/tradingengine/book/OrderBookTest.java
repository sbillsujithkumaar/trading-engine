package tradingengine.book;

import org.junit.jupiter.api.Test;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class OrderBookTest {

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void addOrder_rejectsNull() {
        OrderBook book = new OrderBook();

        assertThrows(NullPointerException.class, () -> book.addOrder(null));
    }

    @Test
    void addOrder_rejectsInactiveOrder() {
        OrderBook book = new OrderBook();
        Order order = new Order(OrderSide.BUY, 100, 1, FIXED_TIME);

        order.execute(1);

        assertFalse(order.isActive());
        assertThrows(IllegalArgumentException.class, () -> book.addOrder(order));
    }

    @Test
    void canExecuteIncoming_requiresPriceCross() {
        OrderBook book = new OrderBook();
        Order sell = new Order(OrderSide.SELL, 100, 1, FIXED_TIME);

        book.addOrder(sell);

        Order buyAtMarket = new Order(OrderSide.BUY, 100, 1, FIXED_TIME);
        Order buyBelow = new Order(OrderSide.BUY, 99, 1, FIXED_TIME);

        assertTrue(book.canExecuteIncoming(buyAtMarket));
        assertFalse(book.canExecuteIncoming(buyBelow));
    }

    @Test
    void bestBidAndAsk_throwWhenEmpty() {
        OrderBook book = new OrderBook();

        assertThrows(IllegalStateException.class, book::bestBid);
        assertThrows(IllegalStateException.class, book::bestAsk);
    }
}
