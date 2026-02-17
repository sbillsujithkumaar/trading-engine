package tradingengine.ops;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tradingengine.domain.Trade;

import java.io.IOException;
import java.util.List;

/**
 * GET /api/trades?limit=50
 *
 * Plain-text trade dump so UI can verify executions quickly.
 */
public final class TradesApiServlet extends HttpServlet {
    private final EngineRuntime runtime;

    public TradesApiServlet(EngineRuntime runtime) {
        this.runtime = runtime;
    }

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
    }
}