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
import org.apache.commons.math3.util.FastMath;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.inversion.addons.WeightingType;
import io.github.kensuke1984.kibrary.inversion.setup.AtAFile;
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
     * A tag to include in output folder name. When this is empty, no tag is used.
     */
    private String folderTag;
    /**
     * Path of the output folder
     */
    private Path outPath;

    /**
     * path of ata file
     */
    private Path ataPath;
    /**
     * path of basic waveform folder
     */
    private Path basicPath;
    /**
     * path of partial waveform folder
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

    private double minCorrelation;
    private double minAmpRatio;
    private double minDiagonalAmplitude;

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
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##########If this section is set, the next section is not neeeded.");
            pw.println("##Path of an AtA file");
            pw.println("#ataPath ata.lst");
            pw.println("##########If the previous section is set, this section is not neeeded.");
            pw.println("##Path of a basic waveform folder");
            pw.println("#basicPath actual");
            pw.println("##Path of a partial waveform folder");
            pw.println("#partialPath partial");
            pw.println("##Weighting type, from {LOWERUPPERMANTLE,RECIPROCAL,TAKEUCHIKOBAYASHI,IDENTITY,FINAL} (RECIPROCAL)");
            pw.println("#weightingType ");
            pw.println("##########Other settings.");
            pw.println("##Path of an unknown parameter list file, must be set and must match ata file if it is used");
            pw.println("#unknownParameterPath unknowns.lst");
            pw.println("##Partial types of parameters to fuse. If not set, all partial types will be used.");
            pw.println("#partialTypes ");
            pw.println("##(double) Minimum value of correlation for a pair of voxels to be fused (0.8)");
            pw.println("#minCorrelation ");
            pw.println("##(double) Minimum value of amplitude ratio for a pair of voxels to be fused (0.9)");
            pw.println("#minAmpRatio ");
            pw.println("##(double) Minimum diagonal component amplitude of AtA for a voxel to be fused (0)");
            pw.println("#minDiagonalAmplitude ");
        }
        System.err.println(outPath + " is created.");
    }

    public MultigridDesigner(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);

        if (property.containsKey("ataPath")) {
            ataPath = property.parsePath("ataPath", null, true, workPath);
        } else {
            basicPath = property.parsePath("basicPath", null, true, workPath);
            partialPath = property.parsePath("partialPath", null, true, workPath);
        }
        unknownParameterPath = property.parsePath("unknownParameterPath", null, true, workPath);

        if (property.containsKey("partialTypes"))
            partialTypes = Arrays.stream(property.parseStringArray("partialTypes", null)).map(PartialType::valueOf)
                    .collect(Collectors.toList());
        weightingType = WeightingType.valueOf(property.parseString("weightingType", "RECIPROCAL"));
        minCorrelation = property.parseDouble("minCorrelation", "0.8");
        minAmpRatio = property.parseDouble("minAmpRatio", "0.9");
        minDiagonalAmplitude = property.parseDouble("minDiagonalAmplitude", "0");
    }

    @Override
    public void run() throws IOException {
        String dateStr = GadgetAid.getTemporaryString();

        // read input and construct AtA
        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownParameterPath);
        RealMatrix ata;
        if (ataPath != null) {
            ata = AtAFile.read(ataPath);
            if (ata.getColumnDimension() != parameterList.size())
                throw new IllegalArgumentException("AtA size does not match number of parameters.");
        } else {
            // read input
            List<BasicID> basicIDs = BasicIDFile.read(basicPath, true);
            List<PartialID> partialIDs = PartialIDFile.read(partialPath, true);

            // assemble matrices
            MatrixAssembly assembler = new MatrixAssembly(basicIDs, partialIDs, parameterList, weightingType, false);
            ata = assembler.getAta();
        }

        // prepare output folder
        outPath = DatasetAid.createOutputFolder(workPath, "multigrid", folderTag, dateStr);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output unknown parameter with large diagonal component and correlation
        Path logPath = outPath.resolve("multigridDesigner.log");
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

                    double coeff = ata.getEntry(i, j) / FastMath.sqrt(ata.getEntry(i, i) * ata.getEntry(j, j));
                    double ampRatio = FastMath.sqrt(ata.getEntry(i, i) / ata.getEntry(j, j));
                    if (ata.getEntry(i, i) > minDiagonalAmplitude && coeff > minCorrelation && ampRatio > minAmpRatio && ampRatio < 1 / minAmpRatio) {
                        GadgetAid.dualPrintln(pw, i + " " + j + " " + ata.getEntry(i, i) + " " + ata.getEntry(i, j) + " " + coeff);
                        GadgetAid.dualPrintln(pw, " - " + parameterList.get(i));
                        GadgetAid.dualPrintln(pw, " - " + parameterList.get(j));
                        multigrid.addFusion(parameterList.get(i), parameterList.get(j));
                    }
                }
            }
        }

        // output multigrid design file
        Path outputMultigridPath = outPath.resolve("multigrid.inf");
        MultigridInformationFile.write(multigrid, outputMultigridPath);

        // output unknown parameter file
        List<UnknownParameter> fusedParameterList = parameterList.stream()
                .filter(param -> !multigrid.fuses(param)).collect(Collectors.toList());
        fusedParameterList.addAll(multigrid.getFusedParameters());
        Path outputUnknownsPath = outPath.resolve("unknowns.lst");
        UnknownParameterFile.write(fusedParameterList, outputUnknownsPath);
    }

}
