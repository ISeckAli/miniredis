# Benchmark Results

Measured with JMH (Java Microbenchmark Harness) 1.37, on the developer's
local machine.

## Run Details
- **Date:** September 25, 2026
- **JVM:** OpenJDK 25.0.4.1 (Eclipse Temurin, 64-bit Server VM)
- **JMH config:** 5 forks, 5 warmup iterations, 5 measurement iterations,
  10 seconds per iteration
- **Store capacity for this benchmark:** 100,000 entries (large enough
  that eviction never triggers during the run, isolating raw operation
  speed rather than eviction overhead)
- **Key space:** operations cycle through 10,000 distinct keys, avoiding
  both an unrealistically cache-friendly single-key benchmark and an
  ever-growing key count that would measure HashMap resizing rather
  than steady-state performance

## Results

| Benchmark  | Mode      | Count | Score (ops/s)  | Error (±)   |
|------------|-----------|-------|-----------------|-------------|
| `GET`      | Throughput| 25    | 42,590,934.586  | 860,655.092 |
| `SET`      | Throughput| 25    | 15,018,905.636  | 924,252.087 |

(Error is the 99.9% confidence interval half-width, as reported by JMH.)

## Interpretation

`GET` significantly outperforms `SET`, roughly 2.8x. This is expected
given the implementation: `GET` performs a HashMap lookup and moves the
accessed node to the front of the LRU list, while `SET` additionally
checks for an existing key, potentially evicts the least-recently-used
entry, allocates a new `Node`, and updates the HashMap, all while
holding the same `synchronized` lock. The asymmetry reflects genuinely
more work being done per `SET` call, not an inefficiency.

## Reproducing This Benchmark

```
mvn clean package
java -jar target/benchmarks.jar
```

Note: a full run with these settings takes approximately 15-20 minutes.