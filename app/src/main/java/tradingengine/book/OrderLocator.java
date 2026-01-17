package tradingengine.book;

import tradingengine.domain.OrderSide;

import java.util.Objects;

/**
 * Points to an order's current side and price level.
 */
public record OrderLocator(OrderSide side, long price) {
    public OrderLocator {
        Objects.requireNonNull(side, "side must not be null");
        if (price <= 0) {
            throw new IllegalArgumentException("price must be positive");
        }
    }
}
