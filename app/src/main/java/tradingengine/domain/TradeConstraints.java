package tradingengine.domain;

/**
 * Domain constraints for Trade-related values.
 * These express business rules, not generic validation.
 */
public final class TradeConstraints {

    private TradeConstraints() {
    }

    public static String requireValidOrderId(String id, String role) {
        // Order ids can come from external systems -> reject blank values also as invalid.
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException(role + " order id must be present");
        }
        return id;
    }

    public static long requireValidPrice(long price) {
        if (price <= 0) {
            throw new IllegalArgumentException("Trade price must be positive");
        }
        return price;
    }

    public static long requireValidQuantity(long quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Trade quantity must be positive");
        }
        return quantity;
    }
}
