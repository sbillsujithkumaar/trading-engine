package tradingengine.persistence;

import tradingengine.domain.Trade;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Objects;


public class FileTradeStore {

    private final Path file;

    public FileTradeStore(Path file) {
        this.file = Objects.requireNonNull(file, "file must not be null");
    }

    public void save(Trade trade) {
        Objects.requireNonNull(trade, "trade must not be null");
        appendToFile(trade);
    }

    public List<Trade> findAll() {
        if (!Files.exists(file)) {
            return List.of();
        }

        try (var lines = Files.lines(file, StandardCharsets.UTF_8)) {
            return lines.filter(line -> !line.isBlank())
                    .map(this::deserialize)
                    .toList();
        } catch (IOException e) {
            throw new RuntimeException("Failed to load trades", e);
        }
    }

    /** Appends a trade to the CSV file.
     * @param trade the trade to append
     */
    private void appendToFile(Trade trade) {
        try {
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            try (BufferedWriter writer = Files.newBufferedWriter(
                    file,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.APPEND
            )) {
                writer.write(serialize(trade));
                writer.newLine();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to persist trade", e);
        }
    }

    private String serialize(Trade trade) {
        return String.join(",",
                trade.buyOrderId(),
                trade.sellOrderId(),
                Long.toString(trade.price()),
                Long.toString(trade.quantity()),
                trade.timestamp().toString()
        );
    }

    private Trade deserialize(String line) {
        String[] parts = line.split(",", -1);
        if (parts.length != 5) {
            throw new IllegalArgumentException("Invalid trade record: " + line);
        }
        return new Trade(
                parts[0],
                parts[1],
                Long.parseLong(parts[2]),
                Long.parseLong(parts[3]),
                java.time.Instant.parse(parts[4])
        );
    }
}
