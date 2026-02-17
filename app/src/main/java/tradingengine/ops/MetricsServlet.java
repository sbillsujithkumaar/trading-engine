package tradingengine.ops;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

/**
 * Metrics endpoint that exposes runtime counters in plain text.
 *
 * Returns {@code 200 OK} with scrape-friendly metrics for monitoring and alerting.
 */
public final class MetricsServlet extends HttpServlet {
    private final EngineRuntime runtime;

    public MetricsServlet(EngineRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(200);
        resp.setContentType("text/plain; charset=utf-8");

        long uptimeSec = Duration.between(runtime.startTime(), Instant.now()).toSeconds();

        resp.getWriter().println("uptime_seconds " + uptimeSec);
        resp.getWriter().println("connected_clients " + runtime.broadcaster().connectedClients());
        resp.getWriter().println("orders_received " + runtime.ordersReceived());
        resp.getWriter().println("cancels_received " + runtime.cancelsReceived());
        resp.getWriter().println("trades_executed " + runtime.tradesExecuted());
        resp.getWriter().println("rejects " + runtime.rejects());
    }
}
