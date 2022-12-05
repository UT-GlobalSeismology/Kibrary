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
import io.github.kensuke1984.kibrary.inversion.addons.WeightingType;
import io.github.kensuke1984.kibrary.inversion.setup.AMatrixBuilder;
import io.github.kensuke1984.kibrary.inversion.setup.DVectorBuilder;
import io.github.kensuke1984.kibrary.inversion.setup.Weighting;
import io.github.kensuke1984.kibrary.math.ParallelizedMatrix;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * Operation for performing trade-off test of structure in the target region and structure outside it.
 * <p>
 * Trade-off is checked by computing the correlation coefficient between two partial waveforms.
 * This is the same as saying "the cosine of the angle between the two vectors of partial derivatives".
 *
 * @author otsuru
 * @since 2022/11/22
 */
public class OrthogonalityTest extends Operation {

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
     * components to be included in the dataset
     */
    private Set<SACComponent> components;

    /**
     * path of partial ID file for the target region
     */
    private Path mainPartialIDPath;
    /**
     * path of partial data for the target region
     */
    private Path mainPartialPath;
    /**
     * Path of unknown parameter file for the target region
     */
    private Path mainUnknownsPath;
    /**
     * path of partial ID file created for this test
     */
    private Path testPartialIDPath;
    /**
     * path of partial data created for this test
     */
    private Path testPartialPath;
    /**
     * Path of unknown parameter file created for this test
     */
    private Path testUnknownsPath;
    /**
     * path of basic ID file
     * This is used to align the two sets of partial waveforms in the same order.
     */
    private Path basicIDPath;
    /**
     * path of waveform data
     * This is needed in addition to the basicIDPath to compute the weightings.
     */
    private Path basicPath;
    private WeightingType weightingType;
    private GlobalCMTID specificEvent;
    private String specificObserverName;

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
            pw.println("##Path of a working directory. (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output folder name. If no tag is needed, leave this unset.");
            pw.println("#folderTag ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##########Information for voxels in the target region");
            pw.println("##Path of a partial ID file for the target region, must be set");
            pw.println("#mainPartialIDPath partialID.dat");
            pw.println("##Path of a partial waveform file for the target region, must be set");
            pw.println("#mainPartialPath partial.dat");
            pw.println("##Path of an unknown parameter list file　for the target region, must be set");
            pw.println("#mainUnknownsPath unknowns.lst");
            pw.println("##########Information for the test voxels");
            pw.println("##Path of a partial ID file created for this test, must be set");
            pw.println("#testPartialIDPath partialID.dat");
            pw.println("##Path of a partial waveform file created for this test, must be set");
            pw.println("#testPartialPath partial.dat");
            pw.println("##Path of an unknown parameter list file　created for this test, must be set");
            pw.println("#testUnknownsPath unknowns.lst");
            pw.println("##########Information of event-observer pairs");
            pw.println("##Path of a basic ID file, must be set");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##Path of a basic waveform file, must be set");
            pw.println("#basicPath actual.dat");
            pw.println("##Weighting type, from {LOWERUPPERMANTLE,RECIPROCAL,TAKEUCHIKOBAYASHI,IDENTITY,FINAL} (RECIPROCAL)");
            pw.println("#weightingType ");
            pw.println("##GCMT ID of a specific event, must be set");
            pw.println("#specificEvent ");
            pw.println("##A specific observer, in the form STA_NET, must be set");
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
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        mainPartialIDPath = property.parsePath("mainPartialIDPath", null, true, workPath);
        mainPartialPath = property.parsePath("mainPartialPath", null, true, workPath);
        mainUnknownsPath = property.parsePath("mainUnknownsPath", null, true, workPath);
        testPartialIDPath = property.parsePath("testPartialIDPath", null, true, workPath);
        testPartialPath = property.parsePath("testPartialPath", null, true, workPath);
        testUnknownsPath = property.parsePath("testUnknownsPath", null, true, workPath);

