package io.github.kensuke1984.kibrary.util.globalcmt;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.math.CircularRange;
import io.github.kensuke1984.kibrary.math.LinearRange;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.EventListFile;

/**
 * List up {@link GlobalCMTID}s that satisfy certain criteria.
 *
 * @author otsuru
 * @since 2023/12/1
 */
public class GlobalCMTListup {
    private GlobalCMTListup() {}

    /**
     * List up {@link GlobalCMTID}s that satisfy certain criteria.
     * @param args Options.
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        Options options = defineOptions();
        try {
            run(Summon.parseArgs(options, args));
        } catch (ParseException e) {
            Summon.showUsage(options);
        }
    }

    /**
     * To be called from {@link Summon}.
     * @return options
     */
    public static Options defineOptions() {
        Options options = Summon.defaultOptions();

        // criteria
        options.addOption(Option.builder("t").longOpt("startDate").hasArg().argName("date")
                .desc("Start date in yyyy-mm-dd format, inclusive. (1990-01-01)").build());
        options.addOption(Option.builder("T").longOpt("endDate").hasArg().argName("date")
                .desc("End date in yyyy-mm-dd format, INCLUSIVE. (2020-12-31)").build());
        options.addOption(Option.builder("x").longOpt("lowerLongitude").hasArg().argName("longitude")
                .desc("Lower limit of longitude [deg], inclusive; [-180:360]. (-180)").build());
        options.addOption(Option.builder("X").longOpt("upperLongitude").hasArg().argName("longitude")
                .desc("Upper limit of longitude [deg], exclusive; [-180:360]. (180)").build());
        options.addOption(Option.builder("y").longOpt("lowerLatitude").hasArg().argName("latitude")
                .desc("Lower limit of latitude [deg], inclusive; [-90:upperLatitude). (-90)").build());
        options.addOption(Option.builder("Y").longOpt("upperLatitude").hasArg().argName("latitude")
                .desc("Upper limit of latitude [deg], exclusive; (lowerLatitude:90]. (90)").build());
        options.addOption(Option.builder("z").longOpt("lowerDepth").hasArg().argName("depth")
                .desc("SHALLOWER limit of DEPTH [km], inclusive; (:upperDepth). (100)").build());
        options.addOption(Option.builder("Z").longOpt("upperDepth").hasArg().argName("depth")
                .desc("DEEPER limit of DEPTH [km], exclusive; (lowerDepth:). (700)").build());
        options.addOption(Option.builder("m").longOpt("lowerMw").hasArg().argName("magnitude")
                .desc("Lower limit of Mw; (:upperMw), inclusive. (5.5)").build());
        options.addOption(Option.builder("M").longOpt("upperMw").hasArg().argName("magnitude")
                .desc("Upper limit of Mw; (lowerMw:), exclusive. (7.31)").build());

        // option
        options.addOption(Option.builder("f").longOpt("full")
                .desc("Whether to write full information of events in output file.").build());

        // output
        options.addOption(Option.builder("o").longOpt("output").hasArg().argName("outputFile")
                .desc("Specify path of output file.").build());

        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        Path outputPath = cmdLine.hasOption("o") ? Paths.get(cmdLine.getOptionValue("o"))
                : Paths.get("event" + GadgetAid.getTemporaryString() + ".lst");

        //~set search ranges
        LocalDate startDate = LocalDate.parse(cmdLine.hasOption("t") ? cmdLine.getOptionValue("t") : "1990-01-01");
        LocalDate endDate = LocalDate.parse(cmdLine.hasOption("T") ? cmdLine.getOptionValue("T") : "2020-12-31");
        MathAid.checkDateRangeValidity(startDate, endDate);

        double lowerLongitude = cmdLine.hasOption("x") ? Double.parseDouble(cmdLine.getOptionValue("x")) : -180.0;
        double upperLongitude = cmdLine.hasOption("X") ? Double.parseDouble(cmdLine.getOptionValue("X")) : 180.0;
        CircularRange longitudeRange = new CircularRange("Longitude", lowerLongitude, upperLongitude, -180.0, 360.0);

        double lowerLatitude = cmdLine.hasOption("y") ? Double.parseDouble(cmdLine.getOptionValue("y")) : -90.0;
        double upperLatitude = cmdLine.hasOption("Y") ? Double.parseDouble(cmdLine.getOptionValue("Y")) : 90.0;
        LinearRange latitudeRange = new LinearRange("Latitude", lowerLatitude, upperLatitude, -90.0, 90.0);

        double lowerDepth = cmdLine.hasOption("z") ? Double.parseDouble(cmdLine.getOptionValue("z")) : 100.0;
        double upperDepth = cmdLine.hasOption("Z") ? Double.parseDouble(cmdLine.getOptionValue("Z")) : 700.0;
        LinearRange depthRange = new LinearRange("Depth", lowerDepth, upperDepth);

        double lowerMw = cmdLine.hasOption("m") ? Double.parseDouble(cmdLine.getOptionValue("m")) : 5.5;
        double upperMw = cmdLine.hasOption("M") ? Double.parseDouble(cmdLine.getOptionValue("M")) : 7.31;
        LinearRange mwRange = new LinearRange("Magnitude", lowerMw, upperMw);

        // search events
        GlobalCMTSearch search = new GlobalCMTSearch(startDate, endDate);
        search.setMwRange(mwRange);
        search.setDepthRange(depthRange);
        search.setLatitudeRange(latitudeRange);
        search.setLongitudeRange(longitudeRange);
        Set<GlobalCMTID> eventSet = search.search();

        // output
        if (!DatasetAid.checkNum(eventSet.size(), "event", "events")) {
            return;
        }
        if (cmdLine.hasOption("f")) EventListFile.writeFullInfo(eventSet, outputPath);
        else EventListFile.write(eventSet, outputPath);
    }

}
