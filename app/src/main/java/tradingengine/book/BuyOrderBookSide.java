package tradingengine.book;

import java.util.Comparator;

/**
 * BUY side of the order book.
 *
 * <p>Orders on this side are prioritized by:
 * <ul>
 *   <li>Higher price first</li>
 *   <li>FIFO ordering within the same price level</li>
 * </ul>
 */
public class BuyOrderBookSide extends OrderBookSide {

    /**
     * Constructs a BUY-side order book with prices ordered
     * from highest to lowest.
     */
    public BuyOrderBookSide() {
        super(Comparator.reverseOrder());
    }
}
