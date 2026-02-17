package tradingengine.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Append-only write-ahead log of accepted commands (ORDER/CANCEL)
 *
 * Stored as JSON Lines so each record is independently appendable and replayable
 */
public final class CommandLog {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    public enum Type { ORDER, CANCEL }

    public static final class Record {
        public Type type;

        // ORDER fields
        public String orderId;
        public String side;
        public long price;
        public long quantity;
        public Instant timestamp;

        // CANCEL fields
        public String cancelOrderId;

        public Record() {
            // Required by Jackson
        }
    }

    private final Path path;

    public CommandLog(Path path) {
        this.path = Objects.requireNonNull(path, "path must not be null");
    }

    public synchronized void appendOrder(String orderId, String side, long price, long quantity, Instant ts) {
        Record record = new Record();
        record.type = Type.ORDER;
        record.orderId = orderId;
        record.side = side;
        record.price = price;
        record.quantity = quantity;
        record.timestamp = ts;
        append(record);
    }

    public synchronized void appendCancel(String cancelOrderId, Instant ts) {
        Record record = new Record();
        record.type = Type.CANCEL;
        record.cancelOrderId = cancelOrderId;
        record.timestamp = ts;
        append(record);
    }

    private void append(Record record) {
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }

            String line = MAPPER.writeValueAsString(record);
            try (BufferedWriter writer = Files.newBufferedWriter(
                    path,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            )) {
                writer.write(line);
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to append to command log: " + path, e);
        }
    }

    public List<Record> readAll() {
        try {
            if (!Files.exists(path)) {
                return List.of();
            }

            List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
            List<Record> records = new ArrayList<>(lines.size());
            for (String line : lines) {
                if (line == null || line.isBlank()) {
                    continue;
                }
                records.add(MAPPER.readValue(line, Record.class));
            }
            return records;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read command log: " + path, e);
        }
    }

    public Path path() {
        return path;
    }
}
