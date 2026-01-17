package tradingengine.websocket;

import org.eclipse.jetty.websocket.api.Session;

import java.io.IOException;
import java.util.Objects;

/**
 * Wrapper around a WebSocket session for safe sends.
 */
public class ClientSession {

    private final Session session;

    public ClientSession(Session session) {
        this.session = Objects.requireNonNull(session, "session must not be null");
    }

    public void send(String message) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.getRemote().sendString(message);
        } catch (IOException ignored) {
            // Client disconnects should not crash the engine.
        }
    }
}
