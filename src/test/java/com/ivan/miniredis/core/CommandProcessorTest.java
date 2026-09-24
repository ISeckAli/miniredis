package com.ivan.miniredis.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for CommandProcessor: parsing of SET/GET/DEL commands,
 * correct delegation to Store, and handling of malformed input.
 */
public class CommandProcessorTest {

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
}