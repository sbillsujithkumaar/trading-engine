package tradingengine.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tradingengine.domain.Trade;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * GET /api/trades?limit=50
 *
 * JSON snapshot for UI state synchronization.
 * Optional fallback: ?format=text for human-readable dump.
 */
public final class TradesApiServlet extends HttpServlet {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final EngineRuntime runtime;

    public TradesApiServlet(EngineRuntime runtime) {
        this.runtime = runtime;
    }

    record TradeSnapshot(String buyOrderId, String sellOrderId, long price, long qty, String timestamp, String info) {}
    record TradesSnapshot(List<TradeSnapshot> trades) {}

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        int limit = 50;
        String s = req.getParameter("limit");
        if (s != null) {
            try { limit = Math.max(1, Math.min(500, Integer.parseInt(s))); }
            catch (NumberFormatException ignored) {}
        }

        List<Trade> all = runtime.engine().tradeHistory();
        int from = Math.max(0, all.size() - limit);

        String format = req.getParameter("format");
        if ("text".equalsIgnoreCase(format)) {
            resp.setStatus(200);
            resp.setContentType("text/plain; charset=utf-8");

            if (all.isEmpty()) {
                resp.getWriter().println("(no trades yet)");
                return;
            }

            for (Trade t : all.subList(from, all.size())) {
                resp.getWriter().println(
                        t.timestamp() + " price=" + t.price() +
                                " qty=" + t.quantity() +
                                " buy=" + t.buyOrderId().substring(0, 8) +
                                " sell=" + t.sellOrderId().substring(0, 8)
                );
            }
            return;
        }

        List<TradeSnapshot> trades = toSnapshots(all.subList(from, all.size()));
        resp.setStatus(200);
        resp.setContentType("application/json; charset=utf-8");
        MAPPER.writeValue(resp.getOutputStream(), new TradesSnapshot(trades));
    }

    private static List<TradeSnapshot> toSnapshots(List<Trade> trades) {
        List<TradeSnapshot> snapshots = new ArrayList<>(trades.size());
        for (int i = trades.size() - 1; i >= 0; i--) {
            Trade t = trades.get(i);
            snapshots.add(new TradeSnapshot(
                    t.buyOrderId(),
                    t.sellOrderId(),
                    t.price(),
                    t.quantity(),
                    t.timestamp().toString(),
                    "buy=" + shortId(t.buyOrderId()) + " sell=" + shortId(t.sellOrderId())
            ));
        }
        return snapshots;
    }

    private static String shortId(String id) {
        int n = Math.min(8, id.length());
        return id.substring(0, n);
    }
}
