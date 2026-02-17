package tradingengine.ops;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/**
 * Simple servlet to serve the static HTML UI for local demos.
 */
public final class UiServlet extends HttpServlet {

    private static final String UI_RESOURCE_ROOT = "/ui";
    private static final String DEFAULT_UI_RESOURCE = "/ui/index.html";

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        String resourcePath = resolveResourcePath(req);
        if (resourcePath == null) {
            resp.setStatus(400);
            resp.setContentType("text/plain; charset=utf-8");
            resp.getWriter().println("Invalid UI resource path");
            return;
        }

        try (InputStream in = UiServlet.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                resp.setStatus(404);
                resp.setContentType("text/plain; charset=utf-8");
                resp.getWriter().println("UI resource not found: " + resourcePath);
                return;
            }

            resp.setStatus(200);
            resp.setContentType(contentType(resourcePath));
            resp.setHeader("Cache-Control", "no-store");
            in.transferTo(resp.getOutputStream());
        }
    }

    private static String resolveResourcePath(HttpServletRequest req) {
        String pathInfo = req.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank() || "/".equals(pathInfo)) {
            return DEFAULT_UI_RESOURCE;
        }
        if (pathInfo.contains("..") || pathInfo.contains("\\")) {
            return null;
        }
        return UI_RESOURCE_ROOT + pathInfo;
    }

    private static String contentType(String resourcePath) {
        String lower = resourcePath.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) return "text/html; charset=utf-8";
        if (lower.endsWith(".css")) return "text/css; charset=utf-8";
        if (lower.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (lower.endsWith(".json")) return "application/json; charset=utf-8";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
}
