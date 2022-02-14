package io.github.kensuke1984.kibrary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.commons.lang3.StringUtils;

import io.github.kensuke1984.kibrary.util.GadgetAid;

/**
 * The property file to set parameters for {@link Operation}s.
 * <p>
 * In property files, all keys must either have a non-empty value or be commented out. Keys with empty values shall not exist.
 *
 * @author otsuru
 * @since a long time ago
 * @version 2022/1/7 Recreated the original Property to extend Properties instead of generating its instance.
 */
public class Property extends Properties {

    /**
     * Creates a new property file for an {@link Operation} listed in {@link Manhattan}.
     *
     * @param args  none to choose an operation <br>
     *              [operation name] to work for that operation
     * @throws IOException on failure of reading input
     * @throws ReflectiveOperationException on failure to invoke method from {@link Manhattan}
     */
    public static void main(String[] args) throws IOException, ReflectiveOperationException {
        if (1 < args.length) {
            throw new IllegalArgumentException("Too many arguments. You can specify only one Manhattan.");
        } else if (args.length == 1) {
            try {
                Manhattan.valueOf(args[0]).writeDefaultPropertiesFile();
                return;
            } catch (IllegalArgumentException iae) {
                System.err.println(args[0] + " is not in Manhattan.");
                System.err.println("Please choose one in:");
                Manhattan.printList();
                return;
            }
        } else {
            Manhattan.printList();
            System.err.print("For which one do you want to create a property file? [1-" + Manhattan.values().length + "] ");
            String input = GadgetAid.readInputLine();
            if (input.isEmpty()) System.exit(9);
            Manhattan.valueOf(Integer.parseInt(input)).writeDefaultPropertiesFile();
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
    public Property() {
        super();
    }

    /**
     * Outputs properties in a file.
     * @param path
     * @throws IOException
     */
    public void write(Path path) throws IOException {
        String manhattan = (containsKey("manhattan") ? getProperty("manhattan") : "(unknown manhattan)");
        store(Files.newBufferedWriter(path), "Properties for " + manhattan);
    }

    /**
     * Gets a pre-specified value, or sets a default value, and returns it in String.
     * @param key (String) Name of key. Must not be empty.
     * @param defaltValue (String) Default value to set to the key. Require a value to be specified by setting this null.
     *          This must not be "".
     * @return
     */
    public String parseString(String key, String defaltValue) {
        return checkAndPutDefault(key, defaltValue);
    }

    /**
     * Gets a pre-specified value, or sets a default value, and returns it as boolean.
     * @param key (String) Name of key. Must not be empty.
     * @param defaltValue (String) Default value to set to the key. Require a value to be specified by setting this null.
     *          This must not be "".
     * @return
     */
    public boolean parseBoolean(String key, String defaltValue) {
        return Boolean.parseBoolean(checkAndPutDefault(key, defaltValue));
    }

    /**
     * Gets a pre-specified value, or sets a default value, and returns it as int.
     * @param key (String) Name of key. Must not be empty.
     * @param defaltValue (String) Default value to set to the key. Require a value to be specified by setting this null.
     *          This must not be "".
     * @return
     */
    public int parseInt(String key, String defaltValue) {
        return Integer.parseInt(checkAndPutDefault(key, defaltValue));
    }

    /**
     * Gets a pre-specified value, or sets a default value, and returns it as double.
     * @param key (String) Name of key. Must not be empty.
     * @param defaltValue (String) Default value to set to the key. Require a value to be specified by setting this null.
     *          This must not be "".
     * @return
     */
    public double parseDouble(String key, String defaltValue) {
        return Double.parseDouble(checkAndPutDefault(key, defaltValue));
    }

    /**
     * Gets a pre-specified value, or sets a default value, and returns it as Path.
     * @param key (String) Name of key. Must not be empty.
     * @param defaltValue (String) Default value to set to the key. Require a value to be specified by setting this null.
     *          This must not be ""; use "." for current path.
     * @param requireExisting (boolean) When true, checks whether the path exists
     * @param workPath
     * @return
     * @throws NoSuchFileException if the specified path does not exist
     */
    public Path parsePath(String key, String defaltValue, boolean requireExisting, Path workPath) throws NoSuchFileException {
        String pathString = checkAndPutDefault(key, defaltValue);

        Path path;
        if (pathString.startsWith("/")) path = Paths.get(pathString);
        else path = workPath.resolve(pathString).normalize();

        if (requireExisting && (Files.exists(path) == false))
            throw new NoSuchFileException("The " + key +  " " + path + " does not exist");

        return path;
    }

    /**
     * @param key (String) Name of key to check, must not be empty
     * @param defaultValue (String) Value to set to the key if it is not yet specified.
     *          Require a value to be specified by setting this null. This must not be "".
     * @return (String) value to the correcponding key, with subsequent spaces trimmed.
     */
    private String checkAndPutDefault(String key, String defaultValue) {
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

        String value = getProperty(key).trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(key + " must not be blank.");
        }
        return value;
    }

}
