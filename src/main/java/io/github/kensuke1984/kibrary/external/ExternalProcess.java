package io.github.kensuke1984.kibrary.external;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.stream.Stream;

/**
 * ExternalProcess
 * 
 * Bit bucket is /dev/null and nul for unix and windows system, respectively.
 * 
 * @author Kensuke Konishi
 * 
 * @version 0.1.0.1
 * 
 */
public class ExternalProcess {
	/**
	 * {@link Stream} for standard output
	 */
	protected InputStreamThread standardOutput;

	/**
	 * {@link Process} for Sac
	 */
	protected Process process;

	/**
	 * {@link Stream} for standard error
	 */
	protected InputStreamThread standardError;

	/**
	 * connected to standard input
	 */
	protected OutputStream standardInput;

	ExternalProcess(Process process) {
		this.process = process;
		standardOutput = new InputStreamThread(process.getInputStream());
		standardError = new InputStreamThread(process.getErrorStream());
		standardError.start();
		standardOutput.start();
		standardInput = process.getOutputStream();
	}

	public static ExternalProcess launch(String... command) throws IOException {
		ProcessBuilder builder = new ProcessBuilder(command);
		return new ExternalProcess(builder.start());
	}

	final static File bitBucket; // TODO check in Windows

	static {
		bitBucket = System.getProperty("os.name").contains("Windows") ? new File("null") : new File("/dev/null");
		if(!bitBucket.exists())
			throw new RuntimeException("There is no BLACK HOLE.");
	}

	/**
	 * @return {@link OutputStream} connected to a standard input to the process
	 */
	public OutputStream getStandardInput() {
		return standardInput;
	}

	/**
	 * @return {@link InputStreamThread} connected to a standard output to the
	 *         process
	 */
	public InputStreamThread getStandardOutput() {
		return standardOutput;
	}

	/**
	 * @return {@link InputStreamThread} connected to a standard error to the
	 *         process
	 */
	public InputStreamThread getStandardError() {
		return standardError;
	}

	public static ExternalProcess launch(List<String> command) throws IOException {
		return new ExternalProcess(new ProcessBuilder(command).start());
	}

	public int waitFor() {
		try {
			int process = this.process.waitFor();
			standardError.join();
			standardOutput.join();
			return process;
		} catch (Exception e) {
			throw new RuntimeException();
		}

	}

	/**
	 * This method uses /usr/bin/which
	 * 
	 * @param executable
	 *            to look for
	 * @return if the executable is found in PATH
	 */
	public static boolean isInPath(String executable) {
		ProcessBuilder check = new ProcessBuilder("/usr/bin/which", executable);
		try {
			return check.start().waitFor() == 0;
		} catch (InterruptedException | IOException e) {
			e.printStackTrace();
			return false;
		}
	}

}
