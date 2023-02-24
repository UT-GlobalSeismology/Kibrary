package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;

import org.apache.commons.math3.linear.ArrayRealVector;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotColorName;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.Trace;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.BasicIDPairUp;

/**
 * Creates and plots binned stacks for each event included in a {@link BasicIDFile}e.
 * <p>
 * A pair of a basic ID file and basic waveform file is required as input.
 * <p>
 * Event directories will be created under workPath if they do not already exist.
 * Text files of waveform data for each bin will be written under the event directories.
 * They will be regenerated each time beacause their contents depend on the settings used
 * (such as which observers to use, plotting by distance or by azimuth, standarization method, etc.).
 * <p>
 * Output pdf files and their corresponding plt files will be created under each of the event directories.
 *
 * @author otsuru
 * @since 2022/7/27 divided from visual.RecordSectionCreater
 */
public class BasicBinnedStackCreator extends Operation {

    /**
     * The interval of exporting travel times
     */
    private static final double TRAVEL_TIME_INTERVAL = 1;
    /**
     * The interval of deciding graph size; should be a multiple of TRAVEL_TIME_INTERVAL
     */
    private static final int GRAPH_SIZE_INTERVAL = 2;
    /**
     * How much space to provide at the rim of the graph in the y axis
     */
    private static final int Y_AXIS_RIM = 2;
    /**
     * How much space to provide at the rim of the graph in the time axis
     */
    private static final int TIME_RIM = 10;

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
     * Path of a basic waveform folder
     */
    private Path mainBasicPath;
    /**
     * Path of reference waveform folder 1
     */
    private Path refBasicPath1;
    /**
     * Path of reference waveform folder 2
     */
    private Path refBasicPath2;

    /**
     * Events to work for. If this is empty, work for all events in workPath.
     */
    private Set<GlobalCMTID> tendEvents = new HashSet<>();
    private double binWidth;
    private BasicPlotAid.AmpStyle obsAmpStyle;
    private BasicPlotAid.AmpStyle synAmpStyle;
    private double ampScale;

    /**
     * Whether to plot the figure with azimuth as the Y-axis
     */
    private boolean byAzimuth;
    /**
     * Whether to set the azimuth range to [-180:180) instead of [0:360)
     */
    private boolean flipAzimuth;
    /**
     * Names of phases to plot travel time curves
     */
    private String[] displayPhases;
    /**
     * Names of phases to use to align the record section. The fastest of these arrivals is used.
     */
    private String[] alignPhases;
    /**
     * apparent velocity to use when reducing time [s/deg]
     */
    private double reductionSlowness;
    /**
     * Name of structure to compute travel times
     */
    private String structureName;

    private double lowerDistance;
    private double upperDistance;
    private double lowerAzimuth;
    private double upperAzimuth;

    private int mainSynStyle;
    private String mainSynName;
    private int refSynStyle1;
    private String refSynName1;
    private int refSynStyle2;
    private String refSynName2;

    private List<BasicID> refBasicIDs1;
    private List<BasicID> refBasicIDs2;
    private double samplingStep;

