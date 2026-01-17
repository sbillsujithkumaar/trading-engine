package tradingengine.events.listeners;

import tradingengine.events.EngineEvent;
import tradingengine.events.EventListener;

import java.util.ArrayList;
import java.util.List;

/**
 * Listener that captures events for testing and inspection.
 */
public final class CapturingEventListener<E extends EngineEvent> implements EventListener<E> {

    private final List<E> events = new ArrayList<>();

    @Override
    public void onEvent(E event) {
        events.add(event);
    }

    /**
     * @return an immutable snapshot of captured events
     */
    public List<E> events() {
        return List.copyOf(events);
    }
}
