package io.github.kensuke1984.kibrary.external;

import java.io.Closeable;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.NoSuchFileException;

/**
 * Sac process made by SACLauncher
 *
 * @author Kensuke Konishi
 * @version 0.1.1
 */
public class SAC extends ExternalProcess implements Closeable {

    /**
     * Input for SAC
     */
    private final PrintWriter standardInput;

    private SAC(Process process) {
        super(process);
        standardInput = new PrintWriter(super.standardInputStream);
    }

    /**
     * @return SAC operating in a simple process 'sac'. Please care about the working folder.
     * @throws IOException if any
     */
    public static SAC createProcess() throws IOException {
        if (System.getenv("SACAUX") != null && isInPath("sac")) return new SAC(new ProcessBuilder("sac").start());
        throw new NoSuchFileException("No sac in PATH or SACAUX is not set.");
    }

    /**
     * Make an order to SAC
     *
     * @param line (String) Command line for SAC
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
