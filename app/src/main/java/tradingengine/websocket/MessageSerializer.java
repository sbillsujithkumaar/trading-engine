package tradingengine.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON serializer for outbound WebSocket messages.
 */
public final class MessageSerializer {

    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MessageSerializer() {
    }

    public static String toJson(Object message) {
        try {
            return mapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }
}
