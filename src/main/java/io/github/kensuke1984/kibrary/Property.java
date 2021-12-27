package io.github.kensuke1984.kibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import io.github.kensuke1984.kibrary.util.GadgetAid;

/**
 * This class will create a default properties file for a procedure in Kibrary.
 *
 * @author Kensuke Konishi
 * @version 0.0.5
 */
public class Property {

    public static void main(String[] args) throws Exception {
        if (1 < args.length) throw new IllegalArgumentException("Too many arguments. You can specify only one Manhattan.");
        if (args.length == 1) {
            try {
                Manhattan.valueOf(args[0]).writeDefaultPropertiesFile();
                return;
            } catch (IllegalArgumentException iae) {
                System.err.println(args[0] + " is not in Manhattan.");
                System.err.println("Please choose one in:");
                Manhattan.printList();
                return;
            }
        }
        Manhattan.printList();
        System.err.print("For which one do you want to create a property file? [1-" + Manhattan.values().length + "] ");
        String input = GadgetAid.readInputLine();
        if (input.isEmpty()) System.exit(1);
        Manhattan.valueOf(Integer.parseInt(input)).writeDefaultPropertiesFile();
    }

    public static Properties parse(String[] args) throws IOException {
        Properties property = new Properties();
        if (args.length == 0) property.load(Files.newBufferedReader(Operation.findPath()));
        else if (args.length == 1) property.load(Files.newBufferedReader(Paths.get(args[0])));
        else throw new IllegalArgumentException("Too many arguments. There should be 0 or 1 ( = property file name)");
        return property;
    }

    /**
     * Generates a path of a properties file, for a specified class.
     * @param classInstance The class to use for the file name
     * @return Path for a new properties file
     *
     * @since 2021/12/12
     * @author otsuru
     */
    public static Path generatePath(Class<?> classInstance) {
        return Paths.get(classInstance.getSimpleName() + GadgetAid.getTemporaryString() + ".properties");
    }

    private Property() {
    }
}
