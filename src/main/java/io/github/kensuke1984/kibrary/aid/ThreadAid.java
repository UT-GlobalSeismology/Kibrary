package io.github.kensuke1984.kibrary.aid;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ThreadAid {

    private ThreadAid() {
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


}
