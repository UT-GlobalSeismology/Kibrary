package io.github.kensuke1984.kibrary.waveform;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotColorName;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotLineAppearance;
import io.github.kensuke1984.kibrary.inversion.WeightingHandler;
import io.github.kensuke1984.kibrary.inversion.setup.AMatrixBuilder;
import io.github.kensuke1984.kibrary.inversion.setup.DVectorBuilder;
import io.github.kensuke1984.kibrary.math.ParallelizedMatrix;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * Operation for performing the orthogonality test
 * to check the trade-off between structure in the target region and structure outside it.
 * <p>
 * Orthogonality is checked by computing the correlation coefficient between two partial waveforms.
 * This is the same as saying "the cosine of the angle between the two vectors of partial derivatives".
 *
 * @author otsuru
 * @since 2022/11/22
 */
public class OrthogonalityTest extends Operation {

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
     * Components to use.
     */
    private Set<SACComponent> components;

    /**
     * Partial waveform folder for the target region.
     */
    private Path mainPartialPath;
    /**
     * Unknown parameter file for the target region.
     */
    private Path mainUnknownsPath;
    /**
     * Partial waveform folder created for this test.
     */
    private Path testPartialPath;
    /**
     * Unknown parameter file created for this test.
     */
    private Path testUnknownsPath;
    /**
     * Basic waveform folder.
     * This is used to align the two sets of partial waveforms in the same order and to compute the weightings.
     */
    private Path basicPath;
    private Path weightingPropertiesPath;
    private GlobalCMTID specificEvent;
    private String specificObserverName;

