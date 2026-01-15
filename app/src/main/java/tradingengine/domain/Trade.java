package tradingengine.domain;

import java.time.Instant;

/**
 * A Trade represents a completed execution between a buy order and a sell order.
 * Trades are immutable records -> more efficient than classes
 */
public record Trade(
        String buyOrderId,
        String sellOrderId,
        long price,
        long quantity,
        Instant timestamp
) {

    public Trade(String buyOrderId, String sellOrderId, long price, long quantity) {
        this(
        TradeConstraints.requireValidOrderId(buyOrderId, "Buy"),
        TradeConstraints.requireValidOrderId(sellOrderId, "Sell"),
        TradeConstraints.requireValidPrice(price),
        TradeConstraints.requireValidQuantity(quantity),
        Instant.now()
        );
    }
}
