package com.ivan.miniredis.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.ivan.miniredis.core.CommandProcessor;
import com.ivan.miniredis.core.Store;
import com.ivan.miniredis.core.WriteAheadLog;

/**
 * A TCP server that exposes a Store over a plain-text network protocol.
 *
 * A Server instance owns a ServerSocket bound to a specific port and a
 * CommandProcessor to delegate command handling to. Once started, it
 * accepts client connections and hands each one off to a thread pool,
 * allowing multiple clients to be connected and issuing commands
 * genuinely simultaneously.
 *
 * Concurrent access to the shared Store is made safe by Store's own
 * internal locking (its public methods are synchronized), not by
 * anything in this class; Server's only responsibility with respect to
 * concurrency is making sure each client runs on its own thread so
 * that one slow or idle client cannot block any other client's
 * commands from being processed.
 *
 * A fixed-size thread pool (via ExecutorService) is used rather than
 * spawning a raw new Thread per client. This bounds the maximum number
 * of concurrently handled clients to a known, reasonable limit,
 * preventing an unbounded number of connections from exhausting system
 * resources, and reuses threads rather than paying thread-creation
 * cost on every single connection.
 */
public class Server {

    public static final int DEFAULT_PORT = 6380;
    private static final int DEFAULT_CAPACITY = 100;
    private static final int THREAD_POOL_SIZE = 20;
    private static final String DEFAULT_LOG_FILE = "miniredis.log";

    private final int port;
    private final CommandProcessor processor;
    private final ExecutorService clientHandlerPool;
    private ServerSocket serverSocket;
    private volatile boolean running;

    public Server(int port, CommandProcessor processor) {
        this.port = port;
        this.processor = processor;
        this.clientHandlerPool = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
    }

    /**
     * Binds the server socket and begins accepting client connections.
     * Each accepted connection is dispatched to the thread pool and
     * handled concurrently with any other currently connected clients.
     * This call blocks the calling thread for as long as the server
     * runs, so callers that need to do other work concurrently (such
     * as tests) should invoke this from a separate thread.
     */
    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;
        System.out.println("MiniRedis server listening on port " + serverSocket.getLocalPort());

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client connected: " + clientSocket.getRemoteSocketAddress());
                clientHandlerPool.submit(() -> handleClient(clientSocket));
            } catch (IOException e) {
                if (running) {
                    System.err.println("Error accepting client connection: " + e.getMessage());
                }
                // If running is false, this exception is the expected
                // result of stop() closing the socket, not a real error.
            }
        }
    }

    /**
     * Stops the server: closes the listening socket, which causes the
     * blocking accept() call in start() to exit its loop, and shuts
     * down the thread pool so any client threads still running are
     * given a chance to finish before this call returns.
     */
    public void stop() throws IOException {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }

        clientHandlerPool.shutdown();
        try {
            if (!clientHandlerPool.awaitTermination(2, TimeUnit.SECONDS)) {
                clientHandlerPool.shutdownNow();
            }
        } catch (InterruptedException e) {
            clientHandlerPool.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Returns the port the server is actually bound to. Useful when
     * the server was constructed with port 0 (meaning "let the OS
     * choose an available port"), most commonly in tests that need to
     * know which port to connect to without risking a conflict with
     * a fixed, hardcoded port.
     */
    public int getPort() {
        return serverSocket.getLocalPort();
    }

    private void handleClient(Socket clientSocket) {
        try (
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String line;
            while ((line = in.readLine()) != null) {
                String response = processor.process(line);
                out.println(response);
            }
        } catch (IOException e) {
            System.err.println("Client connection error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.err.println("Error closing client socket: " + e.getMessage());
            }
            System.out.println("Client disconnected: " + clientSocket.getRemoteSocketAddress());
        }
    }

    /**
     * Application entry point: builds a Store, a WriteAheadLog pointed
     * at a fixed log file in the current working directory, and a
     * CommandProcessor wired to both. Before accepting any client
     * connections, replays the log to restore state from any previous
     * run, so a restart does not lose data that was already
     * successfully written and acknowledged before the process last
     * stopped.
     */
    public static void main(String[] args) throws IOException {
        Store store = new Store(DEFAULT_CAPACITY);
        WriteAheadLog log = new WriteAheadLog(Path.of(DEFAULT_LOG_FILE));
        CommandProcessor processor = new CommandProcessor(store, log);

        System.out.println("Replaying write-ahead log from " + DEFAULT_LOG_FILE + "...");
        processor.replayFromLog();
        System.out.println("Replay complete. Store size: " + store.size());

        Server server = new Server(DEFAULT_PORT, processor);
        server.start();
    }
}