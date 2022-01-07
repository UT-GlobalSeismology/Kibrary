package io.github.kensuke1984.kibrary;

import java.io.IOException;
import java.lang.reflect.Constructor;
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

        try {
            operate(property,  Manhattan_new.valueOf(manhattan));
        } catch (Exception e) {
            System.err.println("Could not run " + manhattan + " due to " + e.getCause());
            e.printStackTrace();
        }

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

    public static void operate(Property_new property, Manhattan_new manhattan) throws IOException, ReflectiveOperationException {
        Constructor<? extends Operation_new> constructor = manhattan.getOperation().getConstructor(Property_new.class);
        Operation_new operation = constructor.newInstance(property);

        long startTime = System.nanoTime();
        System.err.println(manhattan.getFullClassName() + " is operating.");
        operation.run();
        System.err.println(manhattan.getFullClassName() + " finished in " +
                GadgetAid.toTimeString(System.nanoTime() - startTime));
    }

    abstract public void run() throws IOException;


}