    /**
     * Inxtance of tool to use to compute travel times
     */
    private TauP_Time timeTool;

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
            pw.println("##Path of a basic waveform folder (.)");
            pw.println("#mainBasicPath ");
            pw.println("##Path of reference basic waveform folder 1, when plotting their waveforms");
            pw.println("#refBasicPath1 ");
            pw.println("##Path of reference basic waveform folder 2, when plotting their waveforms");
            pw.println("#refBasicPath2 ");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces. To use all events, leave this unset.");
            pw.println("#tendEvents ");
            pw.println("##(double) The width of each bin [deg] (1.0)");
            pw.println("#binWidth ");
            pw.println("##Method for standarization of observed waveform amplitude, from {obsEach,synEach,obsMean,synMean} (synEach)");
            pw.println("#obsAmpStyle ");
            pw.println("##Method for standarization of synthetic waveform amplitude, from {obsEach,synEach,obsMean,synMean} (synEach)");
            pw.println("#synAmpStyle ");
            pw.println("##(double) Coefficient to multiply to all waveforms (1.0)");
            pw.println("#ampScale ");
            pw.println("##(boolean) Whether to plot the figure with azimuth as the Y-axis (false)");
            pw.println("#byAzimuth ");
            pw.println("##(boolean) Whether to set the azimuth range to [-180:180) instead of [0:360) (false)");
            pw.println("## This is effective when using south-to-north raypaths in byAzimuth mode.");
            pw.println("#flipAzimuth ");
            pw.println("##Names of phases to plot travel time curves, listed using spaces. Only when byAzimuth is false.");
            pw.println("#displayPhases ");
            pw.println("##Names of phases to use for alignment, listed using spaces. When unset, the following reductionSlowness will be used.");
            pw.println("##  When multiple phases are set, the fastest arrival of them will be used for alignment.");
            pw.println("#alignPhases ");
            pw.println("##(double) The apparent slowness to use for time reduction [s/deg] (0)");
            pw.println("#reductionSlowness ");
            pw.println("##(String) Name of structure to compute travel times using TauP (prem)");
            pw.println("#structureName ");
            pw.println("##(double) Lower limit of range of epicentral distance to be used [deg] [0:upperDistance) (0)");
            pw.println("#lowerDistance ");
            pw.println("##(double) Upper limit of range of epicentral distance to be used [deg] (lowerDistance:180] (180)");
            pw.println("#upperDistance ");
            pw.println("##(double) Lower limit of range of azimuth to be used [deg] [-360:upperAzimuth) (0)");
            pw.println("#lowerAzimuth ");
            pw.println("##(double) Upper limit of range of azimuth to be used [deg] (lowerAzimuth:360] (360)");
            pw.println("#upperAzimuth ");
            pw.println("##Plot style for main synthetic waveform, from {0:no plot, 1:red, 2:green, 3:blue} (1)");
            pw.println("#mainSynStyle 2");
            pw.println("##Name for main synthetic waveform (synthetic)");
            pw.println("#mainSynName recovered");
            pw.println("##Plot style for reference synthetic waveform 1, from {0:no plot, 1:red, 2:green, 3:blue} (0)");
            pw.println("#refSynStyle1 1");
            pw.println("##Name for reference synthetic waveform 1 (reference1)");
            pw.println("#refSynName1 initial");
            pw.println("##Plot style for reference synthetic waveform 2, from {0:no plot, 1:red, 2:green, 3:blue} (0)");
            pw.println("#refSynStyle2 ");
            pw.println("##Name for reference synthetic waveform 2 (reference2)");
            pw.println("#refSynName2 ");
        }
        System.err.println(outPath + " is created.");
    }

    public BasicBinnedStackCreator(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        mainBasicPath = property.parsePath("mainBasicPath", ".", true, workPath);
        if (property.containsKey("refBasicPath1"))
            refBasicPath1 = property.parsePath("refBasicPath1", ".", true, workPath);
        if (property.containsKey("refBasicPath2"))
            refBasicPath2 = property.parsePath("refBasicPath2", ".", true, workPath);

        if (property.containsKey("tendEvents")) {
            tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                    .collect(Collectors.toSet());
        }

        binWidth = property.parseDouble("binWidth", "1.0");
        obsAmpStyle = BasicPlotAid.AmpStyle.valueOf(property.parseString("obsAmpStyle", "synEach"));
        synAmpStyle = BasicPlotAid.AmpStyle.valueOf(property.parseString("synAmpStyle", "synEach"));
        ampScale = property.parseDouble("ampScale", "1.0");

        byAzimuth = property.parseBoolean("byAzimuth", "false");
        flipAzimuth = property.parseBoolean("flipAzimuth", "false");

        if (property.containsKey("displayPhases") && byAzimuth == false)
            displayPhases = property.parseStringArray("displayPhases", null);
        if (property.containsKey("alignPhases"))
            alignPhases = property.parseStringArray("alignPhases", null);
        reductionSlowness = property.parseDouble("reductionSlowness", "0");
        structureName = property.parseString("structureName", "prem").toLowerCase();

        lowerDistance = property.parseDouble("lowerDistance", "0");
        upperDistance = property.parseDouble("upperDistance", "180");
        if (lowerDistance < 0 || lowerDistance > upperDistance || 180 < upperDistance)
            throw new IllegalArgumentException("Distance range " + lowerDistance + " , " + upperDistance + " is invalid.");

        lowerAzimuth = property.parseDouble("lowerAzimuth", "0");
        upperAzimuth = property.parseDouble("upperAzimuth", "360");
        if (lowerAzimuth < -360 || lowerAzimuth > upperAzimuth || 360 < upperAzimuth)
            throw new IllegalArgumentException("Azimuth range " + lowerAzimuth + " , " + upperAzimuth + " is invalid.");

        mainSynStyle = property.parseInt("mainSynStyle", "1");
        mainSynName = property.parseString("mainSynName", "synthetic");
        refSynStyle1 = property.parseInt("refSynStyle1", "0");
        refSynName1 = property.parseString("refSynName1", "reference1");
        refSynStyle2 = property.parseInt("refSynStyle2", "0");
        refSynName2 = property.parseString("refSynName2", "reference2");
        if (refSynStyle1 != 0 && refBasicPath1 == null)
            throw new IllegalArgumentException("refBasicPath1 must be set when refSynStyle1 != 0");
        if (refSynStyle2 != 0 && refBasicPath2 == null)
            throw new IllegalArgumentException("refBasicPath2 must be set when refSynStyle2 != 0");
    }

   @Override
   public void run() throws IOException {

       // read main basic waveform folders
       List<BasicID> mainBasicIDs = BasicIDFile.read(mainBasicPath, true).stream()
               .filter(id -> components.contains(id.getSacComponent())).collect(Collectors.toList());
       if (!tendEvents.isEmpty()) {
           mainBasicIDs = mainBasicIDs.stream().filter(id -> tendEvents.contains(id.getGlobalCMTID())).collect(Collectors.toList());
       }

       // collect events included in mainBasicIDs
       Set<GlobalCMTID> events = mainBasicIDs.stream().map(id -> id.getGlobalCMTID()).distinct().collect(Collectors.toSet());
       if (!DatasetAid.checkNum(events.size(), "event", "events")) {
           return;
       }

       // check sampling rate
       double[] samplingHzs = mainBasicIDs.stream().mapToDouble(id -> id.getSamplingHz()).distinct().toArray();
       if (samplingHzs.length != 1) {
           Arrays.stream(samplingHzs).forEach(hz -> System.err.print(hz + " "));
           throw new IllegalStateException("Data with different sampling rates exist");
       }
       samplingStep = 1 / samplingHzs[0];

       // read reference basic waveform folders
       if (refBasicPath1 != null && refSynStyle1 != 0) {
           refBasicIDs1 = BasicIDFile.read(refBasicPath1, true).stream()
                   .filter(id -> components.contains(id.getSacComponent()) && events.contains(id.getGlobalCMTID()))
                   .collect(Collectors.toList());
       }
       if (refBasicPath2 != null && refSynStyle2 != 0) {
           refBasicIDs2 = BasicIDFile.read(refBasicPath2, true).stream()
                   .filter(id -> components.contains(id.getSacComponent()) && events.contains(id.getGlobalCMTID()))
                   .collect(Collectors.toList());
       }

       Path outPath = DatasetAid.createOutputFolder(workPath, "binStack", folderTag, GadgetAid.getTemporaryString());
       property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

       try {
           // set up taup_time tool
           if (alignPhases != null || displayPhases != null) {
               timeTool = new TauP_Time(structureName);
           }

           for (GlobalCMTID event : events) {

               // set event to taup_time tool
               // The same instance is reused for all observers because computation takes time when changing source depth (see TauP manual).
               if (alignPhases != null || displayPhases != null) {
                   timeTool.setSourceDepth(event.getEventData().getCmtPosition().getDepth());
               }

               // create plots under outPath
               Path eventPath = outPath.resolve(event.toString());
               Files.createDirectories(eventPath);

               for (SACComponent component : components) {
                   List<BasicID> useIds = mainBasicIDs.stream()
                           .filter(id -> id.getSacComponent().equals(component) && id.getGlobalCMTID().equals(event))
                           .sorted(Comparator.comparing(BasicID::getObserver))
                           .collect(Collectors.toList());

                   String fileNameRoot = "binStack_" + component.toString();

                   Plotter plotter = new Plotter(eventPath, useIds, component, fileNameRoot);
                   plotter.plot();
               }
           }

       } catch (TauModelException e) {
           e.printStackTrace();
       }
   }

    private class Plotter {
        private final Path eventPath;
        private final List<BasicID> ids;
        private final SACComponent component;
        private final String fileNameRoot;

        private GnuplotFile gnuplot;
        private double obsMeanMax;
        private double synMeanMax;

        /**
         * @param eventDir
         * @param ids (BasicID[]) BasicIDs to be plotted. All must be of the same event and component.
         * @param fileNameRoot
         */
        private Plotter(Path eventPath, List<BasicID> ids, SACComponent component, String fileNameRoot) {
            this.eventPath = eventPath;
            this.ids = ids;
            this.component = component;
            this.fileNameRoot = fileNameRoot;
        }

        private void plot() throws IOException, TauModelException {
            if (ids.size() == 0) {
                return;
            }

            // prepare IDs
            BasicIDPairUp pairer = new BasicIDPairUp(ids);
            List<BasicID> obsList = pairer.getObsList();
            List<BasicID> mainSynList = pairer.getSynList();

            // calculate the average of the maximum amplitudes of waveforms
            obsMeanMax = obsList.stream().collect(Collectors.averagingDouble(id -> new ArrayRealVector(id.getData()).getLInfNorm()));
            synMeanMax = mainSynList.stream().collect(Collectors.averagingDouble(id -> new ArrayRealVector(id.getData()).getLInfNorm()));

            // create array to insert stacked waveforms
            Trace[] obsStacks;
            Trace[] mainSynStacks;
            Trace[] refSynStacks1;
            Trace[] refSynStacks2;
            if (!byAzimuth) {
                obsStacks = new Trace[(int) Math.ceil(180 / binWidth)];
                mainSynStacks = new Trace[(int) Math.ceil(180 / binWidth)];
                refSynStacks1 = new Trace[(int) Math.ceil(180 / binWidth)];
                refSynStacks2 = new Trace[(int) Math.ceil(180 / binWidth)];
            } else {
                obsStacks = new Trace[(int) Math.ceil(360 / binWidth)];
                mainSynStacks = new Trace[(int) Math.ceil(360 / binWidth)];
                refSynStacks1 = new Trace[(int) Math.ceil(360 / binWidth)];
                refSynStacks2 = new Trace[(int) Math.ceil(360 / binWidth)];
            }

            // variables to find the minimum and maximum distance for this event
            double minTime = Double.MAX_VALUE;
            double maxTime = -Double.MAX_VALUE;
            double minDistance = Double.MAX_VALUE;
            double maxDistance = -Double.MAX_VALUE;

            // for each pair of observed and synthetic waveforms
            for (int i = 0; i < obsList.size(); i++) {
                BasicID obsID = obsList.get(i);
                BasicID mainSynID = mainSynList.get(i);

                double distance = obsID.getGlobalCMTID().getEventData().getCmtPosition()
                        .computeEpicentralDistanceDeg(obsID.getObserver().getPosition());
                double azimuth = obsID.getGlobalCMTID().getEventData().getCmtPosition()
                        .computeAzimuthDeg(obsID.getObserver().getPosition());

                // skip waveform if distance or azimuth is out of bounds
                if (distance < lowerDistance || upperDistance < distance
                        || MathAid.checkAngleRange(azimuth, lowerAzimuth, upperAzimuth) == false) {
                    continue;
                }

                // compute reduce time by distance or phase travel time
                double reduceTime = 0;
                if (alignPhases != null) {
                    timeTool.setPhaseNames(alignPhases);
                    timeTool.calculate(distance);
                    if (timeTool.getNumArrivals() < 1) {
                        System.err.println("Could not get arrival time of " + String.join(",", alignPhases) + " for " + obsID + " , skipping.");
                        return;
                    }
                    reduceTime = timeTool.getArrival(0).getTime();
                } else {
                    reduceTime = reductionSlowness * distance;
                }

                // update ranges
                double startTime = mainSynID.getStartTime() - reduceTime;
                double endTime = mainSynID.getStartTime() + mainSynID.getNpts() / mainSynID.getSamplingHz() - reduceTime;
                if (startTime < minTime) minTime = startTime;
                if (endTime > maxTime) maxTime = endTime;
                if (distance < minDistance) minDistance = distance;
                if (distance > maxDistance) maxDistance = distance;

                // decide which bin to add waveform to
                int k;
                if (!byAzimuth) {
                    k = (int) Math.floor(distance / binWidth);
                } else {
                    k = (int) Math.floor(azimuth / binWidth);
                }

                //~add waveform
                // observed
                // Time shift of static correction shall be applied to the observed waveform.
                Trace obsTrace = obsID.toTrace().withXAs(mainSynID.toTrace().getX());
                obsStacks[k] = addUponShift(obsStacks[k], obsTrace, reduceTime);
                // main synthetic
                mainSynStacks[k] = addUponShift(mainSynStacks[k], mainSynID.toTrace(), reduceTime);
                // reference synthetic 1
                if (refBasicIDs1 != null) {
                    List<BasicID> refSynIDCandidates1 = refBasicIDs1.stream()
                            .filter(id -> BasicID.isPair(id, obsID)).collect(Collectors.toList());
                    if (refSynIDCandidates1.size() != 1)
                        throw new IllegalStateException("0 or more than 1 refSynID1 matching obsID" + obsID.toString());
                    BasicID refSynID1 = refSynIDCandidates1.get(0);
                    refSynStacks1[k] = addUponShift(refSynStacks1[k], refSynID1.toTrace(), reduceTime);
                }
                // reference synthetic 2
                if (refBasicIDs2 != null) {
                    List<BasicID> refSynIDCandidates2 = refBasicIDs2.stream()
                            .filter(id -> BasicID.isPair(id, obsID)).collect(Collectors.toList());
                    if (refSynIDCandidates2.size() != 1)
                        throw new IllegalStateException("0 or more than 1 refSynID2 matching obsID" + obsID.toString());
                    BasicID refSynID2 = refSynIDCandidates2.get(0);
                    refSynStacks2[k] = addUponShift(refSynStacks2[k], refSynID2.toTrace(), reduceTime);
                }
            }

            binStackPlotSetup();

            // plot for each bin
            for (int j = 0; j < obsStacks.length; j++) {
                if (obsStacks[j] != null && mainSynStacks[j] != null) {
                    binStackPlotContent(obsStacks[j], mainSynStacks[j], refSynStacks1[j], refSynStacks2[j], (j + 0.5) * binWidth);
                }
            }

            // set ranges
            if (minDistance > maxDistance || minTime > maxTime) return;
            int startDistance = (int) Math.floor(minDistance / GRAPH_SIZE_INTERVAL) * GRAPH_SIZE_INTERVAL - Y_AXIS_RIM;
            int endDistance = (int) Math.ceil(maxDistance / GRAPH_SIZE_INTERVAL) * GRAPH_SIZE_INTERVAL + Y_AXIS_RIM;
            gnuplot.setCommonYrange(startDistance, endDistance);
            gnuplot.setCommonXrange(minTime - TIME_RIM, maxTime + TIME_RIM);

            // add travel time curves
            if (displayPhases != null) {
                plotTravelTimeCurve(startDistance, endDistance);
            }

            gnuplot.write();
            if (!gnuplot.execute()) System.err.println("gnuplot failed!!");
        }

        private void binStackPlotSetup() {

            gnuplot = new GnuplotFile(eventPath.resolve(fileNameRoot + ".plt"));

            gnuplot.setOutput("pdf", fileNameRoot + ".pdf", 21, 29.7, true);
            gnuplot.setMarginH(15, 10);
            gnuplot.setMarginV(15, 15);
            gnuplot.setFont("Arial", 20, 15, 15, 15, 10);
            gnuplot.unsetCommonKey();

            gnuplot.setCommonTitle(eventPath.getFileName().toString());
            if (alignPhases != null) {
                gnuplot.setCommonXlabel("Time aligned on " + String.join(",", alignPhases) + "-wave arrival (s)");
            } else {
                gnuplot.setCommonXlabel("Reduced time (T - " + reductionSlowness + " Δ) (s)");
            }
            if (!byAzimuth) {
                gnuplot.setCommonYlabel("Distance (deg)");
            } else {
                gnuplot.setCommonYlabel("Azimuth (deg)");
            }
        }

        private void binStackPlotContent(Trace obsStack, Trace mainSynStack, Trace refSynStack1, Trace refSynStack2, double y) throws IOException {
            String fileName = y + "." + component + ".txt";
            outputBinStackTxt(obsStack, mainSynStack, refSynStack1, refSynStack2, fileName);

            double obsMax = obsStack.getYVector().getLInfNorm();
            double synMax = mainSynStack.getYVector().getLInfNorm();
            double obsAmp = BasicPlotAid.selectAmp(obsAmpStyle, ampScale, obsMax, synMax, obsMeanMax, synMeanMax);
            double synAmp = BasicPlotAid.selectAmp(synAmpStyle, ampScale, obsMax, synMax, obsMeanMax, synMeanMax);

            if (byAzimuth == true && flipAzimuth == true && 180 <= y) {
                y -= 360;
            }

            String obsUsingString = String.format("1:($2/%.3e+%.2f)", obsAmp, y);
            gnuplot.addLine(fileName, obsUsingString, BasicPlotAid.SHIFTED_APPEARANCE, "observed");
            if (mainSynStyle != 0) {
                String mainSynUsingString = String.format("1:($3/%.3e+%.2f)", synAmp, y);
                gnuplot.addLine(fileName, mainSynUsingString, BasicPlotAid.switchSyntheticAppearance(mainSynStyle), mainSynName);
            }
            if (refSynStyle1 != 0) {
                String refSynUsingString1 = String.format("1:($4/%.3e+%.2f)", synAmp, y);
                gnuplot.addLine(fileName, refSynUsingString1, BasicPlotAid.switchSyntheticAppearance(refSynStyle1), refSynName1);
            }
            if (refSynStyle2 != 0) {
                String refSynUsingString2 = String.format("1:($5/%.3e+%.2f)", synAmp, y);
                gnuplot.addLine(fileName, refSynUsingString2, BasicPlotAid.switchSyntheticAppearance(refSynStyle2), refSynName2);
            }
        }

        /**
         * Outputs a text file including stacked waveform.
         * <ul>
         * <li> column 1: time </li>
         * <li> column 2: obs </li>
         * <li> column 3: main syn </li>
         * <li> column 4: ref syn 1 </li>
         * <li> column 5: ref syn 2 </li>
         * </ul>
         *
         * @param obsStack
         * @param mainSynStack
         * @param refSynStack1
         * @param refSynStack2
         * @param fileName
         * @throws IOException
         */
        private void outputBinStackTxt(Trace obsStack, Trace mainSynStack,
                Trace refSynStack1, Trace refSynStack2, String fileName) throws IOException {
            Path outputPath = eventPath.resolve(fileName);

            try (PrintWriter pwTrace = new PrintWriter(Files.newBufferedWriter(outputPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))){
                for (int j = 0; j < obsStack.getLength(); j++) {
                    double time = obsStack.getXAt(j);

                    if (mainSynStack.getXAt(j) != time
                            || (refSynStack1 != null && refSynStack1.getXAt(j) != time)
                            || (refSynStack2 != null && refSynStack2.getXAt(j) != time))
                        throw new IllegalStateException("x values do not match");

                    String line = time + " " + obsStack.getYAt(j) + " " + mainSynStack.getYAt(j)
                            + " " + (refSynStack1 != null ? refSynStack1.getYAt(j) : 0)
                            + " " + (refSynStack2 != null ? refSynStack2.getYAt(j) : 0);
                    pwTrace.println(line);
                }
            }
        }

        /**
         * Shifts time of dataTrace by reductionTime, then adds the waveform onto sumTrace.
         * @param sumTrace ({@link Trace}) Summing-up waveform. X axis is reduced time.
         *                                 May be null when calling this method with the first data waveform.
         * @param dataTrace ({@link Trace}) Data waveform. X axis is time from event.
         * @param reductionTime (double) Time to reduce from the time of data waveform
         * @return ({@link Trace}) New Trace instance with added waveform values. X axis is reduced time.
         */
        private Trace addUponShift(Trace sumTrace, Trace dataTrace, double reductionTime) {
            // shift x values by approximately the reduction time so that x values become multiples of samplingStep
            double startTime = dataTrace.getXAt(0);
            double shiftedTime = startTime - reductionTime;
            double roundedTime = Math.round(shiftedTime / samplingStep) * samplingStep;
            Trace shiftedTrace = dataTrace.shiftX(roundedTime - startTime);

            if (sumTrace == null) {
                return shiftedTrace;

            } else {
                // gather all x values that are contained in at least one of the traces
                double[] newX = DoubleStream.concat(Arrays.stream(sumTrace.getX()), Arrays.stream(shiftedTrace.getX()))
                        .distinct().sorted().toArray();
                double[] newY = new double[newX.length];

                for (int i = 0; i < newX.length; i++) {
                    double x = newX[i];
                    double sum = 0;
                    if (sumTrace.getMinX() <= x && x <= sumTrace.getMaxX()) {
                        sum += sumTrace.getYAt(sumTrace.findNearestXIndex(x));
                    }
                    if (shiftedTrace.getMinX() <= x && x <= shiftedTrace.getMaxX()) {
                        sum += shiftedTrace.getYAt(shiftedTrace.findNearestXIndex(x));
                    }
                    newY[i] = sum;
                }
                return new Trace(newX, newY);
            }
        }

        private void plotTravelTimeCurve(double startDistance, double endDistance) throws IOException, TauModelException {
            int iNum = (int) Math.round((endDistance - startDistance) / TRAVEL_TIME_INTERVAL) + 1;

            // set names of all phases to display, and the phase to align if it is specified
            timeTool.setPhaseNames(displayPhases);
            if (alignPhases != null) {
                for (String phase : alignPhases) timeTool.appendPhaseName(phase);
            }

            // calculate travel times and store in arrays
            Double[][] travelTimes = new Double[displayPhases.length][iNum];
            Double[] alignTimes = new Double[iNum];
            for (int i = 0; i < iNum; i++) {
                double distance = startDistance + i * TRAVEL_TIME_INTERVAL;
                timeTool.calculate(distance);
                List<Arrival> arrivals = timeTool.getArrivals();
                for (int p = 0; p < displayPhases.length; p++) {
                    String phase = displayPhases[p];
                    Optional<Arrival> arrivalOpt = arrivals.stream().filter(arrival -> arrival.getPhase().getName().equals(phase)).findFirst();
                    if (arrivalOpt.isPresent()) {
                        travelTimes[p][i] = arrivalOpt.get().getTime();
                    }
                }
                if (alignPhases != null) {
                    List<String> alignPhaseList = Arrays.asList(alignPhases);
                    Optional<Arrival> arrivalOpt = arrivals.stream().filter(arrival -> alignPhaseList.contains(arrival.getPhase().getName())).findFirst();
                    if (arrivalOpt.isPresent()) {
                        alignTimes[i] = arrivalOpt.get().getTime();
                    }
                }
            }

            // output file and add curve
            for (int p = 0; p < displayPhases.length; p++) {
                String phase = displayPhases[p];
                String curveFileName = "curve_" + component + "_" + phase + ".txt";
                Path curvePath = eventPath.resolve(curveFileName);
                boolean wrotePhaseLabel = false;
                try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(curvePath))) {
                    for (int i = 0; i < iNum; i++) {
                        // write only at distances where travel time exists
                        if (travelTimes[p][i] != null) {
                            double distance = startDistance + i * TRAVEL_TIME_INTERVAL;
                            // reduce time by alignPhase or reductionSlowness
                            if (alignPhases != null) {
                                // write only at distances where travel time of alignPhase exists
                                if (alignTimes[i] != null) {
                                    pw.println(distance + " " + (travelTimes[p][i] - alignTimes[i]));
                                }
                                // add label at first appearance
                                if (wrotePhaseLabel == false) {
                                    gnuplot.addLabel(phase, "first", travelTimes[p][i] - alignTimes[i], distance, GnuplotColorName.turquoise);
                                    wrotePhaseLabel = true;
                                }
                            } else {
                                double reduceTime = reductionSlowness * distance;
                                pw.println(distance + " " + (travelTimes[p][i] - reduceTime));
                                // add label at first appearance
                                if (wrotePhaseLabel == false) {
                                    gnuplot.addLabel(phase, "first", travelTimes[p][i] - reduceTime, distance, GnuplotColorName.turquoise);
                                    wrotePhaseLabel = true;
                                }
                            }
                        }
                    }
                }
                gnuplot.addLine(curveFileName, 2, 1, BasicPlotAid.USE_PHASE_APPEARANCE, phase);
            }
        }
    }

}
