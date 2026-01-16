package tradingengine.domain;

import java.time.Instant;
import java.util.Objects;

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

    public Trade {
        buyOrderId = TradeConstraints.requireValidOrderId(buyOrderId, "Buy");
        sellOrderId = TradeConstraints.requireValidOrderId(sellOrderId, "Sell");
        price = TradeConstraints.requireValidPrice(price);
        quantity = TradeConstraints.requireValidQuantity(quantity);
        timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
    }
}
