package tradingengine.ops;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tradingengine.analytics.AnalyticsStore;

import java.io.IOException;
import java.util.Optional;

/**
 * GET /api/analytics
 *
 * Returns the latest persisted analytics snapshot as CSV text.
 *
 * <p>This endpoint is read-only and does not recompute analytics.
 * It only serves whatever is currently stored on disk.
 */
public final class AnalyticsServlet extends HttpServlet {
    private final AnalyticsStore store;

    /**
     * @param store persistence layer that owns analytics.csv
     */
    public AnalyticsServlet(AnalyticsStore store) {
        this.store = store;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // If no snapshot exists yet, return 404 so clients know to retry later.
        Optional<String> csv = store.readLatestCsv();
        if (csv.isEmpty()) {
            resp.setStatus(404);
            resp.setContentType("text/plain; charset=utf-8");
            resp.getWriter().write("No analytics snapshot available yet.\n");
            return;
        }

        // Return raw CSV so users/tools can parse it directly.
        resp.setStatus(200);
        resp.setContentType("text/csv; charset=utf-8");
        resp.getWriter().write(csv.get());
    }
}
