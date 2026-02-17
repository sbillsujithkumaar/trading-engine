package tradingengine.ops;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;

/**
 * POST /api/cancel
 *
 * Small endpoint for cancel testing and demos.
 */
public final class CancelApiServlet extends HttpServlet {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final EngineRuntime runtime;

    public CancelApiServlet(EngineRuntime runtime) {
        this.runtime = runtime;
    }

    static final class CancelRequest {
        public String orderId;
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            // Jackson APIs are not null-annotated; suppress Eclipse's unsafe @NonNull generic inference here.
            @SuppressWarnings("null")
            CancelRequest body = MAPPER.readValue(req.getInputStream(), CancelRequest.class);

            if (body.orderId == null || body.orderId.isBlank()) {
                runtime.incRejects();
                resp.setStatus(400);
                resp.setContentType("application/json; charset=utf-8");
                MAPPER.writeValue(resp.getOutputStream(), Map.of("ok", false, "error", "orderId is required"));
                return;
            }

            runtime.incCancelsReceived();
            boolean ok = runtime.engine().cancel(body.orderId);

            resp.setStatus(ok ? 200 : 404);
            resp.setContentType("application/json; charset=utf-8");
            MAPPER.writeValue(resp.getOutputStream(), Map.of("ok", ok, "orderId", body.orderId));
        } catch (Exception e) {
            runtime.incRejects();
            resp.setStatus(400);
            resp.setContentType("application/json; charset=utf-8");
            MAPPER.writeValue(resp.getOutputStream(), Map.of("ok", false, "error", e.getMessage()));
        }
    }
}
