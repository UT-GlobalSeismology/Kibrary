package io.github.kensuke1984.kibrary.waveform;

import java.io.File;
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
import java.util.Objects;
import java.util.Set;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.filter.BandPassFilter;
import io.github.kensuke1984.kibrary.filter.ButterworthFilter;
import io.github.kensuke1984.kibrary.inversion.Dvector;
import io.github.kensuke1984.kibrary.inversion.ObservationEquation;
import io.github.kensuke1984.kibrary.inversion.addons.RandomNoiseMaker;
import io.github.kensuke1984.kibrary.inversion.addons.WeightingType;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * Checkerboard test
 * <p>
 * Creates born-waveforms for checkerboard tests
 *
 * @author Kensuke Konishi
 * @since version 0.2.2
 * @version 2022/2/23 Moved & renamed from kibrary.inversion.CheckerBoardTest
 */
public class PseudoWaveformGenerator extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;

    /**
     * Path of a {@link BasicIDFile} file (id part)
     */
    private Path basicIDPath;
    /**
     * Path of a {@link BasicIDFile} file (data part)
     */
    private Path basicPath;
    /**
     * Path of the partialID
     */
    private Path partialIDPath;
    /**
     * Path of the partial data
     */
    private Path partialPath;
    /**
     * Path for the file ({@link UnknownParameterFile})
     */
    private Path unknownParameterPath;
    /**
     * Path of a model file containing psudoM
     */
    private Path modelPath;

    private boolean iterate;
    private boolean noise;
    private double noisePower;

    private ObservationEquation eq;
    private Set<GlobalCMTID> eventSet = new HashSet<>();
    private Set<Observer> observerSet = new HashSet<>();
    private double[][] ranges;
    private Phase[] phases;

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
            pw.println("##Path of a working folder (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##Path of a basic ID file, must be defined");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##Path of a basic waveform file, must be defined");
            pw.println("#basicPath actual.dat");
            pw.println("##Path of a partial ID file, must be defined");
            pw.println("#partialIDPath partialID.dat");
            pw.println("##Path of a partial waveform file, must be defined");
            pw.println("#partialPath partial.dat");
            pw.println("##Path of an unknown parameter list file, must be defined");
            pw.println("#unknownParameterPath unknowns.lst");
            pw.println("##Path of a model file, must be defined");
            pw.println("#modelPath model.lst");
            pw.println("##(boolean) Whether this is for Iterate (false)");
            pw.println("#iterate ");
            pw.println("##(boolean) Whether to add noise (false)");
            pw.println("#noise ");
            pw.println("##(double) Noise power [ ] (1000)"); // TODO what is the unit?
            pw.println("#noisePower ");
        }
        System.err.println(outPath + " is created.");
    }

    public PseudoWaveformGenerator(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);

        basicIDPath = property.parsePath("basicIDPath", null, true, workPath);
        basicPath = property.parsePath("basicPath", null, true, workPath);
        partialIDPath = property.parsePath("partialIDPath", null, true, workPath);
        partialPath = property.parsePath("partialPath", null, true, workPath);
        unknownParameterPath = property.parsePath("unknownParameterPath", null, true, workPath);
        modelPath = property.parsePath("modelPath", null, true, workPath);

        iterate = property.parseBoolean("iterate", "false");
        noise = property.parseBoolean("noise", "false");
        if (noise) {
            noisePower = property.parseDouble("noisePower", "1000");
        }
    }
