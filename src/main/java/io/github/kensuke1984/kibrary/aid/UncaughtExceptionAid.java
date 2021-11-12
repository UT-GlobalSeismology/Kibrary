package io.github.kensuke1984.kibrary.aid;

public class UncaughtExceptionAid implements Thread.UncaughtExceptionHandler {

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        System.err.println("Uncaught exception:");
        e.printStackTrace();
    }

}