        basicIDPath = property.parsePath("basicIDPath", null, true, workPath);
        basicPath = property.parsePath("basicPath", null, true, workPath);
        weightingType = WeightingType.valueOf(property.parseString("weightingType", "RECIPROCAL"));
        specificEvent = new GlobalCMTID(property.parseString("specificEvent", null));
        specificObserverName = property.parseString("specificObserverName", null);
    }

   @Override
   public void run() throws IOException {
       PartialID[] mainPartialIDs = PartialIDFile.read(mainPartialIDPath, mainPartialPath);
       PartialID[] testPartialIDs = PartialIDFile.read(testPartialIDPath, testPartialPath);

       List<UnknownParameter> mainUnknowns = UnknownParameterFile.read(mainUnknownsPath);
       List<UnknownParameter> testUnknowns = UnknownParameterFile.read(testUnknownsPath);

       BasicID[] basicIDs = BasicIDFile.read(basicIDPath, basicPath);
       basicIDs = Arrays.stream(basicIDs).filter(id -> components.contains(id.getSacComponent())).toArray(BasicID[]::new);

       double[][] correlations;
       Path outputPath;

       // one event to one observer
       System.err.println("## One event to one observer ##");
       BasicID[] oneToOneBasicIDs = Arrays.stream(basicIDs)
               .filter(id -> id.getGlobalCMTID().equals(specificEvent) && id.getObserver().toString().equals(specificObserverName))
               .toArray(BasicID[]::new);
       correlations = computeCorrelations(mainPartialIDs, testPartialIDs, mainUnknowns, testUnknowns, oneToOneBasicIDs);
       outputPath = workPath.resolve("oneToOne.txt");
       outputCorrelations(correlations, outputPath);

       // one event to all observers
       System.err.println("## One event to all observers ##");
       BasicID[] oneToAllBasicIDs = Arrays.stream(basicIDs)
               .filter(id -> id.getGlobalCMTID().equals(specificEvent))
               .toArray(BasicID[]::new);
       correlations = computeCorrelations(mainPartialIDs, testPartialIDs, mainUnknowns, testUnknowns, oneToAllBasicIDs);
       outputPath = workPath.resolve("oneToAll.txt");
       outputCorrelations(correlations, outputPath);

       // one event to one observer
       System.err.println("## All events to one observer ##");
       BasicID[] allToOneBasicIDs = Arrays.stream(basicIDs)
               .filter(id -> id.getObserver().toString().equals(specificObserverName))
               .toArray(BasicID[]::new);
       correlations = computeCorrelations(mainPartialIDs, testPartialIDs, mainUnknowns, testUnknowns, allToOneBasicIDs);
       outputPath = workPath.resolve("allToOne.txt");
       outputCorrelations(correlations, outputPath);

       // all events to all observers
       System.err.println("## All events to all observers ##");
       correlations = computeCorrelations(mainPartialIDs, testPartialIDs, mainUnknowns, testUnknowns, basicIDs);
       outputPath = workPath.resolve("allToAll.txt");
       outputCorrelations(correlations, outputPath);

   }

   private double[][] computeCorrelations(PartialID[] mainPartialIDs, PartialID[] testPartialIDs,
           List<UnknownParameter> mainUnknowns, List<UnknownParameter> testUnknowns, BasicID[] basicIDs) {

       // set DVector
       System.err.println("Setting data for d vector");
       DVectorBuilder dVectorBuilder = new DVectorBuilder(basicIDs);
       // set weighting
       System.err.println("Setting weighting of type " + weightingType);
       Weighting weighting = new Weighting(dVectorBuilder, weightingType, null);

       // set and assemble main A matrix
       System.err.println("Setting data for main A matrix");
       AMatrixBuilder mainAMatrixBuilder = new AMatrixBuilder(mainPartialIDs, mainUnknowns, dVectorBuilder);
       System.err.println("Assembling main A matrix");
       ParallelizedMatrix mainA = mainAMatrixBuilder.buildWithWeight(weighting, false);

       // set and assemble test A matrix
       System.err.println("Setting data for test A matrix");
       AMatrixBuilder testAMatrixBuilder = new AMatrixBuilder(testPartialIDs, testUnknowns, dVectorBuilder);
       System.err.println("Assembling test A matrix");
       ParallelizedMatrix testA = testAMatrixBuilder.buildWithWeight(weighting, false);

       // compute correlations
       double[][] correlations = new double[testUnknowns.size()][mainUnknowns.size()];
       for (int i = 0; i < testUnknowns.size(); i++) {
           UnknownParameter testUnknown = testUnknowns.get(i);
           RealVector testPartialVector = testA.getColumnVector(i);
           for (int j = 0; j < mainUnknowns.size(); j++) {
               UnknownParameter mainUnknown = mainUnknowns.get(j);
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
}
