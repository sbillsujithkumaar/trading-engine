package tradingengine.ops;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Readiness endpoint that indicates whether the service can accept traffic.
 *
 * Returns {@code 200 OK} when ready, otherwise {@code 503 Service Unavailable}.
 *
 * Difference from {@code /health}:
 * - {@code /health}: process exists (alive).
 * - {@code /ready}: safe to send requests (ready).
 *
 * Readiness is controlled by startup/shutdown orchestration through {@link EngineRuntime}.
 */
public final class ReadyServlet extends HttpServlet {
    private final EngineRuntime runtime;

    public ReadyServlet(EngineRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        if (runtime.isReady()) {
            resp.setStatus(200);
            resp.setContentType("text/plain");
            resp.getWriter().println("READY");
        } else {
            resp.setStatus(503);
            resp.setContentType("text/plain");
            resp.getWriter().println("NOT_READY");
        }
    }
}
