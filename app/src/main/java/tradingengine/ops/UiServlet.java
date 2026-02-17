package tradingengine.ops;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;

/**
 * Simple servlet to serve the static HTML UI for local demos.
 */
public final class UiServlet extends HttpServlet {

    private static final String UI_RESOURCE = "/ui/index.html";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try (InputStream in = UiServlet.class.getResourceAsStream(UI_RESOURCE)) {
            if (in == null) {
                resp.setStatus(500);
                resp.setContentType("text/plain; charset=utf-8");
                resp.getWriter().println("UI resource not found: " + UI_RESOURCE);
                return;
            }

            resp.setStatus(200);
            resp.setContentType("text/html; charset=utf-8");
            in.transferTo(resp.getOutputStream());
        }
    }
}