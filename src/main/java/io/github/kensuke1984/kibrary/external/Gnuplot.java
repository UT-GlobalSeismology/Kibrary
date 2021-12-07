package io.github.kensuke1984.kibrary.external;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.NoSuchFileException;

/**
 * Gnuplot dialog
 *
 * @author Kensuke Konishi
 * @version 0.0.2
 */
public class Gnuplot extends ExternalProcess implements Closeable {
    private PrintWriter standardInput;

    private Gnuplot(Process process) {
        super(process);
        standardInput = new PrintWriter(super.standardInputStream);
    }

    public static Gnuplot createProcess() throws IOException {
        if (isInPath("gnuplot")) return new Gnuplot(new ProcessBuilder("gnuplot").start());
        throw new NoSuchFileException("No gnuplot in PATH.");
    }

    /**
     * make orders in Gnuplot
     *
     * @param line (String) Command line for gnuplot
     */
    public void inputCMD(String line) {
        synchronized (super.standardInputStream) {
            standardInput.println(line);
            standardInput.flush();
        }
    }

    @Override
    public void close() {
        standardInput.println("q");
        standardInput.flush();
        standardInput.close();
        super.waitFor();
    }


}
