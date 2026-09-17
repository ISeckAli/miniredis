package com.ivan.miniredis.core;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple in-memory key-value store.
 *
 * This is the core building block of MiniRedis. It currently supports
 * basic set, get, and delete operations backed by a HashMap. Eviction
 * and expiration logic will be added in later parts of the build.
 */
public class Store {

    private final Map<String, Object> data;

    public Store() {
        this.data = new HashMap<>();
    }

    /**
     * Stores a value under the given key. If the key already exists,
     * its value is overwritten.
     */
    public void set(String key, Object value) {
        data.put(key, value);
    }

    /**
     * Retrieves the value stored under the given key, or null if the
     * key does not exist.
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * Removes the key and its value from the store, if present.
     * Does nothing if the key does not exist.
     */
    public void delete(String key) {
        data.remove(key);
    }

    /**
     * Returns the number of key-value pairs currently in the store.
     * Useful for tests and for capacity checks in later parts.
     */
    public int size() {
        return data.size();
    }
}