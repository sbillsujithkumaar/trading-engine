package tradingengine.book;

import tradingengine.domain.OrderSide;

/**
 * Points to an order's current side and price level.
 */
public record OrderLocator(OrderSide side, long price) {
}
