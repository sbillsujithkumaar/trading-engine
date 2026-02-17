package tradingengine.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;
import tradingengine.domain.Trade;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * POST /api/order
 *
 * This is a small "developer-facing" API so I can test the engine with curl/Postman
 * (without needing a custom WS client).
 */
public final class OrderApiServlet extends HttpServlet {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final EngineRuntime runtime;

    public OrderApiServlet(EngineRuntime runtime) {
        this.runtime = runtime;
    }

    // Minimal JSON payload for placing an order.
    static final class OrderRequest {
        public String side;     // BUY / SELL
        public long price;
        public long quantity;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            // Jackson APIs are not null-annotated; suppress Eclipse's unsafe @NonNull generic inference here.
            @SuppressWarnings("null")
            OrderRequest body = MAPPER.readValue(req.getInputStream(), OrderRequest.class);

            if (body.side == null) {
                runtime.incRejects();
                resp.setStatus(400);
                resp.setContentType("application/json; charset=utf-8");
                MAPPER.writeValue(resp.getOutputStream(), Map.of("accepted", false, "error", "side is required"));
                return;
            }

            OrderSide side = OrderSide.valueOf(body.side.trim().toUpperCase(Locale.ROOT));

            // Timestamp included so order creation is explicit (useful for auditing later).
            Order order = new Order(side, body.price, body.quantity, Instant.now());

            runtime.incOrdersReceived();
            List<Trade> trades = runtime.engine().submit(order);
            runtime.addTradesExecuted(trades.size());

            resp.setStatus(200);
            resp.setContentType("application/json; charset=utf-8");
            MAPPER.writeValue(resp.getOutputStream(), Map.of(
                    "accepted", true,
                    "orderId", order.getId(),
                    "trades", trades.size()
            ));
        } catch (Exception e) {
            runtime.incRejects();
            resp.setStatus(400);
            resp.setContentType("application/json; charset=utf-8");
            MAPPER.writeValue(resp.getOutputStream(), Map.of(
                    "accepted", false,
                    "error", e.getMessage()
            ));
        }
    }
}
