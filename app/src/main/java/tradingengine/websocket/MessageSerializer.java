package tradingengine.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * JSON serializer for outbound WebSocket messages.
 */
public final class MessageSerializer {

    // Configure ObjectMapper for JSON serialization
    private static final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private MessageSerializer() {
    }

    /** Serializes a message object to its JSON representation.
     * @param message the message object to serialize
     * @return the JSON string representation of the message
     */
    public static String toJson(Object message) {
        try {
            return mapper.writeValueAsString(message);
        } catch (Exception e) {
            throw new RuntimeException("Serialization failed", e);
        }
    }
}
