package tradingengine.book;

import tradingengine.domain.Order;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
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
 *  - Support immediate cancellation by order id
 *
 * This class is agnostic to BUY vs SELL.
 * Price ordering is injected via a Comparator.
 */
public class OrderBookSide {
    /**
     * One resting order snapshot inside a price level.
     *
     * @param orderId order identifier
     * @param qty remaining quantity for this order
     */
    public record RestingOrderSnapshot(String orderId, long qty) {}

    /**
     * Aggregated snapshot row for one price level.
     *
     * @param price  the price level
     * @param qty    total resting quantity at this price
     * @param count  number of resting orders at this price
     * @param orders resting orders at this level in FIFO order
     */
    public record LevelSnapshot(long price, long qty, int count, List<RestingOrderSnapshot> orders) {}

    /**
     * Mapping from price -> FIFO queue of orders at that price.
     *
     * TreeMap enforces price ordering:
     *  - BUY side: highest price first
     *  - SELL side: lowest price first
     */
    private final NavigableMap<Long, OrdersQueue> priceLevels;

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
     * @return true if this side has no resting orders (i.e. no prices)
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
     * Returns the oldest resting order at the best price level, or null if empty.
     *
     * @return the best resting order, or {@code null} if the book is empty
     */
    public Order peekBestOrderOrNull() {
        if (priceLevels.isEmpty()) {
            return null;
        }

        return priceLevels.firstEntry()
                .getValue()
                .peekOldest();
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
    public void addRestingOrder(Order order) {
        Objects.requireNonNull(order, "order must not be null");

        long price = order.getPrice();
        OrdersQueue queue = priceLevels.get(price);
        if (queue == null) {
            queue = new OrdersQueue();
            priceLevels.put(price, queue);
        }

        queue.add(order);
    }

    /**
     * Remove the head order at the best price level if it is inactive.
     *
     * @return the removed order, or {@code null} if nothing was removed
     */
    public Order removeBestOrderIfInactive() {
        var bestPriceLevel = priceLevels.firstEntry();
        if (bestPriceLevel == null) {
            return null;
        }

        OrdersQueue queue = bestPriceLevel.getValue();
        Order head = queue.peekOldest();
        if (head == null) {
            return null;
        }

        if (!head.isActive()) {
            Order removed = queue.removeOldest();
            if (queue.isEmpty()) {
                priceLevels.pollFirstEntry();
            }
            return removed;
        }

        return null;
    }

    /**
     * Cancel a resting order by id using its locator.
     *
     * @param orderId the order id to cancel
     * @param locator the side/price locator
     * @return {@code true} if the order was removed
     */
    public boolean cancelOrderById(String orderId, OrderLocator locator) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        Objects.requireNonNull(locator, "locator must not be null");

        // find the price level
        OrdersQueue queue = priceLevels.get(locator.price());
        if (queue == null) {
            return false;
        }

        
        Order removed = queue.removeById(orderId);
        if (removed == null) {
            return false;
        }

        removed.cancel();
        if (queue.isEmpty()) {
            priceLevels.remove(locator.price());
        }

        return true;
    }

    /**
     * Dumps this side of the book in priority order.
     * For quick UI/debugging.
     */
    public String dumpSide(String label) {
        StringBuilder sb = new StringBuilder();
        sb.append(label).append("\n");

        if (priceLevels.isEmpty()) {
            sb.append("(empty)\n");
            return sb.toString();
        }

        for (var entry : priceLevels.entrySet()) {
            long price = entry.getKey();
            OrdersQueue q = entry.getValue();

            sb.append("price=").append(price).append("\n");
            for (Order o : q.snapshotFifo()) {
                sb.append("  ")
                  .append(o.getId(), 0, Math.min(8, o.getId().length()))
                  .append(" qty=").append(o.getRemainingQty())
                  .append(" status=").append(o.getStatus().name())
                  .append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Returns an aggregated, immutable snapshot in current price-priority order.
     * Used by HTTP snapshot APIs.
     */
    public List<LevelSnapshot> snapshotLevels() {
        List<LevelSnapshot> levels = new ArrayList<>(priceLevels.size());
        for (var entry : priceLevels.entrySet()) {
            long price = entry.getKey();
            OrdersQueue queue = entry.getValue();
            long qty = 0L;
            int count = 0;
            List<RestingOrderSnapshot> orders = new ArrayList<>();
            for (Order order : queue.snapshotFifo()) {
                if (!order.isActive()) {
                    continue;
                }
                long remaining = order.getRemainingQty();
                if (remaining <= 0) {
                    continue;
                }
                qty += remaining;
                count++;
                orders.add(new RestingOrderSnapshot(order.getId(), remaining));
            }
            if (count > 0) {
                levels.add(new LevelSnapshot(price, qty, count, List.copyOf(orders)));
            }
        }
        return List.copyOf(levels);
    }
}
