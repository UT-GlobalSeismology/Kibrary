package io.github.kensuke1984.kibrary.util;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * This class was created to handle exceptions from threads that will not be caught.
 * However, when using {@link ExecutorService#execute(Runnable)}, exceptions will be caught,
 * so this class should be unneeded.
 *
 * @author otsuru
 * @since 2021/11/12
 * @deprecated until this becomes needed for some reason.
 */
public class UncaughtExceptionAid {

    public static class MyHandler implements Thread.UncaughtExceptionHandler {
        @Override
        public void uncaughtException(Thread t, Throwable e) {
            System.err.println("Uncaught exception:");
            e.printStackTrace();
        }
    }

    public static class AidedThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable r) {
            final Thread thread = new Thread(r);
            thread.setUncaughtExceptionHandler(new MyHandler());
            return thread;
        }
    }

    public static ExecutorService createAidedExecutorService () {
        int nThreads = Runtime.getRuntime().availableProcessors();
        System.err.println("Running on " + nThreads + " processors");
        ExecutorService es = Executors.newFixedThreadPool(nThreads, new AidedThreadFactory());
        return es;
    }
}
