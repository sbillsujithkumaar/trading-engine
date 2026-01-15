package tradingengine.domain;

/**
 * Domain constraints for Order-related values.
 * These express business rules, not generic validation.
 */
public final class OrderConstraints {

    private OrderConstraints() {
    }

    public static OrderSide requireValidSide(OrderSide side) {
        if (side == null) {
            throw new IllegalArgumentException("Order side must be BUY or SELL");
        }
        return side;
    }

    public static long requireValidPrice(long price) {
        if (price <= 0) {
            throw new IllegalArgumentException("Order price must be positive");
        }
        return price;
    }

    public static long requireValidQuantity(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Order quantity must be positive");
        }
        return quantity;
    }
}
