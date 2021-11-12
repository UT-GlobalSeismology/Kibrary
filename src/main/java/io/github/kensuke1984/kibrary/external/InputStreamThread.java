package io.github.kensuke1984.kibrary.external;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 * A class of a new thread to read either the standard output or the standard error from an external process.
 * <p>
 * This class is after <a
 * href=http://www.ne.jp/asahi/hishidama/home/tech/java/process.html#ProcessBuilder>here</a>
 * <p>
 * 外部プログラムを実行時の標準出力 標準エラーの読み込みスレッド You may have to {@link #join()} after the
 * external program finishes(). {@link ExternalProcess#waitFor()}
 *
 * @author Kensuke Konishi
 * @version 0.0.2.1
 */
public class InputStreamThread extends Thread {

    /**
     * if the stream is closed
     */
    private boolean closed;
    private final List<String> LIST = new ArrayList<>();
    private final InputStreamReader INPUT_STREAM_READER;

    public InputStreamThread(InputStream is) {
        INPUT_STREAM_READER = new InputStreamReader(is);
    }

    /**
     * Keeps reading the output from the stream.
     * The buffer of the output must be kept reading, or else, the process will freeze when the buffer becomes full.
     * This method will be executed inside this thread when the start() method is called.
     */
    @Override
    public void run() {
        // By using try-with-resources, br will certainly be closed.
        // INPUT_STREAM_READER, and also the InputStream used to make it, will then be closed as well.
        try (BufferedReader br = new BufferedReader(INPUT_STREAM_READER)) {
            for (; ; ) {
                String line = br.readLine();
                if (line == null) break;
                LIST.add(line);
            }
        } catch (IOException e) {
            // Exceptions from threads will not be caught, so output the exception here,
            // and throw RuntimeException to terminate the thread.
            e.printStackTrace();
            throw new RuntimeException(e);
        } finally {
            closed = true;
        }
    }

    /**
     * Wait until the inputstream is closed and return String[]
     *
     * @return {@link String}[] from input stream
     */
    public String[] waitAndGetString() {
        try {
            while (!closed) Thread.sleep(100);
        } catch (InterruptedException e) {
            // This method is called from a master thread, so exceptions can be caught in higher levels.
            // InterruptedException means that someone wants the master thread to stop,
            // but the 'interrupted' flag is reset when InterruptedException is thrown in sleep(),
            // so the flag should be set back up.
            // Then, throw RuntimeException to halt the program.
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return LIST.toArray(new String[0]);
    }

    /**
     * Get List of String from the stream
     * @return {@link List} of {@link String} from the {@link InputStream}
     */
    public List<String> getStringList() {
        return new ArrayList<>(LIST);
    }

}
