package tradingengine.websocket;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;

/**
 * Starts a Jetty WebSocket server for market data streaming.
 */
public final class WebSocketServer {

    private WebSocketServer() {
    }

    /** Starts the WebSocket server.
     * @param broadcaster the market data broadcaster
     * @param port the port to listen on
     * @return the started Jetty server
     * @throws Exception if server fails to start
     */ 
    public static Server start(MarketDataBroadcaster broadcaster, int port) throws Exception {
        Server server = new Server(port);
        ServletContextHandler context = new ServletContextHandler(server, "/");

        JettyWebSocketServletContainerInitializer.configure(
                context,
                (servletContext, container) ->
                        container.addMapping("/", (req, resp) -> new EngineWebSocket(broadcaster))
        );

        server.start();
        return server;
    }
}
