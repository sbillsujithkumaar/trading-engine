package tradingengine.book;

import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;

/**
 * Represents the complete order book
 *
 * <p>The order book maintains both BUY and SELL sides and exposes
 * price-level state required by the matching engine.
 *
 * <p>This class does not perform trade matching or execution.
 */
public class OrderBook {

    private final BuyOrderBookSide buySide;
    private final SellOrderBookSide sellSide;

    /**
     * Creates an empty order book.
     */
    public OrderBook() {
        this.buySide = new BuyOrderBookSide();
        this.sellSide = new SellOrderBookSide();
    }

    /**
     * Adds an order to the appropriate side of the book
     * based on its {@link OrderSide}.
     *
     * @param order the order to add
     */
    public void add(Order order) {
        if (order.getSide() == OrderSide.BUY) {
            buySide.add(order);
        } else {
            sellSide.add(order);
        }
    }

    /**
     * @return {@code true} if both BUY and SELL sides are empty
     */
    public boolean isEmpty() {
        return buySide.isEmpty() && sellSide.isEmpty();
    }

    /**
     * @return {@code true} if at least one BUY and one SELL order exist
     */
    public boolean hasBothSides() {
        return !buySide.isEmpty() && !sellSide.isEmpty();
    }

    /**
     * Returns the current best bid price.
     *
     * @return highest BUY price
     * @throws IllegalStateException if no BUY orders exist
     */
    public long bestBid() {
        if (buySide.isEmpty()) {
            throw new IllegalStateException("No BUY orders in book");
        }
        return buySide.bestPrice();
    }

    /**
     * Returns the current best ask price.
     *
     * @return lowest SELL price
     * @throws IllegalStateException if no SELL orders exist
     */
    public long bestAsk() {
        if (sellSide.isEmpty()) {
            throw new IllegalStateException("No SELL orders in book");
        }
        return sellSide.bestPrice();
    }

    /**
     * Determines whether price crossing exists in the book.
     *
     * <p>
     * A crossing occurs when:
     * <pre>
     * bestBid >= bestAsk
     * </pre>
     *
     * @return {@code true} if matching is possible
     */
    public boolean hasCrossingPrices() {
        if (!hasBothSides()) {
            return false;
        }
        return bestBid() >= bestAsk();
    }

    /**
     * @return the BUY side of the order book
     */
    public BuyOrderBookSide buySide() {
        return buySide;
    }

    /**
     * @return the SELL side of the order book
     */
    public SellOrderBookSide sellSide() {
        return sellSide;
    }
}
