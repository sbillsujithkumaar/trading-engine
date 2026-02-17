package tradingengine.ops;

import tradingengine.matchingengine.MatchingEngine;
import tradingengine.websocket.MarketDataBroadcaster;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds shared runtime state for operational endpoints.
 *
 * Instead of letting servlets reach directly into engine internals,
 * this class centralizes service-level state (readiness + counters).
 */
public final class EngineRuntime {

    // Used to calculate uptime for /metrics
    private final Instant startTime = Instant.now();

    // Core components
    private final MatchingEngine engine;
    private final MarketDataBroadcaster broadcaster;

    // Marks whether the service is ready to accept traffic
    private final AtomicBoolean ready = new AtomicBoolean(false);

    // Lightweight counters exposed via /metrics
    private final AtomicLong ordersReceived = new AtomicLong();
    private final AtomicLong cancelsReceived = new AtomicLong();
    private final AtomicLong tradesExecuted = new AtomicLong();
    private final AtomicLong rejects = new AtomicLong();

    public EngineRuntime(MatchingEngine engine, MarketDataBroadcaster broadcaster) {
        this.engine = Objects.requireNonNull(engine);
        this.broadcaster = Objects.requireNonNull(broadcaster);
    }

    public Instant startTime() { return startTime; }

    public MatchingEngine engine() { return engine; }

    public MarketDataBroadcaster broadcaster() { return broadcaster; }

    public boolean isReady() { return ready.get(); }

    public void setReady(boolean value) { ready.set(value); }

    // Counter helpers

    public void incOrdersReceived() { ordersReceived.incrementAndGet(); }

    public void incCancelsReceived() { cancelsReceived.incrementAndGet(); }

    public void addTradesExecuted(long n) { tradesExecuted.addAndGet(n); }

    public void incRejects() { rejects.incrementAndGet(); }

    public long ordersReceived() { return ordersReceived.get(); }

    public long cancelsReceived() { return cancelsReceived.get(); }

    public long tradesExecuted() { return tradesExecuted.get(); }

    public long rejects() { return rejects.get(); }
}