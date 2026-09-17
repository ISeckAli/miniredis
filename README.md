# MiniRedis

A simplified, self-built in-memory key-value store written in Java,
implementing the core mechanics behind a real production cache: fast
hash-table storage, LRU eviction, and TTL-based key expiration.

**Status: in progress — core build underway.**

## Planned Features (Core)
- GET / SET / DELETE operations
- LRU eviction (hash map + custom doubly linked list, built from first
  principles)
- TTL (time-to-live) key expiration
- Full JUnit test suite
- CI via GitHub Actions

## Planned Features (Stretch)
- TCP server
- Thread-safety for concurrent clients
- Write-ahead log for persistence
- Command-line client

## Running Tests

```
mvn test
```

More documentation will be added as the project develops.

