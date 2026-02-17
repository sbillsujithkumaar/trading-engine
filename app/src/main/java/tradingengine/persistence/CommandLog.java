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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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

        // Hash-chain fields for tamper detection.
        public String prevHash;
        public String hash;

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

    private void append(Record r) {
        try {
            Files.createDirectories(path.getParent());

            // Previous hash = last record hash, or GENESIS if log is empty.
            String prev = "GENESIS";
            if (Files.exists(path)) {
                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                for (int i = lines.size() - 1; i >= 0; i--) {
                    String ln = lines.get(i);
                    if (ln == null || ln.isBlank()) {
                        continue;
                    }
                    // Jackson APIs are not null-annotated; suppress Eclipse's @NonNull generic inference warning.
                    @SuppressWarnings("null")
                    Record last = MAPPER.readValue(ln, Record.class);
                    if (last.hash != null && !last.hash.isBlank()) {
                        prev = last.hash;
                    }
                    break;
                }
            }

            r.prevHash = prev;

            // Hash = SHA256(prevHash + "|" + payload).
            String payload = payloadForHash(r);
            r.hash = sha256Hex(prev + "|" + payload);

            String line = MAPPER.writeValueAsString(r);

            try (BufferedWriter w = Files.newBufferedWriter(
                    path,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND
            )) {
                w.write(line);
                w.newLine();
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
                // Jackson APIs are not null-annotated; suppress Eclipse's @NonNull generic inference warning.
                @SuppressWarnings("null")
                Record parsed = MAPPER.readValue(line, Record.class);
                records.add(parsed);
            }
            return records;
        } catch (IOException e) {
            throw new RuntimeException("Failed to read command log: " + path, e);
        }
    }

    public void verifyChainOrThrow() {
        List<Record> all = readAll();
        String expectedPrev = "GENESIS";

        for (int i = 0; i < all.size(); i++) {
            Record r = all.get(i);

            if (!expectedPrev.equals(safe(r.prevHash))) {
                throw new IllegalStateException("Command log tampered: prevHash mismatch at line " + (i + 1));
            }

            String expectedHash = sha256Hex(expectedPrev + "|" + payloadForHash(r));
            if (!expectedHash.equals(safe(r.hash))) {
                throw new IllegalStateException("Command log tampered: hash mismatch at line " + (i + 1));
            }

            expectedPrev = r.hash;
        }
    }

    private static String sha256Hex(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] out = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(out.length * 2);
            for (byte b : out) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Use a stable string format for hashing (avoid JSON ordering issues).
     */
    private static String payloadForHash(Record r) {
        return String.join("|",
                "type=" + (r.type == null ? "" : r.type.name()),
                "orderId=" + safe(r.orderId),
                "side=" + safe(r.side),
                "price=" + r.price,
                "quantity=" + r.quantity,
                "cancelOrderId=" + safe(r.cancelOrderId),
                "timestamp=" + (r.timestamp == null ? "" : r.timestamp.toString())
        );
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    public Path path() {
        return path;
    }
}
