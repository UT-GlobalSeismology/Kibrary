package io.github.kensuke1984.kibrary;

import java.io.IOException;

import io.github.kensuke1984.kibrary.util.GadgetAid;

/**
 * A class for running {@link Brooklyn}s.
 *
 * @author otsuru
 * @since 2022/4/4
 */
public class Summon {

    /**
     * Runs a class listed in {@link Brooklyn}.
     *
     * @param args  none to choose a {@link Brooklyn} <br>
     *              [operation name] to work for that {@link Brooklyn}
     * @throws IOException on failure of reading input
     * @throws ReflectiveOperationException on failure to invoke method from {@link Brooklyn}
     */
    public static void main(String[] args) throws IOException, ReflectiveOperationException {

        //~get brooklyn~//
        Brooklyn brooklyn;
        if (1 < args.length) {
            throw new IllegalArgumentException("Too many arguments. You can specify only one Brooklyn.");
        } else if (args.length == 1) {
            try {
                brooklyn = Brooklyn.valueOf(args[0]);
            } catch (IllegalArgumentException iae) {
                System.out.println(args[0] + " is not a Brooklyn.");
                System.out.println("Please choose one in:");
                Brooklyn.printList();
                return;
            }
        } else {
            Brooklyn.printList();
            System.out.print("Which one do you want to summon? [" + Brooklyn.numRange() + "] : ");
            String valInput = GadgetAid.readInputLine();
            if (valInput.isEmpty()) System.exit(9);
            brooklyn = Brooklyn.valueOf(Integer.parseInt(valInput));
        }

        //~get arguments~//
        String[] argsInput = {""};
        if (brooklyn.displayUsage()) {
            System.out.print("Enter arguments : ");
            argsInput = GadgetAid.readInputLine().trim().split("\\s+");
        }

        //~run brooklyn~//
        System.err.println(brooklyn.getClassName() + " is running.");
        try {
            brooklyn.summon(argsInput);
        } catch (Exception e) {
            System.err.println("Could not run " + brooklyn + " due to " + e.getCause());
            e.printStackTrace();
            return;
        }
        System.err.println(brooklyn.getClassName() + " finished.");

    }

}
