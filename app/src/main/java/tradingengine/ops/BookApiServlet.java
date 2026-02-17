package tradingengine.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tradingengine.book.OrderBookSide;

import java.io.IOException;
import java.util.List;

/**
 * GET /api/book
 *
 * JSON snapshot for UI state synchronization.
 * Optional fallback: ?format=text for human-readable dump.
 */
public final class BookApiServlet extends HttpServlet {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final EngineRuntime runtime;

    public BookApiServlet(EngineRuntime runtime) {
        this.runtime = runtime;
    }

    record BookSnapshot(List<OrderBookSide.LevelSnapshot> bids, List<OrderBookSide.LevelSnapshot> asks) {}

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String format = req.getParameter("format");
        if ("text".equalsIgnoreCase(format)) {
            resp.setStatus(200);
            resp.setContentType("text/plain; charset=utf-8");
            resp.getWriter().print(runtime.engine().getBook().dump());
            return;
        }

        List<OrderBookSide.LevelSnapshot> bids = runtime.engine().getBook().buySide().snapshotLevels();
        List<OrderBookSide.LevelSnapshot> asks = runtime.engine().getBook().sellSide().snapshotLevels();

        resp.setStatus(200);
        resp.setContentType("application/json; charset=utf-8");
        MAPPER.writeValue(resp.getOutputStream(), new BookSnapshot(bids, asks));
    }
}
