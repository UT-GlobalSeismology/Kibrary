package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.inversion.WeightingHandler;
import io.github.kensuke1984.kibrary.inversion.setup.DVectorBuilder;
import io.github.kensuke1984.kibrary.inversion.setup.MatrixAssembly;
import io.github.kensuke1984.kibrary.math.ParallelizedMatrix;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.KnownParameterFile;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

/**
 * Operation to create born-waveforms for a 3D model.
 * To be used for creating pseudo-waveforms for checkerboard tests, etc.
 * <p>
 * The created pseudo-waveform can be set as either the observed or synthetic waveform in the output {@link BasicIDFile},
 * depending on the setting.
 * <p>
 * White noise can be added to the waveform.
 *
 * @author Kensuke Konishi
 * @since version 0.2.2
 * @version 2022/2/23 Moved & renamed from inversion.CheckerBoardTest to waveform.PseudoWaveformGenerator
 */
public class PseudoWaveformGenerator extends Operation {

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Path of the output folder
     */
    private Path outPath;

    /**
     * basic waveform folder
     */
    private Path basicPath;
    /**
     * partial waveform folder
     */
    private Path partialPath;
    /**
     * Path of a {@link KnownParameterFile} file containing psudoM
     */
    private Path modelPath;

    /**
     * Which to set the psuedo waveform as. true: synthetic, false: observed
     */
    private boolean setPseudoAsSyn;
    private boolean noise;
    private String noiseType;
    private double noisePower;
    private double snRatio;
    /**
     * Fill 0 to empty partial waveforms or not.
     */
    private boolean fillEmptyPartial;
    /**
     * Sampling Hz of sac
     */
    private double sacSamplingHz = 20;

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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##Path of a basic waveform folder, must be set");
            pw.println("#basicPath actual");
            pw.println("##Path of a partial waveform folder, must be set");
            pw.println("#partialPath partial");
            pw.println("##Path of a model file, must be set");
            pw.println("#modelPath model.lst");
            pw.println("##(boolean) Whether to set the psuedo waveform as synthetic. If false, observed. (false)");
            pw.println("#setPseudoAsSyn ");
            pw.println("##(boolean) Whether to add noise (false)");
            pw.println("#noise ");
            pw.println("##(String) A type of noise to add [white, gaussian] (white)");
            pw.println("#noiseType ");
            pw.println("##(double) Noise power [ ] (1000)"); // TODO what is the unit?
            pw.println("#noisePower ");
            pw.println("##(double) S/N ratio (2)");
            pw.println("#snRatio ");
            pw.println("##(boolean) Fill 0 to empty partial waveforms (false)");
            pw.println("#fillEmptyPartial ");
        }
        System.err.println(outPath + " is created.");
    }

    public PseudoWaveformGenerator(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);

        basicPath = property.parsePath("basicPath", null, true, workPath);
        partialPath = property.parsePath("partialPath", null, true, workPath);
        modelPath = property.parsePath("modelPath", null, true, workPath);

        setPseudoAsSyn = property.parseBoolean("setPseudoAsSyn", "false");
        noise = property.parseBoolean("noise", "false");
        if (noise) {
            snRatio = property.parseDouble("snRatio", "2");
            //noisePower = property.parseDouble("noisePower", "1000");
            noiseType = property.parseString("noiseType", "white");
        }
        fillEmptyPartial = property.parseBoolean("fillEmptyPartial", "false");
    }

    @Override
    public void run() throws IOException {

        // read input
        List<KnownParameter> knowns = KnownParameterFile.read(modelPath);
        List<UnknownParameter> params = KnownParameter.extractParameterList(knowns);
        List<BasicID> basicIDs = BasicIDFile.read(basicPath, true);
        List<PartialID> partialIDs = PartialIDFile.read(partialPath, true);

        // assemble matrices (they should not be weighted)
        MatrixAssembly assembler = new MatrixAssembly(basicIDs, partialIDs, params, WeightingHandler.IDENTITY, fillEmptyPartial);
        ParallelizedMatrix a = assembler.getA();
        RealVector m = new ArrayRealVector(KnownParameter.extractValueArray(knowns), false);

        // compute pseudo waveform
        RealVector pseudoD = a.operate(m);
        DVectorBuilder dVectorBuilder = assembler.getDVectorBuilder();
        RealVector pseudoWaveform = dVectorBuilder.fullSynVec().add(pseudoD);

        // add noise
        //if (noise) pseudoWaveform = pseudoWaveform.add(createRandomNoise(dVectorBuilder));
        if (noise) {
            RealVector noiseV = createRandomNoise(dVectorBuilder);
            // debug
            pseudoWaveform = pseudoWaveform.add(noiseV);
            for (int i = 0; i< dVectorBuilder.getNTimeWindow(); i++) {
                int start = dVectorBuilder.getStartPoint(i);
                int npts = (i == dVectorBuilder.getNTimeWindow()-1) ? dVectorBuilder.getNpts() - start
                        : dVectorBuilder.getStartPoint(i+1) - start;
                RealVector p = pseudoWaveform.getSubVector(start, npts);
                RealVector n = noiseV.getSubVector(start, npts);
                double signal = p.getNorm() / p.getDimension();
                double noise = n.getNorm() / n.getDimension();
                System.err.println("S/N ratio of " + i + "th timewiondow is" + signal / noise);
             // debug
            }
        }

        // prepare output folder
        outPath = DatasetAid.createOutputFolder(workPath, "pseudo", folderTag, GadgetAid.getTemporaryString());
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output
        output(pseudoWaveform, dVectorBuilder);
    }

    private void output(RealVector pseudoVec, DVectorBuilder dVectorBuilder) throws IOException {
        RealVector[] pseudoObsParts = dVectorBuilder.decompose(pseudoVec);

        List<BasicID> basicIDs = new ArrayList<>();
        for (int i = 0; i < dVectorBuilder.getNTimeWindow(); i++) {
            BasicID obsID = dVectorBuilder.getObsID(i);
            BasicID synID = dVectorBuilder.getSynID(i);
            if (setPseudoAsSyn) {
                // replace synthetic data with pseudo waveform
                basicIDs.add(obsID);
                basicIDs.add(synID.withData(pseudoObsParts[i].toArray()));
            } else {
                // replace observed data with pseudo waveform
                basicIDs.add(obsID.withData(pseudoObsParts[i].toArray()));
                basicIDs.add(synID);
            }
        }

        BasicIDFile.write(basicIDs, outPath);
    }

    private RealVector createRandomNoise(DVectorBuilder dVectorBuilder) {
        System.err.println("Adding noise of amplitude " + noisePower);
        RealVector[] noiseV = new RealVector[dVectorBuilder.getNTimeWindow()];

        for (int i = 0; i < dVectorBuilder.getNTimeWindow(); i++) {
            BasicID obsID = dVectorBuilder.getObsID(i);
            noiseV[i] = RandomNoiseMaker.create(snRatio, dVectorBuilder.getObsVec(i), obsID.getStartTime(),
                    obsID.getMaxPeriod(), obsID.getMinPeriod(), sacSamplingHz, obsID.getSamplingHz(), noiseType).getYVector();
        }
//        // settings ; TODO: enable these values to be set
//        int[] pts = dVectorBuilder.nptsArray();
//        int sacSamplingHz = 20;
//        int finalSamplingHz = 1;
//        double delta = 1.0 / sacSamplingHz;
//        int step = (int) (sacSamplingHz / finalSamplingHz);
//        double maxFreq = 0.05;
//        double minFreq = 0.01;
//        int np = 6;
//
//        System.err.println("FYI, L2 norm of residual waveform: " + dVectorBuilder.fullObsVec().subtract(dVectorBuilder.fullSynVec()).getNorm());
//
//        ButterworthFilter bpf = new BandPassFilter(2 * Math.PI * delta * maxFreq, 2 * Math.PI * delta * minFreq, np);
//        for (int i = 0; i < dVectorBuilder.getNTimeWindow(); i++) {
//            double[] u = RandomNoiseMaker.create(noisePower, sacSamplingHz, 3276.8, 512).getY();
//            u = bpf.applyFilter(u);
//            int startT = (int) dVectorBuilder.getObsID(i).getStartTime() * sacSamplingHz;
//            noiseV[i] = new ArrayRealVector(pts[i]);
//            for (int j = 0; j < pts[i]; j++)
//                noiseV[i].setEntry(j, u[j * step + startT]);
//        }

        return dVectorBuilder.compose(noiseV);
    }

}
