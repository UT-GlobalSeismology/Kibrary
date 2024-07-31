package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.InformationFileReader;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * Operation to sum up the values of {@link OrthogonalityTest}
 * when it has been done for multiple separate parts of the dataset (i.e. in case the dataset is too large).
 *
 * @author otsuru
 * @since 2024/7/31
 */
public class OrthogonalitySumUp extends Operation {

    private static final int MAX_INPUT = 10;

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
     * Path of the output folder.
     */
    private Path outPath;

    /**
     * Unknown parameter file created for this test.
     */
    private Path testUnknownsPath;
    /**
     * Unknown parameter file for the target region.
     */
    private Path mainUnknownsPath;
    private GlobalCMTID specificEvent;
    private String specificObserverName;

    private List<Path> orthogonalityPaths = new ArrayList<>();

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
            pw.println("##Path of an unknown parameter list file　created for this test, must be set.");
            pw.println("#testUnknownsPath unknowns.lst");
            pw.println("##Path of an unknown parameter list file　for the target region, must be set.");
            pw.println("#mainUnknownsPath unknowns.lst");
            pw.println("##GCMT ID of a specific event, must be set.");
            pw.println("#specificEvent ");
            pw.println("##A specific observer, in the form STA_NET, must be set.");
            pw.println("#specificObserverName ");
            pw.println("##########From here on, list up paths of orthogonality test folders to sum up.");
            pw.println("########## Up to " + MAX_INPUT + " folders can be managed. Any entry may be left unset.");
            for (int i = 1; i <= MAX_INPUT; i++) {
                pw.println("##" + MathAid.ordinalNumber(i) + " folder.");
                pw.println("#orthogonalityPath" + i + " orthogonality");
            }
        }
        System.err.println(outPath + " is created.");
    }

    public OrthogonalitySumUp(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");

        testUnknownsPath = property.parsePath("testUnknownsPath", null, true, workPath);
        mainUnknownsPath = property.parsePath("mainUnknownsPath", null, true, workPath);
        specificEvent = new GlobalCMTID(property.parseString("specificEvent", null));
        specificObserverName = property.parseString("specificObserverName", null);

        for (int i = 1; i <= MAX_INPUT; i++) {
            String orthogonalityKey = "orthogonalityPath" + i;
            if (property.containsKey(orthogonalityKey)) {
                orthogonalityPaths.add(property.parsePath(orthogonalityKey, null, true, workPath));
            }
        }
    }

    @Override
    public void run() throws IOException {
        List<UnknownParameter> testUnknowns = UnknownParameterFile.read(testUnknownsPath);
        List<UnknownParameter> mainUnknowns = UnknownParameterFile.read(mainUnknownsPath);
        int numTest = testUnknowns.size();
        int numMain = mainUnknowns.size();

        outPath = DatasetAid.createOutputFolder(workPath, "orthogonality", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        sumPanel(numTest, numMain, "oneToOne");
        sumPanel(numTest, numMain, "oneToAll");
        sumPanel(numTest, numMain, "allToOne");
        sumPanel(numTest, numMain, "allToAll");

        OrthogonalityTest.createPlot(testUnknowns, mainUnknowns, specificEvent, specificObserverName, outPath);
    }

    private void sumPanel(int numTest, int numMain, String panelName) throws IOException {
        double[] testPartialNorm2s_sum = new double[numTest];
        double[] mainPartialNorm2s_sum = new double[numMain];
        double[][] innerProducts_sum = new double[numTest][numMain];
        double[][] correlations_sum = new double[numTest][numMain];

        // initialize with 0s (they should already be initialized, but just in case)
        for (int i = 0; i < numTest; i++) {
            testPartialNorm2s_sum[i] = 0.0;
            for (int j = 0; j < numMain; j++) {
                innerProducts_sum[i][j] = 0.0;
            }
        }
        for (int j = 0; j < numMain; j++) {
            mainPartialNorm2s_sum[j] = 0.0;
        }

        // accumulate inner products and norm2s for all folders
        for (Path orthogonalityPath : orthogonalityPaths) {
            Path panelPath = orthogonalityPath.resolve(panelName);
            // when no raypaths exist for this dataset, directory is not created, so skip
            if (!Files.exists(panelPath)) continue;

            double[] testPartialNorms_this = readVector(panelPath.resolve("testPartialNorms.lst"), numTest);
            double[] mainPartialNorms_this = readVector(panelPath.resolve("mainPartialNorms.lst"), numMain);
            double[][] innerProducts_this = readMatrix(panelPath.resolve("innerProducts.lst"), numTest, numMain);

            for (int i = 0; i < numTest; i++) {
                testPartialNorm2s_sum[i] += testPartialNorms_this[i] * testPartialNorms_this[i];
                for (int j = 0; j < numMain; j++) {
                    innerProducts_sum[i][j] += innerProducts_this[i][j];
                }
            }
            for (int j = 0; j < numMain; j++) {
                mainPartialNorm2s_sum[j] = mainPartialNorms_this[j] * mainPartialNorms_this[j];
            }
        }

        // compute correlations
        for (int i = 0; i < numTest; i++) {
            for (int j = 0; j < numMain; j++) {
                correlations_sum[i][j] = innerProducts_sum[i][j] / Math.sqrt(mainPartialNorm2s_sum[j]) / Math.sqrt(testPartialNorm2s_sum[i]);
            }
        }

        // output
        Path outPanelPath = outPath.resolve(panelName);
        outputVectorSqrt(testPartialNorm2s_sum, outPanelPath.resolve("testPartialNorms.lst"));
        outputVectorSqrt(mainPartialNorm2s_sum, outPanelPath.resolve("mainPartialNorms.lst"));
        OrthogonalityTest.outputMatrix(innerProducts_sum, outPanelPath.resolve("innerProducts.lst"));
        OrthogonalityTest.outputMatrix(correlations_sum, outPanelPath.resolve("correlations.lst"));
    }

    private double[] readVector(Path inputPath, int num) throws IOException {
        double[] vector = new double[num];

        // read input file
        InformationFileReader reader = new InformationFileReader(inputPath, true);
        String[] lines = reader.getNonCommentLines();
        if (lines.length != num) throw new IllegalStateException("File size does not match dimension.");

        // fill vector
        for (int i = 0; i < num; i++) {
            vector[i] = Double.parseDouble(lines[i]);
        }
        return vector;
    }

    private double[][] readMatrix(Path inputPath, int numTest, int numMain) throws IOException {
        double[][] matrix = new double[numTest][numMain];

        // read input file
        InformationFileReader reader = new InformationFileReader(inputPath, true);
        String[] lines = reader.getNonCommentLines();
        if (lines.length != numMain) throw new IllegalStateException("File size does not match dimension.");

        // fill matrix
        for (int j = 0; j < numMain; j++) {
            String[] rowAsString = lines[j].split("\\s+");
            if (rowAsString.length != numTest + 1) throw new IllegalStateException("File size does not match dimension.");

            for (int i = 0; i < numTest; i++) {
                matrix[i][j] = Double.parseDouble(rowAsString[i + 1]);
            }
        }
        return matrix;
    }

    private static void outputVectorSqrt(double[] vector, Path outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            for (int i = 0; i < vector.length; i++) {
                pw.println(Math.sqrt(vector[i]));
            }
        }
    }

}