    private WeightingHandler weightingHandler;

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
            pw.println("##SacComponents to be used, listed using spaces. (Z R T)");
            pw.println("#components ");
            pw.println("##########Information for voxels in the target region.");
            pw.println("##Path of a partial waveform folder for the target region, must be set.");
            pw.println("#mainPartialPath partial");
            pw.println("##Path of an unknown parameter list file　for the target region, must be set.");
            pw.println("#mainUnknownsPath unknowns.lst");
            pw.println("##########Information for the test voxels.");
            pw.println("##Path of a partial waveform folder created for this test, must be set.");
            pw.println("#testPartialPath partial");
            pw.println("##Path of an unknown parameter list file　created for this test, must be set.");
            pw.println("#testUnknownsPath unknowns.lst");
            pw.println("##########Information of event-observer pairs.");
            pw.println("##Path of a basic waveform folder, must be set.");
            pw.println("#basicPath actual");
            pw.println("##Path of a weighting properties file, must be set.");
            pw.println("#weightingPropertiesPath ");
            pw.println("##GCMT ID of a specific event, must be set.");
            pw.println("#specificEvent ");
            pw.println("##A specific observer, in the form STA_NET, must be set.");
            pw.println("#specificObserverName ");
        }
        System.err.println(outPath + " is created.");
    }

    public OrthogonalityTest(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        mainPartialPath = property.parsePath("mainPartialPath", null, true, workPath);
        mainUnknownsPath = property.parsePath("mainUnknownsPath", null, true, workPath);
        testPartialPath = property.parsePath("testPartialPath", null, true, workPath);
        testUnknownsPath = property.parsePath("testUnknownsPath", null, true, workPath);

        basicPath = property.parsePath("basicPath", null, true, workPath);
        weightingPropertiesPath = property.parsePath("weightingPropertiesPath", null, true, workPath);
        specificEvent = new GlobalCMTID(property.parseString("specificEvent", null));
        specificObserverName = property.parseString("specificObserverName", null);
    }

   @Override
   public void run() throws IOException {
       List<UnknownParameter> mainUnknowns = UnknownParameterFile.read(mainUnknownsPath);
       List<UnknownParameter> testUnknowns = UnknownParameterFile.read(testUnknownsPath);

       List<BasicID> basicIDs = BasicIDFile.read(basicPath, true);
       basicIDs = basicIDs.stream().filter(id -> components.contains(id.getSacComponent())).collect(Collectors.toList());
       long nSpecificObserver = basicIDs.stream().map(BasicID::getObserver)
               .filter(observer -> observer.toString().equals(specificObserverName)).distinct().count();
       if (nSpecificObserver == 0) {
           System.err.println("CAUTION: observer with name " + specificObserverName + " does not exist!");
       } else if (nSpecificObserver > 1) {
           System.err.println("CAUTION: more than 1 observer with the name " + specificObserverName + "!");
       }

       List<PartialID> mainPartialIDs = PartialIDFile.read(mainPartialPath, true);
       List<PartialID> testPartialIDs = PartialIDFile.read(testPartialPath, true);

       weightingHandler = new WeightingHandler(weightingPropertiesPath);

       double[][] correlations;
       Path outputPath;

       outPath = DatasetAid.createOutputFolder(workPath, "orthogonality", folderTag, appendFolderDate, null);
       property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

       // one event to one observer
       System.err.println("## One event to one observer ##");
       List<BasicID> oneToOneBasicIDs = basicIDs.stream()
               .filter(id -> id.getGlobalCMTID().equals(specificEvent) && id.getObserver().toString().equals(specificObserverName))
               .collect(Collectors.toList());
       correlations = computeCorrelations(mainPartialIDs, testPartialIDs, mainUnknowns, testUnknowns, oneToOneBasicIDs);
       outputPath = outPath.resolve("oneToOne.txt");
       outputCorrelations(correlations, outputPath);

       // one event to all observers
       System.err.println("## One event to all observers ##");
       List<BasicID> oneToAllBasicIDs = basicIDs.stream()
               .filter(id -> id.getGlobalCMTID().equals(specificEvent))
               .collect(Collectors.toList());
       correlations = computeCorrelations(mainPartialIDs, testPartialIDs, mainUnknowns, testUnknowns, oneToAllBasicIDs);
       outputPath = outPath.resolve("oneToAll.txt");
       outputCorrelations(correlations, outputPath);

       // one event to one observer
       System.err.println("## All events to one observer ##");
       List<BasicID> allToOneBasicIDs = basicIDs.stream()
               .filter(id -> id.getObserver().toString().equals(specificObserverName))
               .collect(Collectors.toList());
       correlations = computeCorrelations(mainPartialIDs, testPartialIDs, mainUnknowns, testUnknowns, allToOneBasicIDs);
       outputPath = outPath.resolve("allToOne.txt");
       outputCorrelations(correlations, outputPath);

       // all events to all observers
       System.err.println("## All events to all observers ##");
       correlations = computeCorrelations(mainPartialIDs, testPartialIDs, mainUnknowns, testUnknowns, basicIDs);
       outputPath = outPath.resolve("allToAll.txt");
       outputCorrelations(correlations, outputPath);

       createPlot(mainUnknowns, testUnknowns);
   }

   private double[][] computeCorrelations(List<PartialID> mainPartialIDs, List<PartialID> testPartialIDs,
           List<UnknownParameter> mainUnknowns, List<UnknownParameter> testUnknowns, List<BasicID> basicIDs) throws IOException {

       // set DVector
       System.err.println("Setting data for d vector");
       DVectorBuilder dVectorBuilder = new DVectorBuilder(basicIDs);

       // set weighting
       System.err.println("Setting weighting");
       RealVector[] weighting = weightingHandler.weightWaveforms(dVectorBuilder);

       // set and assemble main A matrix
       System.err.println("Assembling main A matrix");
       AMatrixBuilder mainAMatrixBuilder = new AMatrixBuilder(mainUnknowns, dVectorBuilder);
       ParallelizedMatrix mainA = mainAMatrixBuilder.buildWithWeight(mainPartialIDs, weighting, false);

       // set and assemble test A matrix
       System.err.println("Assembling test A matrix");
       AMatrixBuilder testAMatrixBuilder = new AMatrixBuilder(testUnknowns, dVectorBuilder);
       ParallelizedMatrix testA = testAMatrixBuilder.buildWithWeight(testPartialIDs, weighting, false);

       // compute correlations
       double[][] correlations = new double[testUnknowns.size()][mainUnknowns.size()];
       for (int i = 0; i < testUnknowns.size(); i++) {
           RealVector testPartialVector = testA.getColumnVector(i);
           for (int j = 0; j < mainUnknowns.size(); j++) {
               RealVector mainPartialVector = mainA.getColumnVector(j);

               double correlation = mainPartialVector.dotProduct(testPartialVector) / mainPartialVector.getNorm() / testPartialVector.getNorm();
               correlations[i][j] = correlation;
           }
       }
       return correlations;
   }

   private void outputCorrelations(double[][] correlations, Path outputPath) throws IOException {
       try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
           for (int j = 0; j < correlations[0].length; j++) {
               pw.print(j);
               for (int i = 0; i < correlations.length; i++) {
                   pw.print(" " + correlations[i][j]);
               }
               pw.println();
           }
       }
   }

   private void createPlot(List<UnknownParameter> mainUnknowns, List<UnknownParameter> testUnknowns) throws IOException {
       GnuplotFile gnuplot = new GnuplotFile(outPath.resolve("orthogonality.plt"));
       gnuplot.setOutput("png", "orthogonality.png", 1280, 960, false);
       gnuplot.setFont("Arial", 20, 18, 18, 16, 18);
       gnuplot.setNMultiplotColumn(2);
       gnuplot.unsetCommonKey();
       gnuplot.setCommonXlabel("voxel #");
       gnuplot.setCommonYlabel("cosine");
       gnuplot.setCommonXrange(0, mainUnknowns.size() - 1);
       gnuplot.setCommonYrange(-1, 1);

       int nAppearances = 8;
       GnuplotLineAppearance appearances[] = new GnuplotLineAppearance[nAppearances];
       appearances[0] = new GnuplotLineAppearance(1, GnuplotColorName.red, 1);
       appearances[1] = new GnuplotLineAppearance(1, GnuplotColorName.gold, 1);
       appearances[2] = new GnuplotLineAppearance(1, GnuplotColorName.forest_green, 1);
       appearances[3] = new GnuplotLineAppearance(1, GnuplotColorName.cyan, 1);
       appearances[4] = new GnuplotLineAppearance(1, GnuplotColorName.blue, 1);
       appearances[5] = new GnuplotLineAppearance(1, GnuplotColorName.dark_violet, 1);
       appearances[6] = new GnuplotLineAppearance(1, GnuplotColorName.magenta, 1);
       appearances[7] = new GnuplotLineAppearance(1, GnuplotColorName.dark_gray, 1);

       for (int i = 0; i < testUnknowns.size(); i++) {
           gnuplot.setTitle("1 event (" + specificEvent + ") and 1 station (" + specificObserverName.replace("_", "\\\\_") + ")");
           gnuplot.addLine("oneToOne.txt", 1, i + 2, appearances[i % nAppearances],
                   MathAid.simplestString(testUnknowns.get(i).getPosition().getDepth()) + " km");
       }
       gnuplot.nextField();

       for (int i = 0; i < testUnknowns.size(); i++) {
           gnuplot.setTitle("1 event (" + specificEvent + "), all stations");
           gnuplot.addLine("oneToAll.txt", 1, i + 2, appearances[i % nAppearances],
                   MathAid.simplestString(testUnknowns.get(i).getPosition().getDepth()) + " km");
       }
       gnuplot.nextField();

       for (int i = 0; i < testUnknowns.size(); i++) {
           gnuplot.setTitle("1 station (" + specificObserverName.replace("_", "\\\\_") + "), all events");
           gnuplot.addLine("allToOne.txt", 1, i + 2, appearances[i % nAppearances],
                   MathAid.simplestString(testUnknowns.get(i).getPosition().getDepth()) + " km");
       }
       gnuplot.nextField();

       for (int i = 0; i < testUnknowns.size(); i++) {
           gnuplot.setTitle("All events and all stations");
           gnuplot.setKey(false, true, "top right");
           gnuplot.addLine("allToAll.txt", 1, i + 2, appearances[i % nAppearances],
                   MathAid.simplestString(testUnknowns.get(i).getPosition().getDepth()) + " km");
       }

       gnuplot.write();
       if (!gnuplot.execute()) System.err.println("gnuplot failed!!");
   }
}
