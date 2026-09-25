# MiniRedis

A simplified, self-built in-memory key-value store written in Java,
implementing the core mechanics behind a real production cache: fast
hash-table storage, LRU eviction, TTL-based key expiration, a TCP
server for real network access, thread-safe concurrent client
handling, and write-ahead log persistence for crash/restart recovery.

**Status: complete (core + stretch).**

## Why This Project Exists

This is the third piece of a three-project portfolio. PyQuest proves
full-stack web development and thoughtful AI integration. A fraud/
anomaly detection classifier proves real applied machine learning.
Neither touches systems programming, concurrency, or data-structure
design from first principles, which is exactly the gap this project
fills. "I built a simplified Redis" is also immediately legible to any
engineer, at any level of seniority, without further explanation.

## Architecture

```
Client (CLI) ──┐
├──► Server (TCP, thread pool) ──► CommandProcessor ──► Store
Any TCP client ──┘ │ │
▼ ▼
WriteAheadLog HashMap + LRU
(append-only) doubly linked list
```


- **Store** (`core/Store.java`): the in-memory key-value store itself.
  Backed by a `HashMap<String, Node>` for O(1) lookup, with a hand-built
  doubly linked list tracking access recency for LRU eviction. All
  public methods are `synchronized` for thread safety.
- **Node** (`core/Node.java`): a single entry in the LRU tracking
  structure, holding its key, value, TTL expiration timestamp, and
  links to its neighbors in the recency list.
- **CommandProcessor** (`core/CommandProcessor.java`): parses plain-text
  commands (`SET`, `GET`, `DEL`) and executes them against a `Store`.
  The seam between the store's Java API and any text-based interface,
  whether that's the TCP server or a test.
- **WriteAheadLog** (`core/WriteAheadLog.java`): an append-only log of
  every write command, used to rebuild `Store` state after a restart.
- **Server** (`server/Server.java`): a TCP server accepting client
  connections, dispatching each to a thread pool, and delegating
  command handling to `CommandProcessor`.
- **Client** (`client/Client.java`): a standalone interactive
  command-line client for connecting to and demoing a running server.
- **StoreBenchmark** (`benchmark/StoreBenchmark.java`): a JMH benchmark
  measuring real `GET`/`SET` throughput.

## Design Decisions and Tradeoffs

**LRU eviction: hand-built, not `LinkedHashMap`.** Java's
`LinkedHashMap` with `accessOrder=true` provides similar behavior
out of the box. It was deliberately not used here: implementing the
hash map + doubly linked list mechanism directly is the actual point
of this project, proving an understanding of how O(1) LRU tracking
works, not just that a library call can reproduce the behavior.

**TTL expiration: lazy, not active.** Expired keys are only checked
and removed at the moment they're accessed (`get`), rather than swept
by a background thread. Lazy expiration is simpler to reason about and
sufficient at this scale. The real cost: an expired key that is never
accessed again stays in memory (and counts toward `size()`) until
something touches it. An active sweep would keep memory tighter at the
cost of real added complexity (a background thread to manage,
additional synchronization to reason about). This tradeoff is a
genuine engineering decision, not an oversight.

**Concurrency: a single coarse-grained lock, not fine-grained
locking.** `Store`'s public methods are all `synchronized` on the same
object monitor, meaning only one thread can be inside any `Store`
method at a time, even two unrelated `GET` calls block each other.
A `ReentrantReadWriteLock` would allow concurrent reads, offering more
parallelism for read-heavy workloads. Coarse-grained locking was
chosen because it is simpler to reason about and verify correct
(proven by the 50-client concurrency test in `ServerTest`), and this
store is not expected to face contention at a scale where lock
granularity would be a genuine bottleneck. Finer-grained locking is a
legitimate next step if profiling ever showed otherwise.

**Persistence: append-only write-ahead log, no compaction.** Every
`SET`/`DEL` is appended to a log file before being considered durable;
on startup, the log is replayed to rebuild state. The log is never
rewritten or compacted, it grows for the life of the store. A
production system would eventually need a compaction strategy
(periodically rewriting the log to drop superseded entries). This is
explicitly out of scope here: an ever-growing append-only file is an
honest, reasonable simplification at this project's scale, not a
hidden shortcut.

**Thread pool size and networking.** The TCP server uses a fixed
thread pool (20 threads) rather than one raw thread per client, this
bounds resource usage under an unbounded number of connection
attempts and reuses threads rather than paying creation cost per
connection. The server listens on port 6380 (not Redis's default
6379), deliberately adjacent but distinct, avoiding any conflict or
confusion with a real Redis installation on the same machine.

## Supported Commands

- **SET** — `SET key value` — no expiration
- **SET** — `SET key value ttlSeconds` — expires after ttlSeconds
- **GET** — `GET key` — returns `(nil)` if missing/expired
- **DEL** — `DEL key` — no-op if key doesn't exist

Commands are case-insensitive.

## Running the Server

```
mvn clean compile
```


Then, in Eclipse, run `Server.java` as a Java Application (or from the
command line once compiled). The server listens on port 6380 and
replays `miniredis.log` (if one exists) before accepting connections.

## Running the Interactive CLI Client

With the server already running, run `Client.java` as a separate Java
Application. This opens an interactive prompt:

```
miniredis> SET username ivan
OK
miniredis> GET username
ivan
miniredis> SET session abc123 30
OK
miniredis> exit
Disconnected.
```

## Running the Test Suite

```
mvn test
```

43 tests across four test classes:
- `StoreTest` (17): core get/set/delete, LRU eviction correctness,
  TTL expiration, edge cases (empty-store eviction, mixed TTL and
  permanent keys)
- `CommandProcessorTest` (18): command parsing, malformed input
  handling, write-ahead log integration (logging and replay)
- `WriteAheadLogTest` (3): appending and reading log entries
- `ServerTest` (5): real end-to-end TCP behavior, including a
  50-client concurrency stress test proving no data loss or
  corruption under genuinely simultaneous access

Continuous integration via GitHub Actions runs this full suite on
every push (`.github/workflows/tests.yml`).

## Running the Benchmark

```
mvn clean package
java -jar target/benchmarks.jar
```

Produces real, statistically sound throughput numbers via JMH rather
than naive timing (which is misleading due to JVM warm-up effects and
possible dead-code elimination). Full results and methodology are in
[`BENCHMARK_RESULTS.md`](./BENCHMARK_RESULTS.md). Headline numbers:

- **GET:** ~42.6 million operations/second
- **SET:** ~15.0 million operations/second

A full benchmark run takes approximately 15-20 minutes.

## What This Project Proves

- Data structures from first principles: a hash table and a doubly
  linked list combined to give O(1) operations with genuine ordering
  guarantees, not delegated to a library
- Real algorithmic tradeoff reasoning: LRU eviction policy, lazy vs.
  active expiration, coarse-grained vs. fine-grained locking, each a
  deliberate, documented choice
- Systems programming: sockets, a simple text-based network protocol,
  a client-server model, a thread pool
- Crash/restart recovery via a write-ahead log, genuinely verified by
  starting a server, writing data, stopping it, and confirming a fresh
  process recovers the exact prior state
- The same engineering discipline (tests, CI, clean documentation,
  incremental commits) applied a third time, in a third language, to
  a third kind of problem, proving it's a consistent habit, not a
  one-off for a single project

## Tech Stack

Java 21, Maven, JUnit 5, GitHub Actions, JMH. No external database, no
paid service, no hosting required, the entire project runs locally at
zero cost.
