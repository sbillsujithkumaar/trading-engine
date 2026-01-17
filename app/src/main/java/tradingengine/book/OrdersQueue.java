package tradingengine.book;

import tradingengine.domain.Order;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Maintains FIFO ordering for orders at a single price level.
 */
public final class OrdersQueue {

    private final LinkedHashMap<String, Order> ordersQueue = new LinkedHashMap<>();

    /** @return true if there are no orders at this price level */
    public boolean isEmpty() {
        return ordersQueue.isEmpty();
    }

    /** Add a new order to this price level.
     * @param order The order to add
     */
    public void add(Order order) {
        Objects.requireNonNull(order, "order must not be null");
        ordersQueue.put(order.getId(), order);
    }

    /** Peek at the oldest order without removing it.
     * @return The oldest order or null if none
     */
    public Order peekOldest() {
        if (ordersQueue.isEmpty()) {
            return null;
        }
        return ordersQueue.values().iterator().next();
    }

    /** Remove and return the oldest order.
     * @return The removed order or null if none
     */
    public Order removeOldest() {
        var iterator = ordersQueue.entrySet().iterator();
        if (!iterator.hasNext()) {
            return null;
        }
        Map.Entry<String, Order> entry = iterator.next();
        iterator.remove();
        return entry.getValue();
    }

    /** Remove an order by its ID.
     * @param orderId The ID of the order to remove
     * @return The removed order or null if not found
     */
    public Order removeById(String orderId) {
        Objects.requireNonNull(orderId, "orderId must not be null");
        return ordersQueue.remove(orderId);
    }
}
