package com.ivan.miniredis.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ivan.miniredis.core.CommandProcessor;
import com.ivan.miniredis.core.Store;

/**
 * Integration tests for Server: proves that commands sent over a real
 * TCP connection are correctly parsed and executed, that the correct
 * response comes back over the wire, and that many clients issuing
 * commands genuinely concurrently do not corrupt the shared Store.
 *
 * Each test starts a real Server instance on a background thread,
 * bound to port 0 (meaning the operating system chooses an available
 * port), connects to it as a genuine TCP client, and verifies the
 * observed behavior end-to-end. This is intentionally not a unit test
 * of the server's internals; it is a black-box test that confirms the
 * network-facing behavior actually works, which is a materially
 * different (and necessary) kind of proof than testing Store or
 * CommandProcessor in isolation already provides.
 */
public class ServerTest {

    private Server server;
    private Thread serverThread;

    @BeforeEach
    public void startServer() throws IOException, InterruptedException {
        Store store = new Store(1000); // capacity high enough that the concurrency test below doesn't trigger eviction
        CommandProcessor processor = new CommandProcessor(store);
        server = new Server(0, processor); // port 0: let the OS choose a free port

        serverThread = new Thread(() -> {
            try {
                server.start();
            } catch (IOException e) {
                // Expected once stop() closes the socket at the end of
                // each test; not a real failure in that case.
            }
        });
        serverThread.start();

        // Give the background thread a brief moment to actually bind
        // the socket before the test tries to connect to it.
        waitUntilPortIsAssigned();
    }

    @AfterEach
    public void stopServer() throws IOException, InterruptedException {
        server.stop();
        serverThread.join(1000);
    }

    private void waitUntilPortIsAssigned() throws InterruptedException {
        // start() binds the socket almost immediately, but this
        // happens on a separate thread, so briefly retrying avoids a
        // race condition where the test tries to read the port before
        // the server thread has actually reached that line.
        int attempts = 0;
        while (attempts < 50) {
            try {
                server.getPort();
                return;
            } catch (NullPointerException e) {
                Thread.sleep(10);
                attempts++;
            }
        }
        throw new IllegalStateException("Server did not start in time");
    }

    @Test
    public void setCommandReturnsOkOverNetwork() throws IOException {
        try (Socket socket = new Socket("localhost", server.getPort());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("SET username ivan");
            String response = in.readLine();

            assertEquals("OK", response);
        }
    }

    @Test
    public void getCommandReturnsPreviouslySetValueOverNetwork() throws IOException {
        try (Socket socket = new Socket("localhost", server.getPort());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("SET username ivan");
            in.readLine(); // consume the "OK" response before sending the next command

            out.println("GET username");
            String response = in.readLine();

            assertEquals("ivan", response);
        }
    }

    @Test
    public void multipleCommandsOnTheSameConnectionAreHandledInOrder() throws IOException {
        try (Socket socket = new Socket("localhost", server.getPort());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("SET a 1");
            assertEquals("OK", in.readLine());

            out.println("SET b 2");
            assertEquals("OK", in.readLine());

            out.println("GET a");
            assertEquals("1", in.readLine());

            out.println("GET b");
            assertEquals("2", in.readLine());

            out.println("DEL a");
            assertEquals("OK", in.readLine());

            out.println("GET a");
            assertEquals("(nil)", in.readLine());
        }
    }

    @Test
    public void getOnMissingKeyReturnsNilOverNetwork() throws IOException {
        try (Socket socket = new Socket("localhost", server.getPort());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            out.println("GET doesNotExist");
            String response = in.readLine();

            assertEquals("(nil)", response);
        }
    }

    @Test
    public void manyConcurrentClientsCanSetDistinctKeysWithoutDataLoss() throws InterruptedException {
        int clientCount = 50;

        // A CountDownLatch lets every client thread block until all of
        // them are ready, so they all fire their commands at the
        // server at genuinely the same moment, rather than trickling
        // in one after another, which would defeat the point of a
        // concurrency test.
        CountDownLatch readyLatch = new CountDownLatch(clientCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);

        ExecutorService clientPool = Executors.newFixedThreadPool(clientCount);

        for (int i = 0; i < clientCount; i++) {
            final int clientIndex = i;
            clientPool.submit(() -> {
                try (Socket socket = new Socket("localhost", server.getPort());
                     PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                    readyLatch.countDown();
                    startLatch.await(); // all threads wait here until released together

                    out.println("SET key" + clientIndex + " value" + clientIndex);
                    String response = in.readLine();

                    if ("OK".equals(response)) {
                        successCount.incrementAndGet();
                    }
                } catch (IOException | InterruptedException e) {
                    // Left uncounted; the assertion below will catch
                    // any shortfall in successCount either way.
                }
            });
        }

        readyLatch.await(); // wait until every client has connected and is ready
        startLatch.countDown(); // release all of them at once

        clientPool.shutdown();
        clientPool.awaitTermination(5, TimeUnit.SECONDS);

        assertEquals(clientCount, successCount.get(), "every concurrent SET should have succeeded");

        // Now verify every single key actually made it into the store
        // correctly, not just that the server said "OK" for each.
        try (Socket socket = new Socket("localhost", server.getPort());
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            for (int i = 0; i < clientCount; i++) {
                out.println("GET key" + i);
                String response = in.readLine();
                assertEquals("value" + i, response, "key" + i + " was lost or corrupted under concurrent access");
            }
        } catch (IOException e) {
            assertTrue(false, "Failed to verify keys after concurrent writes: " + e.getMessage());
        }
    }
}