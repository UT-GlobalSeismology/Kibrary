package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;

/**
 * Operation for merging pairs of partial ID files and partial waveform files.
 *
 * @author otsuru
 * @since 2022/2/12 Modified the original PartialIDMerge which was in kibrary.waveform.addons.
 */
public class PartialIDMerge extends Operation {

    private static final int MAX_PAIR = 10;

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    private String nameRoot;

    private List<Path> partialIDPaths = new ArrayList<>();
    private List<Path> partialPaths = new ArrayList<>();


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
            pw.println("##Path of a work folder (.)");
            pw.println("#workPath ");
            pw.println("##(String) The first part of the name of output partial ID and waveform files (partial)");
            pw.println("#nameRoot ");
            pw.println("##########From here on, list up pairs of the paths of a partial ID file and a partial waveform file.");
            pw.println("########## Up to " + MAX_PAIR + " pairs can be managed. Any pair may be left blank.");
            for (int i = 1; i <= MAX_PAIR; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " pair");
                pw.println("#partialIDPath" + i + " partialID" + i + ".dat");
                pw.println("#partialPath" + i + " partial" + i + ".dat");
            }
        }
        System.err.println(outPath + " is created.");
    }

    public PartialIDMerge(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));

        nameRoot = property.parseStringSingle("nameRoot", "partial");

        for (int i = 1; i <= MAX_PAIR; i++) {
            String partialIDKey = "partialIDPath" + i;
            String partialKey = "partialPath" + i;
            if (!property.containsKey(partialIDKey) && !property.containsKey(partialKey)) {
                continue;
            } else if (!property.containsKey(partialIDKey) || !property.containsKey(partialKey)) {
                throw new IllegalArgumentException("Partial ID file and partial waveform file must be set in pairs.");
            }
            partialIDPaths.add(property.parsePath(partialIDKey, null, true, workPath));
            partialPaths.add(property.parsePath(partialKey, null, true, workPath));
        }
    }


    @Override
    public void run() throws IOException {
        int pairNum = partialIDPaths.size();
        if (partialPaths.size() != pairNum) {
            throw new IllegalStateException("The number of partial ID files and partial waveform files is different.");
        }
        if (pairNum == 0) {
            System.err.println("No input files found.");
            return;
        } else if (pairNum == 1) {
            System.err.println("Only 1 pair of input files found. Merging will not be done.");
            return;
        }

        // read PartialIDs from all input files
        Set<PartialID> partialIDs = new HashSet<>();
        for (int i = 0; i < pairNum; i++) {
            PartialID[] srcIDs = PartialIDFile.read(partialIDPaths.get(i), partialPaths.get(i));
            Stream.of(srcIDs).forEach(id -> partialIDs.add(id));
        }

        // extract set of voxels, observers, events, periods, and phases
        Set<FullPosition> voxelSet = new HashSet<>();
        Set<Observer> observerSet = new HashSet<>();
        Set<GlobalCMTID> eventSet = new HashSet<>();
        Set<double[]> periodSet = new HashSet<>();
        Set<Phase> phaseSet = new HashSet<>();

        partialIDs.forEach(id -> {
            observerSet.add(id.getObserver());
            eventSet.add(id.getGlobalCMTID());
            voxelSet.add(id.getPerturbationLocation());
            boolean add = true;
            for (double[] periods : periodSet) {
                if (id.getMinPeriod() == periods[0] && id.getMaxPeriod() == periods[1])
                    add = false;
            }
            if (add)
                periodSet.add(new double[] {id.getMinPeriod(), id.getMaxPeriod()});
            for (Phase phase : id.getPhases())
                phaseSet.add(phase);
        });

        double[][] periodRanges = new double[periodSet.size()][];
        int j = 0;
        for (double[] periods : periodSet)
            periodRanges[j++] = periods;
        Phase[] phases = phaseSet.toArray(new Phase[phaseSet.size()]);

        String dateStr = GadgetAid.getTemporaryString();
        Path outputIDPath = workPath.resolve(nameRoot + "ID" + dateStr + ".dat");
        Path outputWavePath = workPath.resolve(nameRoot + dateStr + ".dat");

        System.err.println("Outputting in " + outputIDPath + " and " + outputWavePath);
        try (WaveformDataWriter wdw = new WaveformDataWriter(outputIDPath, outputWavePath, observerSet, eventSet, periodRanges, phases, voxelSet)) {
            partialIDs.forEach(id -> {
                try {
                    if (! id.getWaveformType().equals(WaveformType.PARTIAL)) {
                        System.out.println(id.toString() + "is not a partial, it is a " + id.getWaveformType().toString());
                        throw new RuntimeException();
                    }
                    wdw.addPartialID(id);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }

    }
}
