package io.github.kensuke1984.kibrary.fusion;

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
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.inversion.WeightingHandler;
import io.github.kensuke1984.kibrary.inversion.setup.MatrixAssembly;
import io.github.kensuke1984.kibrary.math.MatrixFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * Computes correlation between partial waveforms of each unknown parameter,
 * and designs an adaptive grid by fusing voxels with large correlation between their partial waveforms.
 *
 * TODO when A~B and B~C (A~C may or may not be true)
 * TODO: only checked for 3D. May or may not work for 1D.
 *
 * @author otsuru
 * @since 2022/8/1
 */
public class AdaptiveGridDesigner extends Operation {

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
     * Path of ata file.
     */
    private Path ataPath;
    /**
     * Path of basic waveform folder.
     */
    private Path basicPath;
    /**
     * Path of partial waveform folder.
     */
    private Path partialPath;
    /**
     * Path of unknown parameter file.
     */
    private Path unknownParameterPath;
    /**
     * Partial types of parameters to be fused.
     */
    private List<VariableType> variableTypes;

    private Path weightingPropertiesPath;

    private double minCorrelation;
    private double minAmpRatio;
    private double minDiagonalAmplitude;

    /**
     * @param args (String[]) Arguments: none to create a property file, path of property file to run it.
     * @throws IOException
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile(null);
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile(String tag) throws IOException {
        String className = new Object(){}.getClass().getEnclosingClass().getSimpleName();
        Path outPath = DatasetAid.generateOutputFilePath(Paths.get(""), className, tag, true, null, ".properties");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + className);
            pw.println("##Path of work folder. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##(boolean) Whether to append date string at end of output folder name. (true)");
            pw.println("#appendFolderDate false");
            pw.println("##########If this section is set, the next section is not neeeded.");
            pw.println("##Path of an AtA file.");
            pw.println("#ataPath ata.lst");
            pw.println("##########If the previous section is set, this section is not neeeded.");
            pw.println("##Path of a basic waveform folder.");
            pw.println("#basicPath actual");
            pw.println("##Path of a partial waveform folder.");
            pw.println("#partialPath partial");
            pw.println("##Path of a weighting properties file, must be set.");
            pw.println("#weightingPropertiesPath ");
            pw.println("##########Other settings.");
            pw.println("##Path of an unknown parameter list file, must be set and must match ata file if it is used.");
            pw.println("#unknownParameterPath unknowns.lst");
            pw.println("##Variable types of parameters to fuse. If not set, all variable types will be used.");
            pw.println("#variableTypes ");
            pw.println("##(double) Minimum value of correlation for a pair of voxels to be fused. (0.8)");
            pw.println("#minCorrelation ");
            pw.println("##(double) Minimum value of amplitude ratio for a pair of voxels to be fused. (0.9)");
            pw.println("#minAmpRatio ");
            pw.println("##(double) Minimum diagonal component amplitude of AtA for a voxel to be fused. (0)");
            pw.println("#minDiagonalAmplitude ");
        }
        System.err.println(outPath + " is created.");
    }

    public AdaptiveGridDesigner(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        if (property.containsKey("ataPath")) {
            ataPath = property.parsePath("ataPath", null, true, workPath);
        } else {
            basicPath = property.parsePath("basicPath", null, true, workPath);
            partialPath = property.parsePath("partialPath", null, true, workPath);
        }
        unknownParameterPath = property.parsePath("unknownParameterPath", null, true, workPath);

        if (property.containsKey("variableTypes"))
            variableTypes = Arrays.stream(property.parseStringArray("variableTypes", null)).map(VariableType::valueOf)
                    .collect(Collectors.toList());
        weightingPropertiesPath = property.parsePath("weightingPropertiesPath", null, true, workPath);
        minCorrelation = property.parseDouble("minCorrelation", "0.8");
        minAmpRatio = property.parseDouble("minAmpRatio", "0.9");
        minDiagonalAmplitude = property.parseDouble("minDiagonalAmplitude", "0");
    }

    @Override
    public void run() throws IOException {

        // read input and construct AtA
        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownParameterPath);
        RealMatrix ata;
        if (ataPath != null) {
            ata = MatrixFile.read(ataPath);
            if (ata.getColumnDimension() != parameterList.size())
                throw new IllegalArgumentException("AtA size does not match number of parameters.");
        } else {
            // read input
            WeightingHandler weightingHandler = new WeightingHandler(weightingPropertiesPath);

            // assemble matrices
            MatrixAssembly assembler = new MatrixAssembly(basicPath, partialPath, parameterList, weightingHandler, false);
            ata = assembler.getAta();
        }

        // prepare output folder
        Path outPath = DatasetAid.createOutputFolder(workPath, "adaptiveGrid", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        // output unknown parameter with large diagonal component and correlation
        Path logPath = outPath.resolve("adaptiveGridDesigner.log");
        FusionDesign fusionDesign = new FusionDesign();
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(logPath))) {
            GadgetAid.dualPrintln(pw, "# i j AtA(i,i) AtA(i,j) coeff");

            for (int i = 0; i < parameterList.size(); i++) {
                if (variableTypes != null && variableTypes.contains(parameterList.get(i).getVariableType()) == false)
                    continue;

                for (int j = 0; j < parameterList.size(); j++) {
                    if (i == j) continue;
                    if (!parameterList.get(i).getVariableType().equals(parameterList.get(j).getVariableType()))
                        continue;

                    double coeff = ata.getEntry(i, j) / Math.sqrt(ata.getEntry(i, i) * ata.getEntry(j, j));
                    double ampRatio = Math.sqrt(ata.getEntry(i, i) / ata.getEntry(j, j));
                    if (ata.getEntry(i, i) > minDiagonalAmplitude && coeff > minCorrelation && ampRatio > minAmpRatio && ampRatio < 1 / minAmpRatio) {
                        GadgetAid.dualPrintln(pw, i + " " + j + " " + ata.getEntry(i, i) + " " + ata.getEntry(i, j) + " " + coeff);
                        GadgetAid.dualPrintln(pw, " - " + parameterList.get(i));
                        GadgetAid.dualPrintln(pw, " - " + parameterList.get(j));
                        fusionDesign.addFusion(parameterList.get(i), parameterList.get(j));
                    }
                }
            }
        }

        // output fusion design file
        Path outputFusionPath = outPath.resolve("fusion.inf");
        FusionInformationFile.write(fusionDesign, outputFusionPath);

        // output unknown parameter file
        List<UnknownParameter> fusedParameterList = parameterList.stream()
                .filter(param -> !fusionDesign.fuses(param)).collect(Collectors.toList());
        fusedParameterList.addAll(fusionDesign.getFusedParameters());
        Path outputUnknownsPath = outPath.resolve("unknowns.lst");
        UnknownParameterFile.write(fusedParameterList, outputUnknownsPath);
    }

}
