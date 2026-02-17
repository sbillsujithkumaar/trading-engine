package tradingengine.ops;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Liveness endpoint for load balancers and orchestration platforms.
 *
 * Returns {@code 200 OK} as long as the process is running.
 *
 * Difference from {@code /ready}:
 * - {@code /health}: process exists (alive).
 * - {@code /ready}: safe to send requests (ready).
 *
 * This implementation only checks process liveness and does not perform dependency checks.
 */
public final class HealthServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(200);
        resp.setContentType("text/plain");
        resp.getWriter().println("OK");
    }
}
