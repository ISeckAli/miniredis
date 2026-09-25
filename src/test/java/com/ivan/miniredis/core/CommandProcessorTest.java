package com.ivan.miniredis.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for CommandProcessor: parsing of SET/GET/DEL commands,
 * correct delegation to Store, handling of malformed input, and
 * write-ahead log integration (logging and replay).
 */
public class CommandProcessorTest {

    @TempDir
    Path tempDir; // used only by the persistence tests below

    @Test
    public void setStoresValueAndReturnsOk() {
        CommandProcessor processor = new CommandProcessor(new Store(10));

        String response = processor.process("SET username ivan");

        assertEquals("OK", response);
    }

    @Test
    public void getReturnsPreviouslySetValue() {
        CommandProcessor processor = new CommandProcessor(new Store(10));

        processor.process("SET username ivan");
        String response = processor.process("GET username");

        assertEquals("ivan", response);
    }

    @Test
    public void getOnMissingKeyReturnsNilIndicator() {
        CommandProcessor processor = new CommandProcessor(new Store(10));

        String response = processor.process("GET doesNotExist");

        assertEquals("(nil)", response);
    }

    @Test
    public void deleteRemovesKeyAndReturnsOk() {
        CommandProcessor processor = new CommandProcessor(new Store(10));

        processor.process("SET temp value");
        String deleteResponse = processor.process("DEL temp");
        String getResponse = processor.process("GET temp");

        assertEquals("OK", deleteResponse);
        assertEquals("(nil)", getResponse);
    }

    @Test
    public void setWithTtlExpiresCorrectly() throws InterruptedException {
        CommandProcessor processor = new CommandProcessor(new Store(10));

        processor.process("SET shortLived value 0");
        Thread.sleep(10);
        String response = processor.process("GET shortLived");

        assertEquals("(nil)", response);
    }

    @Test
    public void commandsAreCaseInsensitive() {
        CommandProcessor processor = new CommandProcessor(new Store(10));

        String setResponse = processor.process("set username ivan");
        String getResponse = processor.process("get username");

        assertEquals("OK", setResponse);
        assertEquals("ivan", getResponse);
    }

    @Test
    public void unknownCommandReturnsError() {
        CommandProcessor processor = new CommandProcessor(new Store(10));

        String response = processor.process("FOO bar");

        assertEquals("ERROR: unknown command 'FOO'", response);
    }

    @Test
    public void setWithWrongNumberOfArgumentsReturnsError() {
        CommandProcessor processor = new CommandProcessor(new Store(10));

        String response = processor.process("SET onlyOneArg");

        assertEquals("ERROR: usage is SET key value [ttlSeconds]", response);
    }

    @Test
    public void setWithNonNumericTtlReturnsError() {
        CommandProcessor processor = new CommandProcessor(new Store(10));

        String response = processor.process("SET key value notANumber");

        assertEquals("ERROR: ttlSeconds must be a whole number", response);
    }

    @Test
    public void getWithWrongNumberOfArgumentsReturnsError() {
        CommandProcessor processor = new CommandProcessor(new Store(10));

        String response = processor.process("GET");

        assertEquals("ERROR: usage is GET key", response);
    }

    @Test
    public void emptyCommandReturnsError() {
        CommandProcessor processor = new CommandProcessor(new Store(10));

        String response = processor.process("");

        assertEquals("ERROR: empty command", response);
    }

    @Test
    public void extraWhitespaceBetweenArgumentsIsHandledGracefully() {
        CommandProcessor processor = new CommandProcessor(new Store(10));

        String response = processor.process("SET   username    ivan");

        assertEquals("OK", response);
        assertEquals("ivan", processor.process("GET username"));
    }

    @Test
    public void setCommandIsWrittenToLog() throws IOException {
        WriteAheadLog log = new WriteAheadLog(tempDir.resolve("test.log"));
        CommandProcessor processor = new CommandProcessor(new Store(10), log);

        processor.process("SET username ivan");

        List<String> loggedLines = log.readAll();
        assertEquals(1, loggedLines.size());
        assertEquals("SET username ivan", loggedLines.get(0));
    }

    @Test
    public void deleteCommandIsWrittenToLog() throws IOException {
        WriteAheadLog log = new WriteAheadLog(tempDir.resolve("test.log"));
        CommandProcessor processor = new CommandProcessor(new Store(10), log);

        processor.process("SET temp value");
        processor.process("DEL temp");

        List<String> loggedLines = log.readAll();
        assertEquals(2, loggedLines.size());
        assertEquals("SET temp value", loggedLines.get(0));
        assertEquals("DEL temp", loggedLines.get(1));
    }

    @Test
    public void getCommandIsNotWrittenToLog() throws IOException {
        WriteAheadLog log = new WriteAheadLog(tempDir.resolve("test.log"));
        CommandProcessor processor = new CommandProcessor(new Store(10), log);

        processor.process("SET username ivan");
        processor.process("GET username"); // should not add a second log entry

        List<String> loggedLines = log.readAll();
        assertEquals(1, loggedLines.size());
    }

    @Test
    public void replayFromLogRebuildsStoreState() throws IOException {
        WriteAheadLog log = new WriteAheadLog(tempDir.resolve("test.log"));

        // Simulate a "previous run": write directly to the log, as if
        // an earlier CommandProcessor instance had processed these.
        log.append("SET a 1");
        log.append("SET b 2");
        log.append("SET a updated");
        log.append("DEL b");

        // A brand new Store and CommandProcessor, representing a fresh
        // server startup with no prior in-memory state.
        Store freshStore = new Store(10);
        CommandProcessor processor = new CommandProcessor(freshStore, log);

        processor.replayFromLog();

        assertEquals("updated", freshStore.get("a")); // the later SET should have overwritten the first
        assertEquals(null, freshStore.get("b"));       // deleted during replay, should not exist
    }

    @Test
    public void replayFromLogDoesNotDuplicateLogEntries() throws IOException {
        WriteAheadLog log = new WriteAheadLog(tempDir.resolve("test.log"));
        Store store = new Store(10);
        CommandProcessor processor = new CommandProcessor(store, log);

        processor.process("SET a 1");
        processor.replayFromLog(); // replaying should not re-log the entry it just replayed

        List<String> loggedLines = log.readAll();
        assertEquals(1, loggedLines.size(), "replay should not add duplicate entries to the log");
    }

    @Test
    public void replayFromLogDoesNothingWhenNoLogIsConfigured() {
        CommandProcessor processor = new CommandProcessor(new Store(10)); // no log

        // Should simply do nothing, not throw, since there is no log to replay from.
        assertDoesNotThrowWhenReplaying(processor);
    }

    private void assertDoesNotThrowWhenReplaying(CommandProcessor processor) {
        try {
            processor.replayFromLog();
        } catch (IOException e) {
            throw new AssertionError("replayFromLog should not throw when no log is configured", e);
        }
    }
}