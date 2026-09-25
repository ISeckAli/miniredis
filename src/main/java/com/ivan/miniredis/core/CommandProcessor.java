package com.ivan.miniredis.core;

import java.io.IOException;
import java.util.List;

/**
 * Parses simple text commands and executes them against a Store.
 *
 * This class exists as a seam between the Store's Java API and any
 * external interface that speaks in plain text commands, most notably
 * the TCP server. By building the parsing and response logic here
 * rather than inside the network layer directly, the network layer
 * only has to worry about receiving and sending raw text; all command
 * interpretation and response formatting stays in one place, and can
 * be tested without any actual networking involved.
 *
 * A CommandProcessor may optionally be given a WriteAheadLog. When
 * present, every successful write command (SET or DEL) is appended to
 * the log after being applied to the Store, so that the operation can
 * be replayed to rebuild state after a restart. Read commands (GET)
 * are never logged, since they carry no state to recover. The log is
 * optional specifically so that CommandProcessor remains simple to use
 * in contexts that don't need persistence, such as most unit tests.
 *
 * Supported commands (case-insensitive):
 *   SET key value
 *   SET key value ttlSeconds
 *   GET key
 *   DEL key
 */
public class CommandProcessor {

    private final Store store;
    private final WriteAheadLog log;

    public CommandProcessor(Store store) {
        this(store, null);
    }

    public CommandProcessor(Store store, WriteAheadLog log) {
        this.store = store;
        this.log = log;
    }

    /**
     * Parses and executes a single command line, returning a response
     * string suitable for sending back to whatever issued the command.
     */
    public String process(String commandLine) {
        if (commandLine == null || commandLine.isBlank()) {
            return "ERROR: empty command";
        }

        String[] parts = commandLine.trim().split("\\s+");
        String command = parts[0].toUpperCase();

        switch (command) {
            case "SET":
                return handleSet(parts, commandLine);
            case "GET":
                return handleGet(parts);
            case "DEL":
                return handleDelete(parts, commandLine);
            default:
                return "ERROR: unknown command '" + parts[0] + "'";
        }
    }

    /**
     * Rebuilds Store state by replaying every command previously
     * written to the configured WriteAheadLog, in the order they were
     * originally written. Intended to be called once, at startup,
     * before any client commands are accepted. Does nothing if no log
     * was configured for this CommandProcessor.
     *
     * Replayed commands are re-executed through the normal process()
     * path, but are NOT re-appended to the log; doing so would
     * duplicate every entry on each restart, causing the log to grow
     * unnecessarily and eventually replay incorrectly.
     */
    public void replayFromLog() throws IOException {
        if (log == null) {
            return;
        }

        List<String> commands = log.readAll();
        for (String commandLine : commands) {
            processWithoutLogging(commandLine);
        }
    }

    /**
     * Executes a command against the Store directly, bypassing the
     * logging step in handleSet/handleDelete. Used only during
     * replayFromLog(), where the command already exists in the log and
     * must not be written a second time.
     */
    private void processWithoutLogging(String commandLine) {
        String[] parts = commandLine.trim().split("\\s+");
        String command = parts[0].toUpperCase();

        if ("SET".equals(command)) {
            if (parts.length == 3) {
                store.set(parts[1], parts[2]);
            } else if (parts.length == 4) {
                try {
                    store.set(parts[1], parts[2], Long.parseLong(parts[3]));
                } catch (NumberFormatException e) {
                    System.err.println("Skipping malformed log entry: " + commandLine);
                }
            }
        } else if ("DEL".equals(command)) {
            if (parts.length == 2) {
                store.delete(parts[1]);
            }
        }
        // GET is never logged, so it never appears during replay;
        // any other command appearing in the log would indicate log
        // corruption and is silently skipped rather than crashing
        // startup over a single bad line.
    }

    private String handleSet(String[] parts, String originalCommandLine) {
        // SET key value            -> 3 parts
        // SET key value ttlSeconds -> 4 parts
        if (parts.length != 3 && parts.length != 4) {
            return "ERROR: usage is SET key value [ttlSeconds]";
        }

        String key = parts[1];
        String value = parts[2];

        if (parts.length == 3) {
            store.set(key, value);
            writeToLog(originalCommandLine);
            return "OK";
        }

        try {
            long ttlSeconds = Long.parseLong(parts[3]);
            store.set(key, value, ttlSeconds);
            writeToLog(originalCommandLine);
            return "OK";
        } catch (NumberFormatException e) {
            return "ERROR: ttlSeconds must be a whole number";
        }
    }

    private String handleGet(String[] parts) {
        if (parts.length != 2) {
            return "ERROR: usage is GET key";
        }

        Object value = store.get(parts[1]);
        return (value == null) ? "(nil)" : value.toString();
    }

    private String handleDelete(String[] parts, String originalCommandLine) {
        if (parts.length != 2) {
            return "ERROR: usage is DEL key";
        }

        store.delete(parts[1]);
        writeToLog(originalCommandLine);
        return "OK";
    }

    /**
     * Appends a command to the write-ahead log, if one was configured.
     * A logging failure is reported to standard error rather than
     * thrown back to the caller: the in-memory write already
     * succeeded by this point, and failing the whole command because
     * the log write failed would be a worse outcome than accepting the
     * write and simply losing durability guarantees for that one
     * operation.
     */
    private void writeToLog(String commandLine) {
        if (log == null) {
            return;
        }

        try {
            log.append(commandLine);
        } catch (IOException e) {
            System.err.println("Failed to write to log: " + e.getMessage());
        }
    }
}