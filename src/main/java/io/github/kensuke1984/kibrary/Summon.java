package io.github.kensuke1984.kibrary;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

import io.github.kensuke1984.kibrary.util.GadgetAid;

/**
 * A class for running {@link Brooklyn}s.
 *
 * @author otsuru
 * @since 2022/4/4
 */
public final class Summon {
    private Summon() {}

    /**
     * Runs a class listed in {@link Brooklyn}.
     *
     * @param args  none to choose a {@link Brooklyn} <br>
     *              "brooklynName [arguments]" to work for that {@link Brooklyn}
     * @throws IOException on failure of reading input
     * @throws ReflectiveOperationException on failure to invoke method from {@link Brooklyn}
     */
    public static void main(String[] args) throws IOException, ReflectiveOperationException {

        //~get brooklyn~//
        Brooklyn brooklyn;
        if (args.length > 0) {
            // when args exists, args[0] is the name of Brooklyn
            try {
                if (StringUtils.isNumeric(args[0])) {
                    brooklyn = Brooklyn.ofNumber(Integer.parseInt(args[0]));
                } else {
                    brooklyn = Brooklyn.valueOf(args[0]);
                }
            } catch (IllegalArgumentException iae) {
                System.out.println(args[0] + " is not a Brooklyn.");
                System.out.println("Please choose one in:");
                Brooklyn.printList();
                return;
            }
        } else {
            // read Brooklyn number from input
            Brooklyn.printList();
            System.out.print("Which one do you want to summon? [" + Brooklyn.numRange() + "] : ");
            String valInput = GadgetAid.readInputLine();
            if (valInput.isEmpty()) return;
            brooklyn = Brooklyn.ofNumber(Integer.parseInt(valInput));
        }

        //~get arguments~//
        Options options = brooklyn.getOptions();
        CommandLine cmdLine = null;
        if (options != null) {

            String[] argsInput;
            if (args.length > 1) {
                // remove args[0] because it is the name of Brooklyn
                // A new ArrayList is created here because the size of result of Arrays.asList() is fixed.
                List<String> argsListOriginal = new ArrayList<String>(Arrays.asList(args));
                argsListOriginal.remove(0);
                argsInput = argsListOriginal.toArray(new String[argsListOriginal.size()]);
            } else {
                // read arguments from input
                showUsage(options);
                System.out.print("Enter arguments : ");
                argsInput = GadgetAid.readInputLine().trim().split("\\s+");
            }

            try {
                cmdLine = parseArgs(options, argsInput);
            } catch (ParseException e) {
                showUsage(options);
                System.err.println("-------- coffee break ---------");
                Queens.noJokeNoScience();
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
        options.addOption("?", "help", false, "Show usage");
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

        if (comLine.hasOption("?")) throw new ParseException("help");

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
