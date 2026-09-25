package com.ivan.miniredis.core;

import java.util.HashMap;
import java.util.Map;

/**
 * An in-memory key-value store with capacity-bounded LRU eviction and
 * optional TTL (time-to-live) expiration.
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
 *
 * Expiration is checked lazily: rather than running a background
 * thread to sweep for expired keys, each entry's expiration is checked
 * at the moment it is accessed. This is simpler to reason about and
 * sufficient for this project's purposes; an active (background sweep)
 * approach would keep memory tighter at the cost of real added
 * complexity, and is noted as a possible extension rather than
 * implemented here.
 *
 * Thread-safety: the public methods below are synchronized, meaning
 * only one thread can execute inside any of them at a time. This is a
 * single, coarse-grained lock around the entire Store rather than
 * finer-grained locking (for example, a ReentrantReadWriteLock that
 * would allow concurrent reads). Coarse-grained locking is simpler to
 * reason about and is sufficient here: this store is not expected to
 * face heavy read contention at a scale where lock granularity would
 * be a real bottleneck. A read-write lock is a legitimate next step if
 * profiling ever showed otherwise, but is not implemented here, since
 * introducing it without a measured need would be added complexity
 * without a proven benefit.
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
     * Stores a value under the given key with no expiration.
     */
    public synchronized void set(String key, Object value) {
        set(key, value, null);
    }

    /**
     * Stores a value under the given key that expires after the given
     * number of seconds. If the key already exists, its value and
     * expiration are updated and it becomes the most recently used
     * entry. If the key is new and the store is at capacity, the least
     * recently used entry is evicted to make room.
     */
    public synchronized void set(String key, Object value, Long ttlSeconds) {
        Long expireAt = (ttlSeconds == null) ? null : System.currentTimeMillis() + (ttlSeconds * 1000);

        Node existing = data.get(key);

        if (existing != null) {
            existing.value = value;
            existing.expireAt = expireAt;
            moveToFront(existing);
            return;
        }

        if (data.size() >= capacity) {
            evictLeastRecentlyUsed();
        }

        Node newNode = new Node(key, value, expireAt);
        data.put(key, newNode);
        addToFront(newNode);
    }

    /**
     * Retrieves the value stored under the given key, or null if the
     * key does not exist or has expired. A successful lookup counts as
     * a "use," so the entry becomes the most recently used. If the
     * entry has expired, it is removed from the store as part of this
     * call, rather than left in place until some future sweep.
     */
    public synchronized Object get(String key) {
        Node node = data.get(key);

        if (node == null) {
            return null;
        }

        if (node.isExpired()) {
            removeFromList(node);
            data.remove(key);
            return null;
        }

        moveToFront(node);
        return node.value;
    }

    /**
     * Removes the key and its value from the store, if present.
     * Does nothing if the key does not exist.
     */
    public synchronized void delete(String key) {
        Node node = data.get(key);

        if (node == null) {
            return;
        }

        removeFromList(node);
        data.remove(key);
    }

    /**
     * Returns the number of key-value pairs currently in the store,
     * including any that have expired but have not yet been accessed
     * (and therefore not yet lazily removed).
     */
    public synchronized int size() {
        return data.size();
    }

    // ---- Internal doubly linked list management ----
    //
    // These methods keep the list's head/tail pointers and each node's
    // prev/next pointers consistent. They are private because the list
    // is purely an internal recency-tracking mechanism; nothing outside
    // Store should ever manipulate it directly. They are intentionally
    // NOT separately synchronized: they are only ever called from
    // within an already-synchronized public method, so they safely
    // inherit that method's lock. Adding synchronized here too would
    // be redundant, not incorrect, but it is left off to keep it clear
    // that these are always invoked under an existing lock, never on
    // their own.

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