package tradingengine.book;

import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;

import java.util.Objects;

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
    public void addOrder(Order order) {
        // Validate input - cannot be null or inactive
        Objects.requireNonNull(order, "order must not be null");
        if (!order.isActive()) {
            throw new IllegalArgumentException("inactive orders cannot be added to the book");
        }


        if (order.getSide() == OrderSide.BUY) {
            buySide.addOrder(order);
        } else {
            sellSide.addOrder(order);
        }
    }

    /**
     * @return {@code true} if both BUY and SELL sides are empty
     */
    public boolean isEmpty() {
        return buySide.isEmpty() && sellSide.isEmpty();
    }

    /**
     * @return {@code true} if at least one resting BUY order exist
     */
    public boolean hasBuyResting() {
        return !buySide.isEmpty();
    }

    /**
     * @return {@code true} if at least one resting SELL order exist
     */
    public boolean hasSellResting() {
        return !sellSide.isEmpty();
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
     * Returns whether the incoming order can execute immediately against
     * the current best resting order on the opposite side.
     *
     * <p>This is a matching-time predicate (executability), not just "does liquidity exist".
     *
     * @param incoming the incoming order
     * @return true if there is resting liquidity AND prices cross for this incoming order
     */
    public boolean canExecuteIncoming(Order incoming) {
        if (incoming.getSide() == OrderSide.BUY) {
            return !sellSide.isEmpty() && incoming.canMatch(sellSide.bestPrice());
        } else {
            return !buySide.isEmpty() && incoming.canMatch(buySide.bestPrice());
        }
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
