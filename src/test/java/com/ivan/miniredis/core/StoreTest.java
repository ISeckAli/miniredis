package com.ivan.miniredis.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for Store: basic get/set/delete behavior, plus LRU
 * eviction correctness under capacity pressure. Expiration (TTL) is
 * tested separately once that feature is added.
 */
public class StoreTest {

    @Test
    public void setAndGetReturnsStoredValue() {
        Store store = new Store(10);

        store.set("username", "ivan");

        assertEquals("ivan", store.get("username"));
    }

    @Test
    public void getReturnsNullForMissingKey() {
        Store store = new Store(10);

        assertNull(store.get("doesNotExist"));
    }

    @Test
    public void setOverwritesExistingValue() {
        Store store = new Store(10);

        store.set("count", 1);
        store.set("count", 2);

        assertEquals(2, store.get("count"));
    }

    @Test
    public void deleteRemovesKey() {
        Store store = new Store(10);
        store.set("temp", "value");

        store.delete("temp");

        assertNull(store.get("temp"));
    }

    @Test
    public void deleteOnMissingKeyDoesNothing() {
        Store store = new Store(10);

        // Should not throw an exception even though the key was never set.
        store.delete("neverSet");

        assertEquals(0, store.size());
    }

    @Test
    public void sizeReflectsNumberOfEntries() {
        Store store = new Store(10);

        store.set("a", 1);
        store.set("b", 2);

        assertEquals(2, store.size());
    }

    @Test
    public void evictsLeastRecentlyUsedWhenCapacityExceeded() {
        Store store = new Store(2);

        store.set("a", 1);
        store.set("b", 2);
        store.set("c", 3); // capacity is 2, so "a" (least recently used) should be evicted

        assertNull(store.get("a"));
        assertEquals(2, store.get("b"));
        assertEquals(3, store.get("c"));
        assertEquals(2, store.size());
    }

    @Test
    public void accessingAnEntryProtectsItFromEviction() {
        Store store = new Store(2);

        store.set("a", 1);
        store.set("b", 2);
        store.get("a");        // "a" is now most recently used, "b" is least recently used
        store.set("c", 3);     // should evict "b", not "a"

        assertEquals(1, store.get("a"));
        assertNull(store.get("b"));
        assertEquals(3, store.get("c"));
    }

    @Test
    public void updatingAnExistingKeyDoesNotTriggerEviction() {
        Store store = new Store(2);

        store.set("a", 1);
        store.set("b", 2);
        store.set("a", 100); // updating an existing key, size should stay at 2

        assertEquals(2, store.size());
        assertEquals(100, store.get("a"));
        assertEquals(2, store.get("b"));
    }

    @Test
    public void evictionOrderFollowsMultipleAccesses() {
        Store store = new Store(3);

        store.set("a", 1);
        store.set("b", 2);
        store.set("c", 3);
        store.get("a");        // order of use is now: b, c, a (a most recent)
        store.get("b");        // order of use is now: c, a, b (b most recent)
        store.set("d", 4);     // capacity exceeded, "c" is least recently used, evict it

        assertNull(store.get("c"));
        assertEquals(1, store.get("a"));
        assertEquals(2, store.get("b"));
        assertEquals(4, store.get("d"));
    }
}