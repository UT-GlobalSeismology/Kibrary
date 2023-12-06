package io.github.kensuke1984.kibrary.visual.map;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.InformationFileReader;

/**
 * File to specify the colors to use in visualizations.
 * <p>
 * Odd lines: (int) value of limit of interval.
 * Even lines: name of color.
 * <p>
 *
 * @author otsuru
 * @since 2022/6/24
 */
public class ColorBinInformationFile {

    private int nSections;
    private int[] values;
    private String[] colors;

    /**
     * Writes a color bin information file.
     * @param values
     * @param colors
     * @param outPath
     * @param options
     * @throws IOException
     */
    public static void write(int[] values, String[] colors, Path outPath, OpenOption... options) throws IOException {
        if (values.length - 1 != colors.length) throw new IllegalArgumentException("#colors must be #values - 1");

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, options))) {
            for (int i = 0; i < values.length; i++) {
                pw.println(values[i]);
                if (i == values.length - 1) break;
                pw.println(colors[i]);
            }
        }
    }

    public ColorBinInformationFile(Path filePath) throws IOException {
        InformationFileReader reader = new InformationFileReader(filePath, true);

        int nLines = reader.getNumLines();
        if (nLines % 2 == 0) throw new IllegalStateException("The file should have odd number of lines");
        nSections = (nLines - 1) / 2;
        values = new int[nSections + 1];
        colors = new String[nSections];

        for (int i = 0 ; i < nSections + 1; i++) {
            values[i] = Integer.parseInt(reader.next());
            if (i == nSections) break;
            colors[i] = reader.next();
        }
    }

    public int getNSections() {
        return nSections;
    }

    public int getStartValueFor(int i) {
        return values[i];
    }

    public String getColorFor(int i) {
        return colors[i];
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Creates a template color bin information file.
     * @param args Options.
     * @throws IOException if an I/O error occurs
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

        // input
        OptionGroup inputOption = new OptionGroup();
        inputOption.addOption(Option.builder("d").longOpt("distance")
                .desc("Bin by epicentral distance").build());
        inputOption.addOption(Option.builder("a").longOpt("azimuth")
                .desc("Bin by azimuth").build());
        inputOption.setRequired(true);
        options.addOptionGroup(inputOption);

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
                : Paths.get("colorBin" + GadgetAid.getTemporaryString() + ".inf");

        int values[];
        String colors[];
        if (cmdLine.hasOption("d")) {
            int values0[] = {70, 80, 90, 100};
            values = values0;
            String colors0[] = {"green", "blue", "purple"};
            colors = colors0;
        } else if (cmdLine.hasOption("a")) {
            int values0[] = {0, 45, 90, 135, 180, 225, 270, 315, 360};
            values = values0;
            String colors0[] = {"darkorange", "green", "blue", "purple", "darkorange", "green", "blue", "purple"};
            colors = colors0;
        } else {
            throw new IllegalArgumentException();
        }

        write(values, colors, outputPath);
    }
}
