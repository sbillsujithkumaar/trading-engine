package tradingengine.analytics;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static java.nio.file.StandardOpenOption.WRITE;

/**
 * Load step of the analytics pipeline.
 *
 * <p>Persists the latest analytics snapshot to CSV and reads it back for the API.
 */
public final class AnalyticsStore {

    // CSV header is fixed so downstream readers always see the same column order.
    private static final String HEADER = "timestamp,totalTrades,totalVolume,avgTradePrice,bestBid,bestAsk,openOrders\n";

    private final Path analyticsPath;

    /**
     * @param analyticsPath path of the persisted analytics CSV file
     */
    public AnalyticsStore(Path analyticsPath) {
        this.analyticsPath = Objects.requireNonNull(analyticsPath, "analyticsPath must not be null");
    }

    /**
     * Writes one snapshot (header + single data row) using a temp file and rename.
     *
     * <p>This avoids half-written analytics files if the process stops during write.
     */
    public void writeLatest(AnalyticsSnapshot snapshot) throws IOException {
        Objects.requireNonNull(snapshot, "snapshot must not be null");

        // Ensure DATA_DIR exists before writing files.
        Path parent = analyticsPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        // Keep decimal formatting stable using ROOT locale.
        String row = String.format(
                Locale.ROOT,
                "%s,%d,%d,%.5f,%s,%s,%d%n",
                snapshot.timestamp(),
                snapshot.totalTrades(),
                snapshot.totalVolume(),
                snapshot.avgTradePrice(),
                snapshot.bestBid() == null ? "" : Long.toString(snapshot.bestBid()),
                snapshot.bestAsk() == null ? "" : Long.toString(snapshot.bestAsk()),
                snapshot.openOrders()
        );

        // Write to temporary file first, then replace real file.
        Path tmp = analyticsPath.resolveSibling(analyticsPath.getFileName().toString() + ".tmp");
        Files.writeString(
                tmp,
                HEADER + row,
                StandardCharsets.UTF_8,
                CREATE,
                TRUNCATE_EXISTING,
                WRITE
        );

        try {
            // Best option on filesystems that support atomic moves.
            Files.move(
                    tmp,
                    analyticsPath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException ignored) {
            // Fallback still replaces old file safely, just not atomically.
            Files.move(tmp, analyticsPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /**
     * Reads the latest CSV contents.
     *
     * @return empty when file does not exist (or cannot be read)
     */
    public Optional<String> readLatestCsv() {
        if (!Files.exists(analyticsPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(analyticsPath, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    /**
     * Exposes the configured analytics path (helpful for debugging/tests).
     */
    public Path path() {
        return analyticsPath;
    }
}
