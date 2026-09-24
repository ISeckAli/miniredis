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
 */
public class Node {

    final String key;
    Object value;
    Node prev;
    Node next;

    public Node(String key, Object value) {
        this.key = key;
        this.value = value;
    }
}