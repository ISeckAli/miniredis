package com.ivan.miniredis.benchmark;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import com.ivan.miniredis.core.Store;

/**
 * JMH benchmark measuring sustained SET and GET throughput on Store.
 *
 * JMH is used here rather than hand-rolled timing (wrapping
 * System.currentTimeMillis() around a loop) because naive timing
 * produces misleading results: the JVM's JIT compiler runs code slowly
 * at first and only reaches full speed after warm-up, so a naive
 * benchmark measures a blend of cold and warm execution rather than
 * genuine sustained throughput. JMH runs proper warm-up iterations
 * before measuring, and guards against the JVM silently optimizing
 * away work whose result is never used (dead code elimination),
 * producing a number that actually reflects real performance.
 */
@State(Scope.Benchmark)
public class StoreBenchmark {

    private Store store;
    private int counter;

    @Setup
    public void setup() {
        store = new Store(100_000); // large capacity so this benchmark measures raw operation speed, not eviction overhead
        counter = 0;
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void setOperation() {
        counter++;
        store.set("key" + (counter % 10_000), "value" + counter);
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public Object getOperation() {
        return store.get("key" + (counter % 10_000));
    }

    /**
     * Allows running this benchmark directly (java -jar benchmarks.jar)
     * without needing JMH's command-line launcher syntax memorized.
     */
    public static void main(String[] args) throws RunnerException {
        Options options = new OptionsBuilder()
            .include(StoreBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(3)
            .measurementIterations(5)
            .build();

        new Runner(options).run();
    }
}