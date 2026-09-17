package com.ivan.miniredis.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the core Store behavior: set, get, and delete.
 * Eviction and expiration are tested separately once those features
 * are implemented in later parts of the build.
 */
public class StoreTest {

    @Test
    public void setAndGetReturnsStoredValue() {
        Store store = new Store();

        store.set("username", "ivan");

        assertEquals("ivan", store.get("username"));
    }

    @Test
    public void getReturnsNullForMissingKey() {
        Store store = new Store();

        assertNull(store.get("doesNotExist"));
    }

    @Test
    public void setOverwritesExistingValue() {
        Store store = new Store();

        store.set("count", 1);
        store.set("count", 2);

        assertEquals(2, store.get("count"));
    }

    @Test
    public void deleteRemovesKey() {
        Store store = new Store();
        store.set("temp", "value");

        store.delete("temp");

        assertNull(store.get("temp"));
    }

    @Test
    public void deleteOnMissingKeyDoesNothing() {
        Store store = new Store();

        // Should not throw an exception even though the key was never set.
        store.delete("neverSet");

        assertEquals(0, store.size());
    }

    @Test
    public void sizeReflectsNumberOfEntries() {
        Store store = new Store();

        store.set("a", 1);
        store.set("b", 2);

        assertEquals(2, store.size());
    }
}