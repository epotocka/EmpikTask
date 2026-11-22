package com.example.empiktask.support;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public final class ConcurrentRunner {

    private static final long TIMEOUT_SECONDS = 60;

    private ConcurrentRunner() {
    }

    public static <T> List<T> runConcurrently(List<Callable<T>> tasks)
            throws InterruptedException, ExecutionException, TimeoutException {
        CountDownLatch startSignal = new CountDownLatch(1);
        List<Future<T>> futures = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(tasks.size())) {
            for (Callable<T> task : tasks) {
                futures.add(executor.submit(() -> {
                    startSignal.await();
                    return task.call();
                }));
            }
            startSignal.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS));
            }
            return results;
        }
    }
}
