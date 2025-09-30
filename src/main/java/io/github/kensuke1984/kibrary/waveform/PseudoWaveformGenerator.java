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
    private boolean addNoise;
    private String noiseType;
    private double noiseAmp;
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
            pw.println("#addNoise ");
            pw.println("##(String) A type of noise to add [white, gaussian] (white)");
            pw.println("#noiseType ");
            pw.println("##(double) S/N ratio. If not set, the following noiseAmp will be used.");
            pw.println("#snRatio ");
            pw.println("##(double) The amplitude of noise (1)");
            pw.println("#noiseAmp ");
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
        addNoise = property.parseBoolean("addNoise", "false");
        if (addNoise) {
            if (property.containsKey("snRatio"))
                snRatio = property.parseDouble("snRatio", null);
            else {
                snRatio = Double.NaN;
                noiseAmp = property.parseDouble("noiseAmp", "1");
            }
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
        if (addNoise) {
            RealVector noiseV = createRandomNoise(dVectorBuilder, pseudoWaveform);
            pseudoWaveform = pseudoWaveform.add(noiseV);
//            // check whether the noise value is correct
//            for (int i = 0; i < dVectorBuilder.getNTimeWindow(); i++) {
//                int start = dVectorBuilder.getStartPoint(i);
//                int npts = dVectorBuilder.getSynID(i).getNpts();
//                RealVector p = pseudoWaveform.getSubVector(start, npts);
//                RealVector n = noiseV.getSubVector(start, npts);
//                double signal = p.getNorm() / p.getDimension();
//                double noise = n.getNorm() / n.getDimension();
//                System.err.println("S/N ratio of " + i + "th timewiondow is " + signal / noise);
//            }
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

    private RealVector createRandomNoise(DVectorBuilder dVectorBuilder, RealVector pseudoWaveform) {
        if (Double.isNaN(snRatio))
            System.err.println("Adding noise of amplitude " + noiseAmp);
        else
            System.err.println("Adding noise of S/N ratio " + snRatio);
        RealVector[] noiseV = new RealVector[dVectorBuilder.getNTimeWindow()];

        for (int i = 0; i < dVectorBuilder.getNTimeWindow(); i++) {
            BasicID synID = dVectorBuilder.getSynID(i);
            int start = dVectorBuilder.getStartPoint(i);
            int npts = synID.getNpts();
            RealVector pseudo = pseudoWaveform.getSubVector(start, npts);
            noiseV[i] = RandomNoiseMaker.create(snRatio, noiseAmp, pseudo, synID.getStartTime(),
                    synID.getMaxPeriod(), synID.getMinPeriod(), sacSamplingHz, synID.getSamplingHz(), noiseType).getYVector();
        }
        return dVectorBuilder.compose(noiseV);
    }

}
