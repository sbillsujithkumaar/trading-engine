package tradingengine.ops;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * GET /api/book
 *
 * Plain-text book dump for UI/debugging.
 */
public final class BookApiServlet extends HttpServlet {
    private final EngineRuntime runtime;

    public BookApiServlet(EngineRuntime runtime) {
        this.runtime = runtime;
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        resp.setStatus(200);
        resp.setContentType("text/plain; charset=utf-8");
        resp.getWriter().print(runtime.engine().getBook().dump());
    }
}