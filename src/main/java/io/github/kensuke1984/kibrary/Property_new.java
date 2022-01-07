package io.github.kensuke1984.kibrary;

import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;

import io.github.kensuke1984.kibrary.util.GadgetAid;

/**
 * The property file to set parameters for {@link Operation_new}s.
 *
 * @author otsuru
 * @since a long time ago
 * @version 2022/1/7 Recreated the original Property to extend Properties instead of generating its instance.
 */
public class Property_new extends Properties {

    public static void main(String[] args) throws Exception {
        if (1 < args.length) {
            throw new IllegalArgumentException("Too many arguments. You can specify only one Manhattan.");
        } else if (args.length == 1) {
            try {
                Manhattan_new.valueOf(args[0]).writeDefaultPropertiesFile();
                return;
            } catch (IllegalArgumentException iae) {
                System.err.println(args[0] + " is not in Manhattan.");
                System.err.println("Please choose one in:");
                Manhattan_new.printList();
                return;
            }
        } else {
            Manhattan_new.printList();
            System.err.print("For which one do you want to create a property file? [1-" + Manhattan_new.values().length + "] ");
            String input = GadgetAid.readInputLine();
            if (input.isEmpty()) System.exit(9);
            Manhattan_new.valueOf(Integer.parseInt(input)).writeDefaultPropertiesFile();
        }
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

    /**
     * Creates a new empty instance.
     */
    public Property_new() {
        super();
    }

    /**
     * Gets a pre-specified value, or sets a default value, and returns it in String.
     * @param key (String) Name of key. Must not be empty.
     * @param defaltValue (String) Default value to set to the key. Require a value to be specified by setting this null.
     * @return
     */
    public String parseString(String key, String defaltValue) {
        checkAndPutDefault(key, defaltValue);
        return getProperty(key);
    }

    /**
     * Gets a pre-specified value, or sets a default value, and returns it as boolean.
     * @param key (String) Name of key. Must not be empty.
     * @param defaltValue (String) Default value to set to the key. Require a value to be specified by setting this null.
     * @return
     */
    public boolean parseBoolean(String key, String defaltValue) {
        checkAndPutDefault(key, defaltValue);
        return Boolean.parseBoolean(getProperty(key));
    }

    /**
     * Gets a pre-specified value, or sets a default value, and returns it as int.
     * @param key (String) Name of key. Must not be empty.
     * @param defaltValue (String) Default value to set to the key. Require a value to be specified by setting this null.
     * @return
     */
    public int parseInt(String key, String defaltValue) {
        checkAndPutDefault(key, defaltValue);
        return Integer.parseInt(getProperty(key));
    }

    /**
     * Gets a pre-specified value, or sets a default value, and returns it as double.
     * @param key (String) Name of key. Must not be empty.
     * @param defaltValue (String) Default value to set to the key. Require a value to be specified by setting this null.
     * @return
     */
    public double parseDouble(String key, String defaltValue) {
        checkAndPutDefault(key, defaltValue);
        return Double.parseDouble(getProperty(key));
    }

    /**
     * Gets a pre-specified value, or sets a default value, and returns it as Path.
     * @param key (String) Name of key. Must not be empty.
     * @param defaltValue (String) Default value to set to the key. Require a value to be specified by setting this null.
     * @param requireExisting (boolean) When true, checks whether the path exists
     * @param workPath
     * @return
     * @throws NoSuchFileException if the specified path does not exist
     */
    public Path parsePath(String key, String defaltValue, boolean requireExisting, Path workPath) throws NoSuchFileException {
        checkAndPutDefault(key, defaltValue);
        String pathString = getProperty(key).trim();

        Path path;
        if (pathString.startsWith("/")) path = Paths.get(pathString);
        else path = workPath.resolve(pathString);

        if (requireExisting && (Files.exists(path) == false))
            throw new NoSuchFileException("The " + key +  " " + path + " does not exist");

        return path;
    }

    private void checkAndPutDefault(String key, String defaultValue) {
        if (StringUtils.isEmpty(key)) {
            throw new IllegalArgumentException("Key is empty!");
        }
        if (!containsKey(key)) {
            if (defaultValue == null) {
                throw new IllegalArgumentException(key + " must be specified.");
            } else {
                setProperty(key, defaultValue);
            }
        }
    }


}
