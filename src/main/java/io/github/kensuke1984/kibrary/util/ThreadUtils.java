package io.github.kensuke1984.kibrary.util;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class ThreadUtils {

    private ThreadUtils() {
    }

    public static ExecutorService createFixedThreadPool() {
        int nThreads = Runtime.getRuntime().availableProcessors();
        System.err.println(nThreads + " processors available.");
        return Executors.newFixedThreadPool(nThreads);
    }

    public static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    /**
     * @param <T>  The result type.
     * @param task to put in another thread
     * @return Future of the task
     */
    public static <T> Future<T> run(Callable<T> task) {
        FutureTask<T> ft = new FutureTask<>(task);
        new Thread(ft).start();
        return ft;
    }

    /**
     * Runs process for all event folders under the workPath
     *
     * @param workPath where this looks for event folders
     * @param process  {@link Consumer} for each event
     * @param timeout  timeout for the process
     * @param unit     unit of the timeout
     * @return elapsed time [nano second]
     * @throws RuntimeException if the process takes over 30 minutes
     * @throws IOException          if an I/O error occurs
     */
    public static long runEventProcess(Path workPath, Consumer<EventFolder> process, long timeout, TimeUnit unit)
            throws IOException {
        long startTime = System.nanoTime();
    
        ExecutorService exec = createFixedThreadPool();
        for (EventFolder eventDirectory : DatasetUtils.eventFolderSet(workPath))
            exec.execute(() -> process.accept(eventDirectory));
        exec.shutdown();
        try {
            if(!exec.awaitTermination(timeout, unit))
                throw new RuntimeException(timeout + " " + unit + " elapsed.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return System.nanoTime() - startTime;
    }


}
