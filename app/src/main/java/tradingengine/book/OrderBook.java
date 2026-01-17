package tradingengine.book;

import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Represents the complete order book
 *
 * <p>The order book maintains both BUY and SELL sides and exposes
 * price-level state required by the matching engine.
 *
 * <p>This class does not perform trade matching or execution. Cancellations
 * remove orders immediately to keep top-of-book queries accurate.
 */
public class OrderBook {

    private final OrderBookSide buySide;
    private final OrderBookSide sellSide;
    private final Map<String, OrderLocator> orderIndex;

    /**
     * Creates an empty order book.
     */
    public OrderBook() {
        this.buySide = new OrderBookSide(Comparator.reverseOrder());
        this.sellSide = new OrderBookSide(Comparator.naturalOrder());
        this.orderIndex = new HashMap<>();
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
            buySide.addRestingOrder(order);
        } else {
            sellSide.addRestingOrder(order);
        }
        orderIndex.put(order.getId(), new OrderLocator(order.getSide(), order.getPrice()));
    }

    /**
     * Cancel an order by id. Cancellation is idempotent and removes immediately.
     *
     * @param orderId the order id to cancel
     * @return {@code true} if the order was removed
     */
    public boolean cancelOrder(String orderId) {
        return cancelOrderAndGetLocator(orderId).isPresent();
    }

    /**
     * Cancel an order by id and return its locator when present.
     *
     * @param orderId the order id to cancel
     * @return locator of the cancelled order, or empty if not found
     * @implNote Used by the matching engine to emit cancel events with side and price.
     */
    public Optional<OrderLocator> cancelOrderAndGetLocator(String orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        OrderLocator locator = orderIndex.get(orderId);
        if (locator == null) {
            return Optional.empty();
        }
        OrderBookSide side = sideFor(locator.side());
        boolean removed = side.cancelOrderById(orderId, locator);
        orderIndex.remove(orderId);
        if (!removed) {
            return Optional.empty();
        }
        return Optional.of(locator);
    }

    /**
     * Remove the best resting order if it is inactive.
     *
     * @param side the side to clean
     * @return the removed order, or {@code null} if nothing was removed
     */
    public Order removeBestOrderIfInactive(OrderSide side) {
        // find the correct side
        OrderBookSide bookSide = sideFor(side);
        
        // attempt to remove the best order if inactive
        Order removed = bookSide.removeBestOrderIfInactive();

        // if an order was removed, also remove it from the index
        if (removed != null) {
            orderIndex.remove(removed.getId());
        }
        
        return removed;
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

    private OrderBookSide sideFor(OrderSide side) {
        return (side == OrderSide.BUY) ? buySide : sellSide;
    }

    /**
     * @return the BUY side of the order book
     */
    public OrderBookSide buySide() {
        return buySide;
    }

    /**
     * @return the SELL side of the order book
     */
    public OrderBookSide sellSide() {
        return sellSide;
    }
}
