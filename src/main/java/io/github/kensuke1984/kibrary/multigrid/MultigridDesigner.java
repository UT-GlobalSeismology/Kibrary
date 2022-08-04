package io.github.kensuke1984.kibrary.multigrid;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.RealMatrix;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.inversion.addons.WeightingType;
import io.github.kensuke1984.kibrary.inversion.setup.MatrixAssembly;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Computes correlation between partial waveforms of each unknown parameter, and creates a multigrid design.
 *
 * TODO when A~B and B~C (A~C may or may not be true)
 *
 * @author otsuru
 * @since 2022/8/1
 */
public class MultigridDesigner extends Operation {

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
     * path of basic ID file
     */
    private Path basicIDPath;
    /**
     * path of waveform data
     */
    private Path basicPath;
    /**
     * path of partial ID file
     */
    private Path partialIDPath;
    /**
     * path of partial data
     */
    private Path partialPath;
    /**
     * Path of unknown parameter file
     */
    private Path unknownParameterPath;
    /**
     * Partial types of parameters to be fused
     */
    private List<PartialType> partialTypes;

    private WeightingType weightingType;

    private double minDiagonalAmplitude;
    private double minCorrelation;

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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this blank.");
            pw.println("#tag ");
            pw.println("##Path of a basic ID file, must be set");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##Path of a basic waveform file, must be set");
            pw.println("#basicPath actual.dat");
            pw.println("##Path of a partial ID file, must be set");
            pw.println("#partialIDPath partialID.dat");
            pw.println("##Path of a partial waveform file, must be set");
            pw.println("#partialPath partial.dat");
            pw.println("##Path of an unknown parameter list file, must be set");
            pw.println("#unknownParameterPath unknowns.lst");
            pw.println("##Partial types of parameters to fuse. If not set, all partial types will be used.");
            pw.println("#partialTypes ");
            pw.println("##Weighting type, from {LOWERUPPERMANTLE,RECIPROCAL,TAKEUCHIKOBAYASHI,IDENTITY,FINAL} (RECIPROCAL)");
            pw.println("#weightingType ");
            pw.println("##(double) minDiagonalAmplitude");
            pw.println("#minDiagonalAmplitude ");
            pw.println("##(double) minCorrelation (0.75)");
            pw.println("#minCorrelation ");
        }
        System.err.println(outPath + " is created.");
    }

    public MultigridDesigner(Property property) throws IOException {
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

        if (property.containsKey("partialTypes"))
            partialTypes = Arrays.stream(property.parseStringArray("partialTypes", null)).map(PartialType::valueOf)
                    .collect(Collectors.toList());
        weightingType = WeightingType.valueOf(property.parseString("weightingType", "RECIPROCAL"));
        minDiagonalAmplitude = property.parseDouble("minDiagonalAmplitude", "5");
        minCorrelation = property.parseDouble("minCorrelation", "0.75");
    }

    @Override
    public void run() throws IOException {
        String dateStr = GadgetAid.getTemporaryString();

        // read input
        BasicID[] basicIDs = BasicIDFile.read(basicIDPath, basicPath);
        PartialID[] partialIDs = PartialIDFile.read(partialIDPath, partialPath);
        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownParameterPath);

        // assemble matrices
        MatrixAssembly assembler = new MatrixAssembly(basicIDs, partialIDs, parameterList, weightingType);
        RealMatrix ata = assembler.getAta();

        // output unknown parameter with large diagonal component and correlation
        Path logPath = workPath.resolve("multigridDesigner" + dateStr + ".log");
        MultigridDesign multigrid = new MultigridDesign();
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(logPath))) {
            GadgetAid.dualPrintln(pw, "# i j AtA(i,i) AtA(i,j) coeff");

            for (int i = 0; i < parameterList.size(); i++) {
                if (partialTypes != null && partialTypes.contains(parameterList.get(i).getPartialType()) == false)
                    continue;

                for (int j = 0; j < parameterList.size(); j++) {
                    if (i == j) continue;
                    if (partialTypes != null && partialTypes.contains(parameterList.get(j).getPartialType()) == false)
                        continue;

                    double coeff = ata.getEntry(i, j) * ata.getEntry(i, j) / ata.getEntry(i, i) / ata.getEntry(j, j);
                    if (ata.getEntry(i, i) > minDiagonalAmplitude && coeff > minCorrelation) {
                        GadgetAid.dualPrintln(pw, i + " " + j + " " + ata.getEntry(i, i) + " " + ata.getEntry(i, j) + " " + coeff);
                        GadgetAid.dualPrintln(pw, " - " + parameterList.get(i));
                        GadgetAid.dualPrintln(pw, " - " + parameterList.get(j));
                        multigrid.addFusion(parameterList.get(i), parameterList.get(j));
                    }
                }
            }
        }

        // output multigrid design file
        Path outputMultigridPath = workPath.resolve(DatasetAid.generateOutputFileName("multigrid", tag, dateStr, ".inf"));
        MultigridInformationFile.write(multigrid, outputMultigridPath);

        // output unknown parameter file
        List<UnknownParameter> fusedParameterList = parameterList.stream()
                .filter(param -> !multigrid.fuses(param)).collect(Collectors.toList());
        fusedParameterList.addAll(multigrid.getFusedParameters());
        Path outputUnknownsPath = workPath.resolve(DatasetAid.generateOutputFileName("unknowns", tag, dateStr, ".lst"));
        UnknownParameterFile.write(fusedParameterList, outputUnknownsPath);
    }

}
