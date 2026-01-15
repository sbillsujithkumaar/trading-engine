package tradingengine.domain;

import java.time.Instant;
import java.util.UUID;

public class Order {

    // Immutable order fields
    private final String id = UUID.randomUUID().toString();
    private final OrderSide side;
    private final long price;
    private final Instant timestamp = Instant.now();

    // Mutable order fields
    private long remainingQty;
    private OrderStatus status = OrderStatus.NEW;

    public Order(OrderSide side, long price, long quantity) {
        this.side = OrderConstraints.requireValidSide(side);
        this.price = OrderConstraints.requireValidPrice(price);
        this.remainingQty = OrderConstraints.requireValidQuantity(quantity);
    }

    public String getId() {
        return id;
    }
    public OrderSide getSide() {
        return side;
    }
    public long getPrice() {
        return price;
    }
    public Instant getTimestamp() {
        return timestamp;
    }
    public long getRemainingQty() {
        return remainingQty;
    }
    public OrderStatus getStatus() {
        return status;
    }

    /** Check if this order can match with an opposing order at the given price 
     * @param opposingPrice The price of the opposing order
     * @return true if the orders can match, false otherwise
     */
    public boolean canMatch(long opposingPrice) {
        return side == OrderSide.BUY
                ? opposingPrice <= price
                : opposingPrice >= price;
    }

    /**
     * Execute the order for the requested quantity
     * @param requestedQty The quantity to fill i.e. resting or incoming quantity
     * @return The actual quantity filled
     */
    public long execute(long requestedQty) {
        // Invariant: Remaining quantity cannot be negative
        long filled = Math.min(requestedQty, remainingQty);
        remainingQty -= filled;

        // Invariant: order status must reflect remaining quantity
        if (remainingQty == 0) {
            status = OrderStatus.FILLED;
        } else {
            status = OrderStatus.PARTIALLY_FILLED;
        }

        return filled;
    }

    /** Check if the order is still active (i.e., can be matched) 
     * @return true if the order is active, false otherwise
     */
    public boolean isActive() {
        return status == OrderStatus.NEW || status == OrderStatus.PARTIALLY_FILLED;
    }

}