/*
    private void checkAndPutDefaults() {
        if (!property.containsKey("workPath")) property.setProperty("workPath", "");
        if (!property.containsKey("components")) property.setProperty("components", "Z R T");
        if (!property.containsKey("timePartial")) property.setProperty("timePartial", "false");
        if (!property.containsKey("modelName")) property.setProperty("modelName", "");
        if (!property.containsKey("noise")) property.setProperty("noise", "false");
        if (property.getProperty("noise").equals("true") && !property.containsKey("noisePower"))
            throw new RuntimeException("There is no information about 'noisePower'");
        if (!property.containsKey("iterate")) property.setProperty("iterate", "false");
    }

    private void set() {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        if (!Files.exists(workPath))
            throw new RuntimeException("The workPath: " + workPath + " does not exist");
        waveIDPath = getPath("waveIDPath");
        waveformPath = getPath("waveformPath");
        partialIDPath = getPath("partialIDPath");
        partialWaveformPath = getPath("partialWaveformPath");
        unknownParameterListPath = getPath("unknownParameterListPath");
        inputDataPath = getPath("inputDataPath");
        noise = Boolean.parseBoolean(property.getProperty("noise"));
        if (noise)
            noisePower = Double.parseDouble(property.getProperty("noisePower"));
        iterate = Boolean.parseBoolean(property.getProperty("iterate"));
    }
*/
    @Override
    public void run() throws IOException {
        read();
        readIDs();

        //compute
        RealVector pseudoM = readPseudoM();
        RealVector pseudoD = computePseudoD(pseudoM);
        RealVector bornVec = pseudoD.add(getSynVector());

        String dateStr = GadgetAid.getTemporaryString();
        Path pseudoIDPath = workPath.resolve(DatasetAid.generateOutputFileName("pseudoID", tag, dateStr, ".dat"));
        Path pseudoPath = workPath.resolve(DatasetAid.generateOutputFileName("pseudo", tag, dateStr, ".dat"));
        System.err.println("Outputting in " + pseudoIDPath + " , " + pseudoPath);

        if (noise) bornVec = bornVec.add(computeRandomNoise());
        if (iterate) output4Iterate(pseudoIDPath, pseudoPath, bornVec);
        else output4ChekeBoardTest(pseudoIDPath, pseudoPath, bornVec);
    }

    private void read() throws IOException {
        BasicID[] ids = BasicIDFile.read(basicIDPath, basicPath);
        Dvector dVector = new Dvector(ids, id -> true, WeightingType.RECIPROCAL);
        PartialID[] pids = PartialIDFile.read(partialIDPath, partialPath);
        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownParameterPath);
        eq = new ObservationEquation(pids, parameterList, dVector, false, false, null, null, null, null);
    }

    private void readIDs() {
        List<double[]> ranges = new ArrayList<>();
        Set<Phase> tmpPhases = new HashSet<>();
        for (BasicID id : eq.getDVector().getObsIDs()) {
            observerSet.add(id.getObserver());
            eventSet.add(id.getGlobalCMTID());
            for (Phase phase : id.getPhases())
                tmpPhases.add(phase);
            double[] range = new double[] { id.getMinPeriod(), id.getMaxPeriod() };
            if (ranges.size() == 0)
                ranges.add(range);
            boolean exists = false;
            for (int i = 0; !exists && i < ranges.size(); i++)
                if (Arrays.equals(range, ranges.get(i)))
                    exists = true;
            if (!exists)
                ranges.add(range);
        }
        this.ranges = ranges.toArray(new double[0][]);
        phases = new Phase[tmpPhases.size()];
        phases = tmpPhases.toArray(phases);
    }

    /**
     * 読み込んだデータセットに対してボルン波形を観測波形として 理論波形を理論波形として書き込む（上書きではない）
     *
     * @param outIDPath   for write
     * @param outDataPath for write
     * @param bornVec     for write
     * @throws IOException if any
     */
    public void output4ChekeBoardTest(Path outIDPath, Path outDataPath, RealVector bornVec) throws IOException {
        // bornVec = dVector.getObsVec();
        Objects.requireNonNull(bornVec);

        Dvector dVector = eq.getDVector();
        RealVector[] bornPart = dVector.separate(bornVec);
        System.err.println("outputting " + outIDPath + " " + outDataPath);
        try (WaveformDataWriter bdw = new WaveformDataWriter(outIDPath, outDataPath, observerSet, eventSet, ranges, phases)) {
            BasicID[] obsIDs = dVector.getObsIDs();
            BasicID[] synIDs = dVector.getSynIDs();
            for (int i = 0; i < dVector.getNTimeWindow(); i++) {
                bdw.addBasicID(obsIDs[i].withData(bornPart[i].mapDivide(dVector.getWeighting(i)).toArray()));
                bdw.addBasicID(synIDs[i].withData(dVector.getSynVec()[i].mapDivide(dVector.getWeighting(i)).toArray()));
            }
        }
    }

    /**
     * 読み込んだデータセットに対してボルン波形を理論波形として書き込む（上書きではない）
     *
     * @param outIDPath   {@link File} for write ID file
     * @param outDataPath {@link File} for write data file
     * @param bornVec     {@link RealVector} of born
     * @throws IOException if any
     */
    public void output4Iterate(Path outIDPath, Path outDataPath, RealVector bornVec) throws IOException {
        if (bornVec == null) {
            System.err.println("bornVec is not set");
            return;
        }
        Dvector dVector = eq.getDVector();
        RealVector[] bornPart = dVector.separate(bornVec);
        System.err.println("outputting " + outIDPath + " " + outDataPath);
        try (WaveformDataWriter bdw = new WaveformDataWriter(outIDPath, outDataPath, observerSet, eventSet, ranges, phases)) {
            BasicID[] obsIDs = dVector.getObsIDs();
            BasicID[] synIDs = dVector.getSynIDs();
            for (int i = 0; i < dVector.getNTimeWindow(); i++) {
                double weighting = dVector.getWeighting(i);
                bdw.addBasicID(obsIDs[i].withData(dVector.getObsVec()[i].mapDivide(weighting).toArray()));
                bdw.addBasicID(synIDs[i].withData(bornPart[i].mapDivide(weighting).toArray()));
            }
        }
    }

    /**
     * Reads pseudoM
     */
    private RealVector readPseudoM() throws IOException {
        List<String> lines = Files.readAllLines(modelPath);
        if (lines.size() != eq.getMlength())
            throw new RuntimeException("input model length is wrong");
        double[] pseudoM = lines.stream().mapToDouble(Double::parseDouble).toArray();
        return new ArrayRealVector(pseudoM, false);
    }

    /**
     * d = A m
     *
     * @param pseudoM &delta;m
     * @return d for the input pseudo M
     */
    public RealVector computePseudoD(RealVector pseudoM) {
        return eq.operate(pseudoM);
    }

    public RealVector getSynVector() {
        return eq.getDVector().getSyn();
    }

    public RealVector computeRandomNoise() {
        Dvector dVector = eq.getDVector();
        RealVector[] noiseV = new RealVector[dVector.getNTimeWindow()];
        int[] pts = dVector.getLengths();
        double minFreq = 0.05;
        double maxFreq = 0.01;
        int np = 6;
        ButterworthFilter bpf = new BandPassFilter(2 * Math.PI * 0.05 * minFreq, 2 * Math.PI * 0.05 * maxFreq, np);
        for (int i = 0; i < dVector.getNTimeWindow(); i++) {
            // System.out.println(i);
            double[] u = RandomNoiseMaker.create(noisePower, 20, 3276.8, 512).getY();
            u = bpf.applyFilter(u);
            int startT = (int) dVector.getObsIDs()[i].getStartTime() * 20; // 6*4=20
            noiseV[i] = new ArrayRealVector(pts[i]);
//			System.out.println(new ArrayRealVector(u).getLInfNorm());
            for (int j = 0; j < pts[i]; j++)
                noiseV[i].setEntry(j, u[j * 20 + startT]);
        }
        // pseudoD = pseudoD.add(randomD);
        return dVector.combine(noiseV);
    }

}
