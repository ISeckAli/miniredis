package com.ivan.miniredis.core;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * An append-only write-ahead log for Store's write commands.
 *
 * Every SET and DEL command is appended to this log as a single line
 * of text before being applied in memory, so that if the process
 * later restarts, replaying the log rebuilds the Store to its last
 * known state. Read commands (GET) are never logged, since they do
 * not change state and therefore have nothing to recover.
 *
 * This is deliberately a simple, append-only log: entries are never
 * edited or removed, and the log file grows indefinitely for the
 * life of the store. A production system would eventually need a
 * compaction strategy (periodically rewriting the log to drop
 * superseded entries), but that is explicitly out of scope here; an
 * ever-growing append-only file is a reasonable, honest simplification
 * for a project at this scale, not a hidden shortcut.
 */
public class WriteAheadLog {

    private final Path logFilePath;

    public WriteAheadLog(Path logFilePath) {
        this.logFilePath = logFilePath;
    }

    /**
     * Appends a single command line to the log file, creating the file
     * if it does not already exist. Each call opens and closes the
     * file rather than holding it open indefinitely; this is simpler
     * to reason about and safe, at the cost of some performance, which
     * is an acceptable tradeoff at this project's scale.
     */
    public void append(String commandLine) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(logFilePath.toFile(), true))) {
            writer.println(commandLine);
        }
    }

    /**
     * Reads every line currently in the log file, in the order they
     * were written. Returns an empty list if the log file does not
     * exist yet (a brand new store with no prior write history).
     */
    public List<String> readAll() throws IOException {
        List<String> lines = new ArrayList<>();

        if (!Files.exists(logFilePath)) {
            return lines;
        }

        try (BufferedReader reader = Files.newBufferedReader(logFilePath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    lines.add(line);
                }
            }
        }

        return lines;
    }
}