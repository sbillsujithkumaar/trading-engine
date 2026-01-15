package tradingengine.book;

import tradingengine.domain.Order;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Represents one side of an order book (either BUY or SELL).
 *
 * Responsibilities:
 *  - Maintain price priority using a sorted map
 *  - Maintain time priority (FIFO) within each price level
 *  - Support fast access to the "best" price level
 *
 * This class is agnostic to BUY vs SELL.
 * Price ordering is injected via a Comparator.
 */
public class OrderBookSide {

    /**
     * Mapping from price → FIFO queue of orders at that price.
     *
     * TreeMap enforces price ordering:
     *  - BUY side: highest price first
     *  - SELL side: lowest price first
     *
     * Deque enforces FIFO ordering within the same price.
     */
    private final NavigableMap<Long, Deque<Order>> priceLevels;

    /**
     * Create an OrderBookSide with a given price ordering rule.
     *
     * @param priceComparator Comparator defining price priority
     *                        (e.g. reverseOrder for BUY, naturalOrder for SELL)
     */
    public OrderBookSide(Comparator<Long> priceComparator) {
        this.priceLevels = new TreeMap<>(priceComparator);
    }

    /**
     * @return true if this side has no resting orders
     */
    public boolean isEmpty() {
        return priceLevels.isEmpty();
    }

    /**
     * @return the best price currently available on this side
     * @throws NoSuchElementException if the book is empty
     */
    public long bestPrice() {
        return priceLevels.firstKey();
    }

    /**
     * Returns the oldest resting order at the best price level.
     *
     * @return the best resting order
     * @throws IllegalStateException if the book side is empty
     */
    public Order peekBestOrder() {
        if (priceLevels.isEmpty()) {
            throw new IllegalStateException("Cannot peek order from empty book side");
        }

        return priceLevels.firstEntry()
                .getValue()
                .peekFirst();
    }

    /**
     * Add a new order to this side of the book.
     *
     * Orders are:
     *  - grouped by price
     *  - appended to the end of the FIFO queue at that price
     *
     * @param order the order to add
     */
    public void add(Order order) {
        Objects.requireNonNull(order, "order must not be null");

        long price = order.getPrice();
        Deque<Order> queue = priceLevels.get(price);
        if (queue == null) {
            queue = new ArrayDeque<>();
            priceLevels.put(price, queue);
        }

        queue.addLast(order);
    }

    /**
     * Remove the head order at the best price level
     * if it is no longer active - FILLED or CANCELLED.
     * If the price level becomes empty, it is also removed entirely.
     */
    public void removeHeadIfInactive() {
        var bestPriceLevel = priceLevels.firstEntry();
        if (bestPriceLevel == null) {
            return;
        }

        Deque<Order> queue = bestPriceLevel.getValue();
        Order head = queue.peekFirst();
 
        // Remove if inactive - FILLED or CANCELLED
        if (!head.isActive()) {
            // Only the oldest order at the best price is eligible for removal (FIFO).
            queue.pollFirst();
            if (queue.isEmpty()) {
                priceLevels.pollFirstEntry();
            }
        }
    }
}
