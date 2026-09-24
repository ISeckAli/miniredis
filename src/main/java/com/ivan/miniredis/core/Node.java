package com.ivan.miniredis.core;

/**
 * A single entry in the LRU tracking structure.
 *
 * Each Node sits inside a doubly linked list that tracks access order
 * (most recently used at the front, least recently used at the back),
 * while also being the object referenced directly by the Store's
 * HashMap for O(1) lookup. Storing the key on the Node itself (not just
 * the value) is what allows eviction to remove the correct entry from
 * the HashMap once the least-recently-used node is identified from the
 * back of the list.
 *
 * expireAt holds the timestamp (in epoch milliseconds) at which this
 * entry becomes invalid, or null if the entry has no expiration.
 * Expiration is checked lazily, at the point a key is accessed, rather
 * than through a background sweep; see Store for that logic.
 */
public class Node {

    final String key;
    Object value;
    Long expireAt;
    Node prev;
    Node next;

    public Node(String key, Object value, Long expireAt) {
        this.key = key;
        this.value = value;
        this.expireAt = expireAt;
    }

    /**
     * Returns true if this node has an expiration set and that time
     * has already passed.
     */
    boolean isExpired() {
        return expireAt != null && System.currentTimeMillis() >= expireAt;
    }
}