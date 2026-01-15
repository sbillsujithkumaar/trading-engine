package tradingengine.book;

import java.util.Comparator;

/**
 * SELL side of the order book.
 *
 * <p>Orders on this side are prioritized by:
 * <ul>
 *   <li>Lower price first</li>
 *   <li>FIFO ordering within the same price level</li>
 * </ul>
 */
public class SellOrderBookSide extends OrderBookSide {

    /**
     * Constructs a SELL-side order book with prices ordered
     * from lowest to highest.
     */
    public SellOrderBookSide() {
        super(Comparator.naturalOrder());
    }
}
