package com.samplemvcwebapp.web.models;

import java.lang.management.ManagementFactory;
import java.util.concurrent.ForkJoinPool;

/**
 * Port of SampleWebApp/Models/InternalsInfo. The .NET ThreadPool numbers become the JVM common pool (which runs the
 * async services), available physical memory and used heap.
 */
public record InternalsInfo(int workerThreads, int availableThreads, int availableMbytes, int heapMemoryUsedKbytes) {

    public static InternalsInfo capture() {
        ForkJoinPool pool = ForkJoinPool.commonPool();
        int workers = pool.getParallelism();
        int available = Math.max(0, workers - pool.getActiveThreadCount());
        long freeBytes = ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean os
                ? os.getFreeMemorySize() : Runtime.getRuntime().freeMemory();
        Runtime rt = Runtime.getRuntime();
        long usedHeap = rt.totalMemory() - rt.freeMemory();
        return new InternalsInfo(workers, available, (int) (freeBytes / (1024 * 1024)), (int) (usedHeap / 1000));
    }
}
