package com.ivan.miniredis.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Unit tests for WriteAheadLog: appending lines and reading them back
 * in order, and correct behavior when no log file exists yet.
 */
public class WriteAheadLogTest {

    @TempDir
    Path tempDir; // JUnit creates and cleans up a fresh temp directory for each test automatically

    @Test
    public void readAllReturnsEmptyListWhenLogFileDoesNotExist() throws IOException {
        WriteAheadLog log = new WriteAheadLog(tempDir.resolve("does-not-exist.log"));

        List<String> lines = log.readAll();

        assertTrue(lines.isEmpty());
    }

    @Test
    public void appendedLineCanBeReadBack() throws IOException {
        WriteAheadLog log = new WriteAheadLog(tempDir.resolve("test.log"));

        log.append("SET username ivan");

        List<String> lines = log.readAll();

        assertEquals(1, lines.size());
        assertEquals("SET username ivan", lines.get(0));
    }

    @Test
    public void multipleAppendedLinesAreReadBackInOrder() throws IOException {
        WriteAheadLog log = new WriteAheadLog(tempDir.resolve("test.log"));

        log.append("SET a 1");
        log.append("SET b 2");
        log.append("DEL a");

        List<String> lines = log.readAll();

        assertEquals(3, lines.size());
        assertEquals("SET a 1", lines.get(0));
        assertEquals("SET b 2", lines.get(1));
        assertEquals("DEL a", lines.get(2));
    }
}