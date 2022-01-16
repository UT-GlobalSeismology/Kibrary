package io.github.kensuke1984.kibrary;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.EnumUtils;

import io.github.kensuke1984.kibrary.util.GadgetAid;

/**
 * Parent class of all operations in Kibrary that are executed given a property file.
 * @author otsuru
 * @since a long time ago
 * @version 2022/1/7 Recreated the original interface Operation into an abstract class.
 */
public abstract class Operation_new {

    /**
     * Runs an {@link Operation_new} using a {@link Property_new} file.
     * The {@link Operation_new} must be listed in {@link Manhattan_new}.
     *
     * @param args  none to choose a property file <br>
     *              [property file] to run an operation <br>
     *               -l to show the list of operations
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {

        Property_new property = new Property_new();
        if (1 < args.length) {
            throw new IllegalArgumentException("Too many arguments. You can specify only one property file.");
        } else if (args.length == 0) {
            property.load(Files.newBufferedReader(findPath()));
        } else if (args[0].equals("-l")) {
            Manhattan_new.printList();
            return;
        } else {
            property.load(Files.newBufferedReader(Paths.get(args[0])));
        }

        if (!property.containsKey("manhattan")) {
            throw new IllegalArgumentException("'manhattan' is not set in " + args[0]);
        }
        String manhattan = property.getProperty("manhattan");
        if (!EnumUtils.isValidEnum(Manhattan_new.class, manhattan)) {
            throw new IllegalArgumentException(manhattan + " is not a valid name of Manhattan.");
        }

        operate(Manhattan_new.valueOf(manhattan).getOperation(), property);

    }

    private static Path findPath() throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Paths.get("."), "*.properties")) {
            List<Path> list = new ArrayList<>();
            int i = 1;
            for (Path path : stream) {
                System.err.println(i++ + ": " + path);
                list.add(path);
            }
            if (list.isEmpty()) {
                System.err.println("No property file is found");
                System.exit(9);
            }
            System.err.print("Which one do you want to use as a property file? [1-" + list.size() + "] ");
            String input = GadgetAid.readInputLine();
            if (input.isEmpty()) System.exit(9);
            return list.get(Integer.parseInt(input) - 1);
        }
    }

    /**
     * Method to be called from the main method of each class extending {@link Operation_new}.
     * This operates the {@link Operation_new} that called this class,
     * regardless of the 'manhattan' property written in the specified property file.
     * This enables {@link Operation_new}s that are not listed in {@link Manhattan_new} to be operated.
     *
     * @param args [property file name]
     * @throws IOException if the property file cannot be loaded
     */
    public static void mainFromSubclass(String[] args) throws IOException {

        Property_new property = new Property_new();
        if (1 < args.length) {
            throw new IllegalArgumentException("Too many arguments. You can specify only one property file.");
        } else if (args.length == 0) {
            throw new IllegalArgumentException("A property file must be specified.");
        } else {
            property.load(Files.newBufferedReader(Paths.get(args[0])));
        }

        // get the fully-qualified class name of the Operation that has called this method
        String operationClassName = Thread.currentThread().getStackTrace()[2].getClassName();
        // get the Class instance of the Operation
        Class<? extends Operation_new> operationClass;
        try {
            operationClass = Class.forName(operationClassName).asSubclass(Operation_new.class);
        } catch (Exception e) {
            System.err.println("Could not get " + operationClassName);
            e.printStackTrace();
            return;
        }

        operate(operationClass, property);

    }

    /**
     * Operates an {@link Operation_new} with the specified {@link Property_new} file.
     * @param operationClass Class that extends {@link Operation_new}
     * @param property {@link Property_new} file
     */
    public static void operate(Class<? extends Operation_new> operationClass, Property_new property) {

        // construct
        Operation_new operation;
        try {
            Constructor<? extends Operation_new> constructor = operationClass.getConstructor(Property_new.class);
            operation = constructor.newInstance(property);
        } catch (InvocationTargetException e) {
            System.err.println("Could not construct " + operationClass.getName() + " due to " + e.getCause());
            e.printStackTrace();
            return;
        } catch (Exception e) {
            System.err.println("Could not construct " + operationClass.getName());
            e.printStackTrace();
            return;
        }

        // set up
        try {
            operation.set();
        } catch (Exception e) {
            System.err.println("Could not set up " + operationClass.getName());
            e.printStackTrace();
            return;
        }

        long startTime = System.nanoTime();
        System.err.println(operationClass.getName() + " is operating.");

        // run
        try {
            operation.run();
        } catch (Exception e) {
            //System.err.println("Could not run " + operationClass.getName());
            e.printStackTrace();
            return;
        }

        System.err.println(operationClass.getName() + " finished in " +
                GadgetAid.toTimeString(System.nanoTime() - startTime));

    }

    abstract public void set() throws IOException;

    abstract public void run() throws IOException;


}
