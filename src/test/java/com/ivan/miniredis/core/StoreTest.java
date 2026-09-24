package com.ivan.miniredis.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for Store: basic get/set/delete behavior, LRU eviction
 * correctness under capacity pressure, and TTL expiration.
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

    @Test
    public void keyWithNoTtlNeverExpires() {
        Store store = new Store(10);

        store.set("permanent", "value"); // no TTL given

        assertEquals("value", store.get("permanent"));
    }

    @Test
    public void keyWithFutureTtlIsStillAccessible() {
        Store store = new Store(10);

        store.set("session", "abc123", 60L); // expires 60 seconds from now

        assertEquals("abc123", store.get("session"));
    }

    @Test
    public void keyWithPastTtlIsTreatedAsExpired() throws InterruptedException {
        Store store = new Store(10);

        store.set("shortLived", "value", 0L); // expires immediately (0 seconds from now)
        Thread.sleep(10); // ensure the clock has genuinely moved past expireAt

        assertNull(store.get("shortLived"));
    }

    @Test
    public void expiredKeyIsRemovedFromStoreOnAccess() throws InterruptedException {
        Store store = new Store(10);

        store.set("shortLived", "value", 0L);
        Thread.sleep(10);

        store.get("shortLived"); // triggers lazy removal
        assertEquals(0, store.size());
    }

    @Test
    public void settingExistingKeyUpdatesItsTtl() {
        Store store = new Store(10);

        store.set("key", "first", 0L);      // would expire immediately
        store.set("key", "second", 60L);    // overwritten with a fresh, far-future TTL

        assertEquals("second", store.get("key"));
    }

    @Test
    public void evictingFromAnEmptyStoreDoesNothing() {
        Store store = new Store(5);

        // No entries have ever been set. A get/delete on an empty store
        // should behave the same as a missing key, not throw or corrupt
        // internal state (head/tail pointers should both remain null).
        assertNull(store.get("anything"));
        store.delete("anything");

        assertEquals(0, store.size());

        // Confirm the store is still fully functional afterward.
        store.set("first", "value");
        assertEquals("value", store.get("first"));
        assertEquals(1, store.size());
    }

    @Test
    public void keysWithAndWithoutTtlCoexistCorrectly() {
        Store store = new Store(10);

        store.set("permanent", "staysForever");       // no TTL
        store.set("temporary", "expiresSoon", 0L);     // expires immediately

        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // The TTL key should be gone; the permanent key should be
        // completely unaffected by its neighbor's expiration.
        assertNull(store.get("temporary"));
        assertEquals("staysForever", store.get("permanent"));
        assertEquals(1, store.size());
    }
}