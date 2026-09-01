package com.fabricio.payments.performance;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class VirtualThreadsVsPlatformThreadsTest {

    @Test
    void shouldCompareThreadModelsForIoBoundAndCpuBoundWork() throws Exception {
        int taskCount = 200;

        long platformIoElapsed = runIoBoundScenario(Executors.newFixedThreadPool(32), taskCount, false);
        long virtualIoElapsed = runIoBoundScenario(Executors.newVirtualThreadPerTaskExecutor(), taskCount, true);
        long platformCpuElapsed = runCpuBoundScenario(Executors.newFixedThreadPool(8), taskCount, false);
        long virtualCpuElapsed = runCpuBoundScenario(Executors.newVirtualThreadPerTaskExecutor(), taskCount, true);

        assertThat(platformIoElapsed).isGreaterThan(0L);
        assertThat(virtualIoElapsed).isGreaterThan(0L);
        assertThat(platformCpuElapsed).isGreaterThan(0L);
        assertThat(virtualCpuElapsed).isGreaterThan(0L);

        System.out.println("=== Virtual Threads vs Platform Threads benchmark ===");
        System.out.println("I/O-bound platform threads elapsed ms: " + platformIoElapsed);
        System.out.println("I/O-bound virtual threads elapsed ms: " + virtualIoElapsed);
        System.out.println("CPU-bound platform threads elapsed ms: " + platformCpuElapsed);
        System.out.println("CPU-bound virtual threads elapsed ms: " + virtualCpuElapsed);
        System.out.println("I/O scenario improvement factor: " + formatRatio(platformIoElapsed, virtualIoElapsed));
        System.out.println("CPU scenario improvement factor: " + formatRatio(platformCpuElapsed, virtualCpuElapsed));
        System.out.println("Virtual threads are especially beneficial for I/O-bound workloads because they reduce blocking overhead, while CPU-bound tasks remain limited by processor cores.");
        System.out.println("Database-bound workloads still depend on HikariCP pool capacity and PostgreSQL connection limits, so additional threads do not create additional physical connections.");
    }

    private long runIoBoundScenario(ExecutorService executor, int taskCount, boolean useVirtualThreads) throws Exception {
        long start = System.nanoTime();
        CountDownLatch latch = new CountDownLatch(taskCount);
        List<CompletableFuture<Long>> futures = new ArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            final int index = i;
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    simulateIoBlocking(index);
                    return System.nanoTime();
                } finally {
                    latch.countDown();
                }
            }, executor));
        }

        latch.await(30, TimeUnit.SECONDS);
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        System.out.println((useVirtualThreads ? "Virtual" : "Platform") + " I/O-bound tasks: " + elapsedMs + " ms");
        return elapsedMs;
    }

    private long runCpuBoundScenario(ExecutorService executor, int taskCount, boolean useVirtualThreads) throws Exception {
        long start = System.nanoTime();
        List<Callable<Long>> tasks = new ArrayList<>();

        for (int i = 0; i < taskCount; i++) {
            tasks.add(() -> {
                long value = 0L;
                for (int j = 0; j < 250_000; j++) {
                    value += j * 2L + 1L;
                }
                return value;
            });
        }

        executor.invokeAll(tasks, 30, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(30, TimeUnit.SECONDS);
        long elapsedMs = Duration.ofNanos(System.nanoTime() - start).toMillis();
        System.out.println((useVirtualThreads ? "Virtual" : "Platform") + " CPU-bound tasks: " + elapsedMs + " ms");
        return elapsedMs;
    }

    private void simulateIoBlocking(int id) {
        try {
            TimeUnit.MILLISECONDS.sleep(25L + (id % 10));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating I/O-bound task", e);
        }
    }

    private String formatRatio(long baselineMs, long candidateMs) {
        if (candidateMs == 0L) {
            return "N/A";
        }
        double ratio = (double) baselineMs / candidateMs;
        return String.format("%.2fx", ratio);
    }
}
