package tradingengine.events;

import java.time.Instant;

/**
 * Marker interface for all engine events.
 * Events are immutable facts emitted by the matching engine.
 */
public interface EngineEvent {
    /**
     * @return the event timestamp used for deterministic ordering and replay.
     */
    Instant timestamp();
}
