package tradingengine.websocket;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import tradingengine.ops.*;

/**
 * Single place that exposes all service endpoints.
 *
 * I separate interfaces by path:
 * - UI pages live on HTTP routes (/ui)
 * - ops endpoints live on HTTP routes (/health, /metrics)
 * - WebSocket streaming lives on /ws
 *
 * This makes it easy to demo and keeps protocol routes stable.
 */
public final class WebSocketServer {

    private WebSocketServer() {}

    public static Server start(EngineRuntime runtime, int port) throws Exception {
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(server, "/");

        // UI routes
        context.addServlet(RootRedirectServlet.class, "/");
        context.addServlet(UiServlet.class, "/ui");
        context.addServlet(UiServlet.class, "/ui/*");

        // Ops routes
        context.addServlet(HealthServlet.class, "/health");
        context.addServlet(new ServletHolder(new ReadyServlet(runtime)), "/ready");
        context.addServlet(new ServletHolder(new MetricsServlet(runtime)), "/metrics");

        // Dev APIs (need runtime, so instantiated manually)
        context.addServlet(new ServletHolder(new OrderApiServlet(runtime)), "/api/order");
        context.addServlet(new ServletHolder(new CancelApiServlet(runtime)), "/api/cancel");
        context.addServlet(new ServletHolder(new BookApiServlet(runtime)), "/api/book");
        context.addServlet(new ServletHolder(new TradesApiServlet(runtime)), "/api/trades");


        // WebSocket streaming endpoint
        JettyWebSocketServletContainerInitializer.configure(
                context,
                (servletContext, container) ->
                        container.addMapping("/ws", (req, resp) -> new EngineWebSocket(runtime.broadcaster()))
        );

        server.start();
        return server;
    }
}
