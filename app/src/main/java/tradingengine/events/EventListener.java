package tradingengine.events;

/**
 * Listener for engine events.
 */
@FunctionalInterface
public interface EventListener<E extends EngineEvent> {
    /**
     * Handle an event published by the engine.
     *
     * @param event the event instance
     */
    void onEvent(E event);
}
