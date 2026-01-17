package tradingengine.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Synchronous event dispatcher.
 * Guarantees event ordering per publish call.
 */
public final class EventDispatcher {

    private final Map<Class<?>, List<EventListener<?>>> listeners = new HashMap<>();

    /**
     * Register a listener for a specific event type.
     *
     * @param eventType the event class to subscribe to
     * @param listener the listener to invoke
     * @param <E> the event type
     */
    public <E extends EngineEvent> void register(
            Class<E> eventType,
            EventListener<E> listener
    ) {
        Objects.requireNonNull(eventType, "eventType must not be null");
        Objects.requireNonNull(listener, "listener must not be null");

        listeners
                .computeIfAbsent(eventType, key -> new ArrayList<>())
                .add(listener);
    }

    /**
     * Publish an event to all listeners registered for its type.
     *
     * @param event the event to dispatch
     */
    @SuppressWarnings("unchecked")
    public void publish(EngineEvent event) {
        Objects.requireNonNull(event, "event must not be null");

        List<EventListener<?>> registered = listeners.get(event.getClass());
        if (registered != null) {
            for (EventListener<?> listener : registered) {
                ((EventListener<EngineEvent>) listener).onEvent(event);
            }
        }

        List<EventListener<?>> wildcard = listeners.get(EngineEvent.class);
        if (wildcard != null) {
            for (EventListener<?> listener : wildcard) {
                ((EventListener<EngineEvent>) listener).onEvent(event);
            }
        }
    }
}
