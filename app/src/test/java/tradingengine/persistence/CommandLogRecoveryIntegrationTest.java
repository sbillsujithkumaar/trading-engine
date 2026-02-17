package tradingengine.persistence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tradingengine.book.OrderBook;
import tradingengine.domain.Order;
import tradingengine.domain.OrderSide;
import tradingengine.domain.Trade;
import tradingengine.events.EventDispatcher;
import tradingengine.matchingengine.MatchingEngine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

// Integration tests for WAL integrity and deterministic replay/recovery behavior.
class CommandLogRecoveryIntegrationTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    private Path commandsPath() {
        return tempDir.resolve("commands.log");
    }

    private Path tradesPath() {
        return tempDir.resolve("trades.csv");
    }

    private MatchingEngine newEngine(Path commands, Path trades) {
        return new MatchingEngine(
                new OrderBook(),
                FIXED_CLOCK,
                new EventDispatcher(),
                new FileTradeStore(trades),
                new CommandLog(commands)
        );
    }

    private static Order order(OrderSide side, long price, long quantity) {
        return new Order(side, price, quantity, Instant.now(FIXED_CLOCK));
    }

    private static void replay(CommandLog log, MatchingEngine engine) {
        engine.setReplayMode(true);
        try {
            for (CommandLog.Record record : log.readAll()) {
                if (record.type == CommandLog.Type.ORDER) {
                    engine.submit(new Order(
                            record.orderId,
                            OrderSide.valueOf(record.side),
                            record.price,
                            record.quantity,
                            record.timestamp
                    ));
                } else if (record.type == CommandLog.Type.CANCEL) {
                    engine.cancel(record.cancelOrderId);
                }
            }
        } finally {
            engine.setReplayMode(false);
        }
    }

    private static void runScenario(MatchingEngine engine) {
        engine.submit(order(OrderSide.BUY, 100, 10));
        engine.submit(order(OrderSide.BUY, 99, 5));
        engine.submit(order(OrderSide.SELL, 100, 3));
        engine.submit(order(OrderSide.SELL, 100, 7));
    }

    private static void seedThreeRecords(CommandLog log) {
        log.appendOrder("order-1", "BUY", 100, 5, Instant.parse("2026-01-01T00:00:00Z"));
        log.appendOrder("order-2", "SELL", 101, 4, Instant.parse("2026-01-01T00:00:01Z"));
        log.appendCancel("order-1", Instant.parse("2026-01-01T00:00:02Z"));
    }

    // Rationale: The same command history must rebuild the exact same final book and trade stream after restart.
    @Test
    void replayDeterminismSameCommandsProduceSameFinalState() {
        Path commands = commandsPath();
        Path trades = tradesPath();

        MatchingEngine liveEngine = newEngine(commands, trades);
        runScenario(liveEngine);

        String expectedBookDump = liveEngine.getBook().dump();
        List<Trade> expectedTrades = new ArrayList<>(liveEngine.tradeHistory());
        int commandCountBeforeReplay = new CommandLog(commands).readAll().size();

        FileTradeStore recoveredStore = new FileTradeStore(trades);
        recoveredStore.clear();
        MatchingEngine recoveredEngine = newEngine(commands, trades);
        replay(new CommandLog(commands), recoveredEngine);

        assertEquals(expectedBookDump, recoveredEngine.getBook().dump());
        assertEquals(expectedTrades, recoveredEngine.tradeHistory());
        assertEquals(commandCountBeforeReplay, new CommandLog(commands).readAll().size());
    }

    // Rationale: Recovery should regenerate trades exactly once, not accumulate duplicates across restarts.
    @Test
    void replayAfterClearDoesNotDuplicateTrades() {
        Path commands = commandsPath();
        Path trades = tradesPath();

        MatchingEngine liveEngine = newEngine(commands, trades);
        runScenario(liveEngine);

        int initialTradeCount = liveEngine.tradeHistory().size();
        assertEquals(2, initialTradeCount);

        FileTradeStore recoveredStore = new FileTradeStore(trades);
        recoveredStore.clear();
        MatchingEngine recoveredEngine = newEngine(commands, trades);
        replay(new CommandLog(commands), recoveredEngine);

        assertEquals(initialTradeCount, recoveredEngine.tradeHistory().size());
    }

    // Rationale: If any field in a persisted record is tampered, hash verification must fail on startup.
    @Test
    void verifyChainFailsWhenLineIsModified() throws IOException {
        CommandLog log = new CommandLog(commandsPath());
        seedThreeRecords(log);

        List<String> lines = Files.readAllLines(commandsPath(), StandardCharsets.UTF_8);
        lines.set(0, lines.get(0).replace("\"price\":100", "\"price\":999"));
        Files.write(commandsPath(), lines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);

        assertThrows(IllegalStateException.class, log::verifyChainOrThrow);
    }

    // Rationale: Removing a middle record should break the prevHash linkage and be detected immediately.
    @Test
    void verifyChainFailsWhenLineIsDeleted() throws IOException {
        CommandLog log = new CommandLog(commandsPath());
        seedThreeRecords(log);

        List<String> lines = Files.readAllLines(commandsPath(), StandardCharsets.UTF_8);
        lines.remove(1);
        Files.write(commandsPath(), lines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);

        assertThrows(IllegalStateException.class, log::verifyChainOrThrow);
    }

    // Rationale: Reordering valid lines should still fail because the chain captures record order, not just content.
    @Test
    void verifyChainFailsWhenLinesAreReordered() throws IOException {
        CommandLog log = new CommandLog(commandsPath());
        seedThreeRecords(log);

        List<String> lines = Files.readAllLines(commandsPath(), StandardCharsets.UTF_8);
        String first = lines.get(0);
        lines.set(0, lines.get(1));
        lines.set(1, first);
        Files.write(commandsPath(), lines, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);

        assertThrows(IllegalStateException.class, log::verifyChainOrThrow);
    }

    // Rationale: Corruption such as truncated JSON should fail startup validation rather than replay partial state.
    @Test
    void verifyChainFailsWhenLogIsTruncated() throws IOException {
        CommandLog log = new CommandLog(commandsPath());
        seedThreeRecords(log);

        List<String> lines = Files.readAllLines(commandsPath(), StandardCharsets.UTF_8);
        String truncated = lines.get(0).substring(0, lines.get(0).length() / 2);
        Files.writeString(
                commandsPath(),
                truncated,
                StandardCharsets.UTF_8,
                StandardOpenOption.TRUNCATE_EXISTING
        );

        assertThrows(RuntimeException.class, log::verifyChainOrThrow);
    }

    // Rationale: After restart, newly appended records must continue the same hash chain from the previous tail.
    @Test
    void hashChainContinuesAcrossRestarts() {
        Path commands = commandsPath();
        CommandLog firstRunLog = new CommandLog(commands);

        firstRunLog.appendOrder("first", "BUY", 100, 1, Instant.parse("2026-01-01T00:00:00Z"));
        String firstHash = firstRunLog.readAll().get(0).hash;

        CommandLog secondRunLog = new CommandLog(commands);
        secondRunLog.appendCancel("first", Instant.parse("2026-01-01T00:00:01Z"));

        List<CommandLog.Record> records = secondRunLog.readAll();
        assertEquals(2, records.size());
        assertEquals(firstHash, records.get(1).prevHash);
    }

    // Rationale: Successful cancel after partial fill must be logged for replay so the remaining quantity is removed.
    @Test
    void successfulCancelAfterPartialFillAppendsCancelRecord() {
        Path commands = commandsPath();
        Path trades = tradesPath();
        MatchingEngine engine = newEngine(commands, trades);

        Order sell = order(OrderSide.SELL, 100, 10);
        engine.submit(sell);
        engine.submit(order(OrderSide.BUY, 100, 4));

        assertTrue(engine.cancel(sell.getId()));

        List<CommandLog.Record> records = new CommandLog(commands).readAll();
        assertEquals(3, records.size());
        CommandLog.Record last = records.get(2);
        assertEquals(CommandLog.Type.CANCEL, last.type);
        assertEquals(sell.getId(), last.cancelOrderId);
    }

    // Rationale: Cancelling an already filled order must not append a CANCEL command because nothing changed.
    @Test
    void cancelFilledOrderDoesNotAppendCancelRecord() {
        Path commands = commandsPath();
        Path trades = tradesPath();
        MatchingEngine engine = newEngine(commands, trades);

        Order sell = order(OrderSide.SELL, 100, 5);
        engine.submit(sell);
        engine.submit(order(OrderSide.BUY, 100, 5));

        assertFalse(engine.cancel(sell.getId()));

        List<CommandLog.Record> records = new CommandLog(commands).readAll();
        assertEquals(2, records.size());
        assertTrue(records.stream().noneMatch(record -> record.type == CommandLog.Type.CANCEL));
    }
}
