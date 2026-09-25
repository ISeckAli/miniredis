package com.ivan.miniredis.client;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

/**
 * An interactive command-line client for MiniRedis.
 *
 * Connects to a running Server over TCP, then repeatedly reads a line
 * of input from the user, sends it to the server as a command, and
 * prints the server's response. This exists specifically to make the
 * project demoable live: connect, run SET/GET/DEL, watch eviction
 * happen by filling the store past capacity, watch a TTL expire by
 * waiting and re-querying, all visibly, rather than only being
 * provable by reading source code or running automated tests.
 *
 * Type "exit" or "quit" to disconnect and end the session.
 */
public class Client {

    public static void main(String[] args) {
        String host = "localhost";
        int port = 6380;

        System.out.println("Connecting to MiniRedis at " + host + ":" + port + "...");

        try (
            Socket socket = new Socket(host, port);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner scanner = new Scanner(System.in)
        ) {
            System.out.println("Connected. Type commands like SET key value, GET key, DEL key.");
            System.out.println("Type 'exit' or 'quit' to disconnect.");
            System.out.println();

            while (true) {
                System.out.print("miniredis> ");
                String input = scanner.nextLine();

                if (input.equalsIgnoreCase("exit") || input.equalsIgnoreCase("quit")) {
                    break;
                }

                if (input.isBlank()) {
                    continue;
                }

                out.println(input);
                String response = in.readLine();

                if (response == null) {
                    System.out.println("Server closed the connection.");
                    break;
                }

                System.out.println(response);
            }
        } catch (IOException e) {
            System.err.println("Could not connect to server: " + e.getMessage());
            System.err.println("Make sure the MiniRedis server is running first.");
        }

        System.out.println("Disconnected.");
    }
}