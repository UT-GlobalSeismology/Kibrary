/**
 * 
 */
package io.github.kensuke1984.kibrary;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.commons.lang3.EnumUtils;

import io.github.kensuke1984.kibrary.dsminformation.SshDSMInformationFileMaker;
import io.github.kensuke1984.kibrary.dsminformation.SyntheticDSMInformationFileMaker;
import io.github.kensuke1984.kibrary.selection.FilterDivider;
import io.github.kensuke1984.kibrary.timewindow.TimewindowMaker;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.spc.SpcSAC;

/**
 * 
 * 
 * Main procedures in Kibrary
 * 
 * @version 0.0.1
 * @author kensuke
 *
 */
public interface Operation {

	Properties getProperties();

	/**
	 * This method creates a file for the properties under the root. The file
	 * name will be 'spcsac????.properties'
	 * 
	 * @param root
	 *            a folder where the file will be created
	 * @throws IOException
	 */
	default void writeProperties(Path root) throws IOException {
		getProperties().store(
				Files.newBufferedWriter(
						root.resolve(getClass().getName() + Utilities.getTemporaryString() + ".properties")),
				"This properties for " + getClass().getName());
	}

	void run() throws Exception;

	static Path findPath() throws IOException {

		try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), "*.properties")) {
			List<Path> list = new ArrayList<>();
			int i = 0;
			for (Path path : stream) {
				System.out.println(i++ + ": " + path);
				list.add(path);
			}
			if (list.isEmpty())
				throw new NoSuchFileException("No property file is found");
			System.out.print("Which one do you want to use as a property file? [0-" + (list.size() - 1) + "]");
			BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
			return list.get(Integer.parseInt(reader.readLine()));
		}
	}

	/**
	 * @param args
	 *            [a name of procedure] (a property file) <br>
	 *            -l to show the list of procedures
	 * @throws Exception
	 * 
	 */
	public static void main(String[] args) throws Exception {
		if (args.length == 0) {
			Manhattan.printList();
			System.out.print("Which one do you want to operate? [0-" + (Manhattan.values().length - 1) + "]");
			args = new String[] { Manhattan.valueOf(Integer.parseInt(Utilities.readInputLine())).toString() };
		}

		if (2 < args.length)
			throw new IllegalArgumentException(
					"Usage: [a name of procedure] (a property file) or -l to list the names of procedures");
		if (args[0].equals("-l")) {
			Manhattan.printList();
			return;
		}

		if (!EnumUtils.isValidEnum(Manhattan.class, args[0]))
			throw new IllegalArgumentException(args[0] + " is not a name of Manhattan");

		String[] pass = args.length == 1 ? new String[0] : new String[] { args[1] };
		switch (Manhattan.valueOf(args[0])) {
		case SpcSAC:
			SpcSAC.main(pass);
			break;
		case TimewindowMaker:
			TimewindowMaker.main(pass);
			break;
		case FilterDivider:
			FilterDivider.main(pass);
			break;
		case SyntheticDSMInformationFileMaker:
			SyntheticDSMInformationFileMaker.main(pass);
			break;
		case SshDSMInformationFileMaker:
			SshDSMInformationFileMaker.main(pass);
			break;
		}

	}

}
