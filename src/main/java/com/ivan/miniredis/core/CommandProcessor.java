package com.ivan.miniredis.core;

/**
 * Parses simple text commands and executes them against a Store.
 *
 * This class exists as a seam between the Store's Java API and any
 * external interface that speaks in plain text commands, most notably
 * the TCP server planned for the stretch phase of this project. By
 * building the parsing and response logic here rather than inside the
 * network layer directly, the network layer only has to worry about
 * receiving and sending raw text; all command interpretation and
 * response formatting stays in one place, and can be tested without
 * any actual networking involved.
 *
 * Supported commands (case-insensitive):
 *   SET key value
 *   SET key value ttlSeconds
 *   GET key
 *   DEL key
 */
public class CommandProcessor {

    private final Store store;

    public CommandProcessor(Store store) {
        this.store = store;
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
                return handleSet(parts);
            case "GET":
                return handleGet(parts);
            case "DEL":
                return handleDelete(parts);
            default:
                return "ERROR: unknown command '" + parts[0] + "'";
        }
    }

    private String handleSet(String[] parts) {
        // SET key value            -> 3 parts
        // SET key value ttlSeconds -> 4 parts
        if (parts.length != 3 && parts.length != 4) {
            return "ERROR: usage is SET key value [ttlSeconds]";
        }

        String key = parts[1];
        String value = parts[2];

        if (parts.length == 3) {
            store.set(key, value);
            return "OK";
        }

        try {
            long ttlSeconds = Long.parseLong(parts[3]);
            store.set(key, value, ttlSeconds);
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

    private String handleDelete(String[] parts) {
        if (parts.length != 2) {
            return "ERROR: usage is DEL key";
        }

        store.delete(parts[1]);
        return "OK";
    }
}