package com.ivan.miniredis.core;

import java.util.HashMap;
import java.util.Map;

/**
 * An in-memory key-value store with capacity-bounded LRU eviction.
 *
 * Lookup is backed by a HashMap for O(1) access. Recency of use is
 * tracked with a hand-built doubly linked list rather than relying on
 * LinkedHashMap's built-in access-order mode, since implementing the
 * mechanism directly is the point of this project: it proves an
 * understanding of how O(1) LRU tracking actually works, not just that
 * a library call can produce the same behavior.
 *
 * The list's head represents the most recently used entry; the tail
 * represents the least recently used entry, and is the first candidate
 * removed when the store exceeds its configured capacity.
 */
public class Store {

    private final Map<String, Node> data;
    private final int capacity;

    private Node head;
    private Node tail;

    public Store(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be positive");
        }
        this.capacity = capacity;
        this.data = new HashMap<>();
    }

    /**
     * Stores a value under the given key. If the key already exists,
     * its value is updated and it becomes the most recently used entry.
     * If the key is new and the store is at capacity, the least
     * recently used entry is evicted to make room.
     */
    public void set(String key, Object value) {
        Node existing = data.get(key);

        if (existing != null) {
            existing.value = value;
            moveToFront(existing);
            return;
        }

        if (data.size() >= capacity) {
            evictLeastRecentlyUsed();
        }

        Node newNode = new Node(key, value);
        data.put(key, newNode);
        addToFront(newNode);
    }

    /**
     * Retrieves the value stored under the given key, or null if the
     * key does not exist. A successful lookup counts as a "use," so the
     * entry becomes the most recently used.
     */
    public Object get(String key) {
        Node node = data.get(key);

        if (node == null) {
            return null;
        }

        moveToFront(node);
        return node.value;
    }

    /**
     * Removes the key and its value from the store, if present.
     * Does nothing if the key does not exist.
     */
    public void delete(String key) {
        Node node = data.get(key);

        if (node == null) {
            return;
        }

        removeFromList(node);
        data.remove(key);
    }

    /**
     * Returns the number of key-value pairs currently in the store.
     */
    public int size() {
        return data.size();
    }

    // ---- Internal doubly linked list management ----
    //
    // These methods keep the list's head/tail pointers and each node's
    // prev/next pointers consistent. They are private because the list
    // is purely an internal recency-tracking mechanism; nothing outside
    // Store should ever manipulate it directly.

    private void addToFront(Node node) {
        node.prev = null;
        node.next = head;

        if (head != null) {
            head.prev = node;
        }
        head = node;

        if (tail == null) {
            tail = node;
        }
    }

    private void removeFromList(Node node) {
        if (node.prev != null) {
            node.prev.next = node.next;
        } else {
            head = node.next;
        }

        if (node.next != null) {
            node.next.prev = node.prev;
        } else {
            tail = node.prev;
        }

        node.prev = null;
        node.next = null;
    }

    private void moveToFront(Node node) {
        if (head == node) {
            return;
        }
        removeFromList(node);
        addToFront(node);
    }

    private void evictLeastRecentlyUsed() {
        if (tail == null) {
            return;
        }
        Node lru = tail;
        removeFromList(lru);
        data.remove(lru.key);
    }
}