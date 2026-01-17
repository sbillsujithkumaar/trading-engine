package tradingengine.websocket;

import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

/**
 * WebSocket endpoint for streaming market data.
 * 
 */
@WebSocket
public class EngineWebSocket {

    private final MarketDataBroadcaster broadcaster;
    private ClientSession client;

    public EngineWebSocket(MarketDataBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    /* Handles new client connections.
    * @param session the WebSocket session
    */
    @OnWebSocketConnect
    public void onConnect(Session session) {
        client = new ClientSession(session);
        broadcaster.register(client);
    }

    /* Handles client disconnections.
    * @param statusCode the close status code
    * @param reason the reason for closure
    */
    @OnWebSocketClose
    public void onClose(int statusCode, String reason) {
        broadcaster.unregister(client);
    }
}
