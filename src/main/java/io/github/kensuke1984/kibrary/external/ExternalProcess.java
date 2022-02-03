package io.github.kensuke1984.kibrary.external;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * A class that executes an external process.
 * {@link InputStreamThread} is used to read the standard output and standard error.
 * The buffer of the output must be kept reading, or else, the process will freeze when the buffer becomes full.
 * <p>
 * Bit bucket is /dev/null and nul for unix and windows system, respectively.
 *
 * @author Kensuke Konishi
 * @version 0.1.1
 */
public class ExternalProcess {
    static final File bitBucket; // TODO check in Windows

    static {
        bitBucket = System.getProperty("os.name").contains("Windows") ? new File("null") : new File("/dev/null");
        if (!bitBucket.exists()) throw new RuntimeException("There is no BLACK HOLE.");
    }

    /**
     * {@link Stream} for standard write (Output of the process, input for this program)
     */
    protected final InputStreamThread standardOutputThread;
    /**
     * {@link Stream} for standard error (Output of the process, input for this program)
     */
    protected final InputStreamThread standardErrorThread;
    /**
     * connected to standard input (Input of the process, output for this program)
     */
    protected final OutputStream standardInputStream;

    protected Process process;


    /**
     * Checks whether an executable exists in PATH.
     * This method uses /usr/bin/which
     *
     * @param executable to look for
     * @return if the executable is found in PATH
     */
    public static boolean isInPath(String executable) {
        ProcessBuilder check = new ProcessBuilder("/usr/bin/which", executable);
        try {
            return check.start().waitFor() == 0;
        } catch (InterruptedException | IOException e) {
            // Here, exceptions can be suppressed because they are used just to check whether the executable is in PATH.
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Starts executing a command using ExternalProcess, and returns that instance.
     * @param command (String) The command to be executed, using spaces to separate words
     * @param workpath (Path) Path of the working directory of the command
     * @return (ExternalProcess)
     * @throws IOException
     */
    public static ExternalProcess launch(String command, Path workpath) throws IOException {
        ProcessBuilder builder = new ProcessBuilder(command.split("\\s"));
        builder.directory(workpath.toFile());
        return new ExternalProcess(builder.start());
    }

    /**
     * New {@link InputStreamThread}s are started to read the standard output and standard error.
     * @param process (Process)
     */
    protected ExternalProcess(Process process) {
        this.process = process;
        standardInputStream = process.getOutputStream();
        standardOutputThread = new InputStreamThread(process.getInputStream());
        standardErrorThread = new InputStreamThread(process.getErrorStream());
        // By calling start(), the threads are activated and their run() methods are executed.
        standardOutputThread.start();
        standardErrorThread.start();
    }

    /**
     * @return {@link OutputStream} connected to the standard input to the process
     */
    public OutputStream getStandardInputStream() {
        return standardInputStream;
    }

    /**
     * @return {@link InputStreamThread} connected from the standard write of the process
     */
    public InputStreamThread getStandardOutputThread() {
        return standardOutputThread;
    }

    /**
     * @return {@link InputStreamThread} connected from the standard error of the process
     */
    public InputStreamThread getStandardErrorThread() {
        return standardErrorThread;
    }

    /**
     * Waits until everything is finished.
     * @return (int) The exit code of the process. 0 is returned in case of success.
     */
    public int waitFor() {
        try {
            int exit = this.process.waitFor();
            standardErrorThread.join();
            standardOutputThread.join();
            return exit;
        } catch (InterruptedException e) {
            // InterruptedException means that someone wants the current thread to stop,
            // but the 'interrupted' flag is reset when InterruptedException is thrown in sleep(),
            // so the flag should be set back up.
            // Then, throw RuntimeException to halt the program.
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

}
