package io.github.kensuke1984.kibrary;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

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
        Options options = brooklyn.getOptions();
        CommandLine cmdLine = null;
        if (options != null) {
            showUsage(options);
            System.out.print("Enter arguments : ");
            String[] argsInput = GadgetAid.readInputLine().trim().split("\\s+");
            try {
                cmdLine = parseArgs(options, argsInput);
            } catch (ParseException e) {
                showUsage(options);
                return;
            }
        }

        //~run brooklyn~//
        System.err.println(brooklyn.getClassName() + " is running.");
        try {
            brooklyn.summon(cmdLine);
        } catch (InvocationTargetException e) {
            System.err.println("InvocationTargetException caused by");
            e.getCause().printStackTrace();
            return;
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        System.err.println(brooklyn.getClassName() + " finished.");

    }

    /**
     * Create a new {@link Options} instance including default options.
     * @return an options instance
     *
     * @author otsuru
     * @since 2022/4/20
     */
    public static Options defaultOptions() {
        Options options = new Options();
        options.addOption("h", "help", false, "Show usage");
        return options;
    }

    /**
     * Reads command line arguments according to a set of {@link Options} and sets them in a {@link CommandLine} object.
     * @param options (Options) The set of options accepted in a command line
     * @param args (String[]) Command line input
     * @return (CommandLine) Parsed arguments
     * @throws ParseException if input is illegal, or if help should be shown
     *
     * @author otsuru
     * @since 2022/4/20
     */
    public static CommandLine parseArgs(Options options, String[] args) throws ParseException {
        CommandLineParser parser = new DefaultParser();
        CommandLine comLine = parser.parse(options, args);

        if (comLine.hasOption("h")) throw new ParseException("help");

        return comLine;
    }

    /**
     * Shows the usage of a set of options.
     * @param (Options) The set of options accepted in a command line
     *
     * @author otsuru
     * @since 2022/4/20
     */
    public static void showUsage(Options options) {
        HelpFormatter formatter = new HelpFormatter();
        // to display options in order of declaration, reset the comparator with null
        formatter.setOptionComparator(null);
        // cmdLineSyntax (the first argument) is set blank here because it will become too long
        formatter.printHelp(" ", options, true);
    }

}
