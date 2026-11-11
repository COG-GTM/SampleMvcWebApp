package com.northwind.scheduling.characterization.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/** Starts N tasks behind a barrier so they hit the target at the same moment. */
public final class Race implements AutoCloseable {

    public static final int ITERATIONS = Integer.getInteger("concurrency.iterations", 25);
    public static final int THREADS = Integer.getInteger("concurrency.threads", 8);

    private final ExecutorService pool;
    private final int threads;

    public Race(int threads) {
        this.threads = threads;
        this.pool = Executors.newFixedThreadPool(threads);
    }

    public <T> List<T> run(IntFunction<T> task) {
        CyclicBarrier gate = new CyclicBarrier(threads);
        List<Future<T>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                gate.await(30, TimeUnit.SECONDS);
                return task.apply(idx);
            }));
        }
        List<T> out = new ArrayList<>();
        for (Future<T> f : futures) {
            try {
                out.add(f.get(120, TimeUnit.SECONDS));
            } catch (Exception e) {
                throw new IllegalStateException("race task failed", e);
            }
        }
        return out;
    }

    @Override
    public void close() {
        pool.shutdownNow();
    }
}
