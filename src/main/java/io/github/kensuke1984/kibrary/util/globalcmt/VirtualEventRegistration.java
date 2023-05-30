package io.github.kensuke1984.kibrary.util.globalcmt;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;

/**
 * Register a virtual event in a custom globalCMT catalog.
 *
 * @author otsuru
 * @since 2023/5/30
 */
public class VirtualEventRegistration extends Operation {

    private final Property property;

    /**
     * @param args  none to create a property file <br>
     *              [property file] to run
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile();
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
        }
        System.err.println(outPath + " is created.");
    }

    public VirtualEventRegistration(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {


    }

    @Override
    public void run() throws IOException {



    // check that the ID is valid

    // check that the ID is not used yet

    }
}
