package com.ivan.miniredis.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import com.ivan.miniredis.core.CommandProcessor;
import com.ivan.miniredis.core.Store;

/**
 * A TCP server that exposes a Store over a plain-text network protocol.
 *
 * A Server instance owns a ServerSocket bound to a specific port and a
 * CommandProcessor to delegate command handling to. Once started, it
 * accepts client connections one at a time. For each connected client,
 * it reads a line of text (a command such as "SET foo bar"), delegates
 * parsing and execution to CommandProcessor, and writes the response
 * back to that same client, one response per command, until the
 * client disconnects.
 *
 * This initial version is intentionally single-threaded: only one
 * client can be connected at a time. Handling multiple simultaneous
 * clients safely, without corrupting the shared Store's internal
 * state, requires real concurrency control and is addressed
 * separately, once this single-client version is proven correct.
 *
 * Server is a standalone class (not just a main method) specifically
 * so it can be started and stopped programmatically, most notably by
 * automated tests that need to connect to a real running instance
 * without launching a separate process.
 */
public class Server {

    public static final int DEFAULT_PORT = 6380;
    private static final int DEFAULT_CAPACITY = 100;

    private final int port;
    private final CommandProcessor processor;
    private ServerSocket serverSocket;
    private volatile boolean running;

    public Server(int port, CommandProcessor processor) {
        this.port = port;
        this.processor = processor;
    }

    /**
     * Binds the server socket and begins accepting client connections.
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
                handleClient(clientSocket);
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
     * blocking accept() call in start() to exit its loop.
     */
    public void stop() throws IOException {
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
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
     * Application entry point: builds a Store and CommandProcessor
     * with default settings and starts the server on the default port.
     */
    public static void main(String[] args) throws IOException {
        Store store = new Store(DEFAULT_CAPACITY);
        CommandProcessor processor = new CommandProcessor(store);
        Server server = new Server(DEFAULT_PORT, processor);
        server.start();
    }
}