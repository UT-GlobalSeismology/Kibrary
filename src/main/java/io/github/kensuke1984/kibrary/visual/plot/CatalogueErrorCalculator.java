package io.github.kensuke1984.kibrary.visual.plot;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 *
 * @author rei
 * @since 2022/10/22
 */
public class CatalogueErrorCalculator extends Operation {

    private static final int MAX_PAIR = 10;

    private final Property property;
    /**
     * Path of the work folder.
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Whether to append date string at end of output folder name.
     */
    private boolean appendFolderDate;
    /**
     * Components to make maps for.
     */
    private Set<SACComponent> components;
    /**
     * Variable types to make maps for.
     */
    private Set<VariableType> variableTypes;
    /**
     * Events to work for. If this is empty, work for all events in workPath.
     */
    private Set<GlobalCMTID> tendEvents = new HashSet<>();
    /**
     * Observers to work for. If this is empty, work for all events in workPath.
     */
    private String tendObservers;

    /**
     * Voxel to work for. Each elements are 0: latitude, 1: longitude, 2: r.
     */
    private double[] voxelPosition = new double[3];

    /**
     * Path of partial data without epicentral distance catalog.
     */
    private Path exactPartialPath;

    /**
     * Theta range for the BP catalog.
     */
    private List<String> dthetas = new ArrayList<>();
    /**
     * Path of catalog partial data.
     */
    private List<Path> catPartialPaths = new ArrayList<>();

    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile();
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
            pw.println("##Path of a working directory. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##SacComponents to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##Variable types to be used, listed using spaces. (MU)");
            pw.println("#variableTypes ");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces. To use all events, leave this unset.");
            pw.println("#tendEvents ");
            pw.println("##Observers to work for, in the form STA_NET, listed using spaces. To use all observers, leave this unset.");
            pw.println("#tendObservers ");
            pw.println("#Voxel to work for, in the form LAT LONG R, listed using spaces, must be set.");
            pw.println("#tendVoxelPosition ");
            pw.println("##Path of an exact (without epicentral distance catalog) partial waveform folder, must be set.");
            pw.println("#exactPartialPath partial");
            pw.println("##########From here on, list up sets of theta range for the BP catalog and paths of catalog partial waveform folders.");
            pw.println("########## Up to " + MAX_PAIR + " pairs can be managed. Any pair may be left blank.");
            for (int i = 1; i <= MAX_PAIR; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " set.");
                 pw.println("#dtheta" + i + " ");
                 pw.println("#catPartialPath" + i + " partial");
            }
        }
        System.err.println(outPath + " is created.");
    }

    public CatalogueErrorCalculator(Property property) {
        this.property = property;
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());
        variableTypes = Arrays.stream(property.parseStringArray("variableTypes", "MU")).map(VariableType::valueOf)
                .collect(Collectors.toSet());

        tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                    .collect(Collectors.toSet());
        tendObservers = property.parseString("tendObservers", null);

        voxelPosition = property.parseDoubleArray("tendVoxelPosition", null);

        exactPartialPath = property.parsePath("exactPartialPath", null, true, workPath);

        for (int i = 1; i <= MAX_PAIR; i++) {
            String dthetaKey = "dtheta" + i;
            String partialKey = "catPartialPath" + i;
            if (!property.containsKey(partialKey) && !property.containsKey(dthetaKey)) {
                continue;
            } else if (!property.containsKey(partialKey) || !property.containsKey(dthetaKey)) {
                throw new IllegalArgumentException("Theta range and catalog partial waveform folder must be set in pairs.");
            }
            dthetas.add(property.parseString(dthetaKey, null));
            catPartialPaths.add(property.parsePath(partialKey, null, true, workPath));
        }
    }

    @Override
    public void run() throws IOException {
        Path outPath = DatasetAid.createOutputFolder(workPath, "relativeError", folderTag, appendFolderDate, GadgetAid.getTemporaryString());

        // check the number of catalogue partials
        int setNum = dthetas.size();
        if (catPartialPaths.size() != setNum) {
            throw new IllegalStateException("The number of theta ranges and catalog partial waveform folders are different.");
        }
        if (setNum == 0) {
            System.err.println("No input files found.");
            return;
        }

        // read partials
        List<PartialID> exactPartials = PartialIDFile.read(exactPartialPath, true);
        List<List<PartialID>> catPartials = new ArrayList<>();
        for (int i = 0; i < setNum; i++) {
            catPartials.add(PartialIDFile.read(catPartialPaths.get(i), true));
        }

        for (PartialID exactPartial : exactPartials) {
            if (!components.contains(exactPartial.getSacComponent())) continue;
            if (!variableTypes.contains(exactPartial.getVariableType())) continue;
            if (!tendEvents.contains(exactPartial.getGlobalCMTID())) continue;
            if (!tendObservers.contains(exactPartial.getObserver().toString())) continue;
            if (!Precision.equals(voxelPosition[0], exactPartial.getVoxelPosition().getLatitude(), FullPosition.LATITUDE_EPSILON)) continue;
            if (!Precision.equals(voxelPosition[1], exactPartial.getVoxelPosition().getLongitude(), FullPosition.LONGITUDE_EPSILON)) continue;
            if (!Precision.equals(voxelPosition[2], exactPartial.getVoxelPosition().getR(), FullPosition.RADIUS_EPSILON)) continue;

            final List<Double> relativeErrors = new ArrayList<>();

            // read cat partials
            for (int i = 0; i < setNum; i++) {
                for (PartialID catPartial : catPartials.get(i)) {
                    if (!components.contains(catPartial.getSacComponent())) continue;
                    if (!variableTypes.contains(catPartial.getVariableType())) continue;
                    if (!tendEvents.contains(catPartial.getGlobalCMTID())) continue;
                    if (!tendObservers.contains(catPartial.getObserver().toString())) continue;
                    if (exactPartial.getVoxelPosition().equals(catPartial.getVoxelPosition())) {
                        // compute relative error
                        double[] exactData = exactPartial.getData();
                        double[] catData = catPartial.getData();
                        if (exactData.length != catData.length) {
                            System.err.println("data lengths are differnent between two partials");
                            continue;
                        }
                        relativeErrors.add(calcError(exactData, catData));
                    }
                }
            }
            createPlot(outPath, exactPartial, relativeErrors);
        }
    }

    /**
     * calcurate relative error from evact partial waveform data and catalogue partial waveform data
     * @param exactData
     * @param catData
     * @return relativeError
     */
    private double calcError(double[] exactData, double[] catData) {
        double diffDataAbs = 0.;
        double exactDataAbs = 0.;

        for (int j = 0; j < exactData.length; j++) {
            double diffData = exactData[j] - catData[j];

            diffDataAbs += diffData * diffData;
            exactDataAbs += exactData[j] * exactData[j];
        }
        diffDataAbs = Math.sqrt(diffDataAbs);
        exactDataAbs = Math.sqrt(exactDataAbs);

        return diffDataAbs / exactDataAbs * 100.;
    }

    /**
     * Create plot file and data files listing relative errors for each theta range.
     * Output files are in observer directory in event directory in outPath
     * @param outPath
     * @param exactPartial
     * @param relativeErrors
     * @throws IOException
     */
    private void createPlot(Path outPath, PartialID exactPartial, List<Double> relativeErrors) throws IOException {
        if (dthetas.size() != relativeErrors.size())
            throw new RuntimeException("The number of theta range " + dthetas.size() + " and calcurated errors " + relativeErrors.size() + " are not equal");

        System.err.println("Creating plot file and data file");

        double minDTheta = Double.parseDouble(dthetas.get(0));
        double maxDTheta = Double.parseDouble(dthetas.get(0));
        for (int i = 1; i < dthetas.size(); i++) {
            double tmpDTheta = Double.parseDouble(dthetas.get(i));
            if (tmpDTheta < minDTheta) minDTheta = tmpDTheta;
            if (tmpDTheta > maxDTheta) maxDTheta = tmpDTheta;
        }

        Path eventPath = outPath.resolve(exactPartial.getGlobalCMTID().toString());
        Path observerPath = eventPath.resolve(exactPartial.getObserver().toString());
        Files.createDirectories(observerPath);

        // make data file
        Path filePath = observerPath.resolve("relativeError_" + exactPartial.getSacComponent() + "_" + exactPartial.getVariableType() + ".lst");
        if (!Files.exists(filePath))
            Files.createFile(filePath);
        PrintWriter pw = new PrintWriter(new FileWriter(filePath.toString(), true));
        for (int i = 0; i < dthetas.size(); i ++)
            pw.println(dthetas.get(i) + " " + relativeErrors.get(i));
        pw.close();

        // make plot file
        Path plotPath = observerPath.resolve("plot_" + exactPartial.getSacComponent() + "_" + exactPartial.getVariableType() + ".plt");
//      GnuplotFile gnuplot = new GnuplotFile(observerPath.resolve("plot_" + fileNameRoot + ".plt"));
//      gnuplot.setOutput("pdf", "relativeError_" + fileNameRoot + ".pdf", 21, 29.7, true);
//      gnuplot.setXlabel("d{/Symbol q}");
//      gnuplot.setYlabel("#Error(%)");
//      gnuplot.setXrange(minDTheta, maxDTheta * 2.);
//      gnuplot.write();
//      if (!gnuplot.execute()) System.err.println("gnuplot failed!!");
        PrintWriter pw2 = new PrintWriter(new FileWriter(plotPath.toString(), true));
        pw2.println("set term pngcairo enhanced font 'Helvetica,14'");
        pw2.println("set xlabel 'd{/Symbol q}'");
        pw2.println("set ylabel '#Error(%)'");
        pw2.println("set logscale");
        pw2.println("set xrange [" + minDTheta + ":" + maxDTheta * 2. + "]");
        pw2.println("set xtics nomirror");
        pw2.println("set ytics nomirror");
        pw2.println("set sample 11");
        pw2.println("set output 'relativeError.png'");
        for (int i = 0; i < dthetas.size(); i++) {
             pw2.println("set label " + (i + 1) + " point pt 2 ps 2 lc rgb \"red\" at " + dthetas.get(i) + "," + relativeErrors.get(i));
        }
        pw2.println("p [" + minDTheta + ":" + maxDTheta * 2. + "][0.001:1000] 1/0 notitle");
        pw2.close();
        System.err.println("After finish working, please run " + plotPath.toString());
    }
}
