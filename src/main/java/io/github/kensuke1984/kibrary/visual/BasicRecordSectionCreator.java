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
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Time;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotColorName;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotLineAppearance;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.BasicIDPairUp;

/**
 * Creates record section for each event included in a {@link BasicIDFile}.
 * Time-shift is applied to observed waveform when being plotted.
 * <p>
 * A pair of a basic ID file and basic waveform file is required as input.
 * <p>
 * Event directories will be created under workPath if they do not already exist.
 * Output files will be created under these event directories.
 * If text files of waveform data for each observer exist under event directories, they will be reused;
 * if not, they will be generated using {@link BasicIDFile}.
 * Rewriting should not be needed because the content of these files should always be the same.
 * <p>
 * Output pdf files and their corresponding plt files will be created under each of the event directories.
 *
 * @author otsuru
 * @since 2021/12/11
 * @version 2022/7/27 renamed from visual.RecordSectionCreater to visual.BasicRecordSectionCreator and separated visual.BasicBinnedStackCreator
 */
public class BasicRecordSectionCreator extends Operation {

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
     * components to be included in the dataset
     */
    private Set<SACComponent> components;

    /**
     * {@link Path} of a basic ID file
     */
    private Path basicIDPath;
    /**
     * {@link Path} of a basic file
     */
    private Path basicPath;

    /**
     * Events to work for. If this is empty, work for all events in workPath.
     */
    private Set<GlobalCMTID> tendEvents = new HashSet<>();
    private AmpStyle obsAmpStyle;
    private AmpStyle synAmpStyle;
    private double ampScale;

    private boolean byAzimuth;
    private boolean flipAzimuth;
    /**
     * Names of phases to plot travel time curves
     */
    private String[] displayPhases;
    /**
     * Name of phase to align the record section
     */
    private String alignPhase;
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
            pw.println("##(String) A tag to include in output file names. If no tag is needed, set this blank.");
            pw.println("#tag ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a basic ID file, must be defined");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##Path of a basic waveform file, must be defined");
            pw.println("#basicPath actual.dat");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces. To use all events, leave this unset.");
            pw.println("#tendEvents ");
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
            pw.println("##Phase name to use for alignment. When unset, the following reductionSlowness will be used.");
            pw.println("#alignPhase ");
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
        }
        System.err.println(outPath + " is created.");
    }

    public BasicRecordSectionCreator(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("tag")) tag = property.parseStringSingle("tag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        basicIDPath = property.parsePath("basicIDPath", null, true, workPath);
        basicPath = property.parsePath("basicPath", null, true, workPath);

        if (property.containsKey("tendEvents")) {
            tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                    .collect(Collectors.toSet());
        }

        obsAmpStyle = AmpStyle.valueOf(property.parseString("obsAmpStyle", "synEach"));
        synAmpStyle = AmpStyle.valueOf(property.parseString("synAmpStyle", "synEach"));
        ampScale = property.parseDouble("ampScale", "1.0");

        byAzimuth = property.parseBoolean("byAzimuth", "false");
        flipAzimuth = property.parseBoolean("flipAzimuth", "false");

        if (property.containsKey("displayPhases") && byAzimuth == false)
            displayPhases = property.parseStringArray("displayPhases", null);
        if (property.containsKey("alignPhase"))
            alignPhase = property.parseString("alignPhase", null);
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
    }

   @Override
   public void run() throws IOException {
       try {
           BasicID[] ids = BasicIDFile.read(basicIDPath, basicPath);

           // get all events included in basicIDs
           Set<GlobalCMTID> allEvents = Arrays.stream(ids).filter(id -> components.contains(id.getSacComponent()))
                   .map(id -> id.getGlobalCMTID()).distinct().collect(Collectors.toSet());
           // eventDirs of events to be used
           Set<EventFolder> eventDirs;
           if (tendEvents.isEmpty()) {
               eventDirs = allEvents.stream()
                       .map(event -> new EventFolder(workPath.resolve(event.toString()))).collect(Collectors.toSet());
           } else {
               // choose only events that are included in tendEvents
               eventDirs = allEvents.stream().filter(event -> tendEvents.contains(event))
                       .map(event -> new EventFolder(workPath.resolve(event.toString()))).collect(Collectors.toSet());
           }
           if (!DatasetAid.checkNum(eventDirs.size(), "event", "events")) {
               return;
           }

           // set up taup_time tool
           if (alignPhase != null || displayPhases != null) {
               timeTool = new TauP_Time(structureName);
               timeTool.parsePhaseList(alignPhase);
           }

           for (EventFolder eventDir : eventDirs) {
               // create event directory if it does not exist
               Files.createDirectories(eventDir.toPath());

               // set event to taup_time tool
               if (alignPhase != null || displayPhases != null) {
                   timeTool.setSourceDepth(eventDir.getGlobalCMTID().getEventData().getCmtLocation().getDepth());
               }

               for (SACComponent component : components) {
                   BasicID[] useIds = Arrays.stream(ids).filter(id -> id.getGlobalCMTID().equals(eventDir.getGlobalCMTID())
                           && id.getSacComponent().equals(component))
                           .sorted(Comparator.comparing(BasicID::getObserver))
                           .toArray(BasicID[]::new);

                   String fileNameRoot;
                   if (tag == null) {
                       fileNameRoot = "profile_" + eventDir.toString() + "_" + component.toString();
                   } else {
                       fileNameRoot = "profile_" + tag + "_" + eventDir.toString() + "_" + component.toString();
                   }

                   Plotter plotter = new Plotter(eventDir, useIds, fileNameRoot);
                   plotter.plot();
               }
           }

       } catch (TauModelException e) {
           e.printStackTrace();
       }
   }

    private static enum AmpStyle {
        obsEach,
        synEach,
        obsMean,
        synMean
    }

    private class Plotter {
        private final GnuplotLineAppearance obsAppearance = new GnuplotLineAppearance(1, GnuplotColorName.black, 1);
        private final GnuplotLineAppearance synAppearance = new GnuplotLineAppearance(1, GnuplotColorName.red, 1);

        private EventFolder eventDir;
        private BasicID[] ids;
        private String fileNameRoot;

        private GnuplotFile profilePlot;
        private double obsMeanMax;
        private double synMeanMax;

        /**
         * @param eventDir
         * @param ids (BasicID[]) BasicIDs to be plotted. All must be of the same event and component.
         * @param fileNameRoot
         */
        private Plotter(EventFolder eventDir, BasicID[] ids, String fileNameRoot) {
            this.eventDir = eventDir;
            this.ids = ids;
            this.fileNameRoot = fileNameRoot;
        }

        public void plot() throws IOException, TauModelException {
            if (ids.length == 0) {
                return;
            }

            BasicIDPairUp pairer = new BasicIDPairUp(ids);
            List<BasicID> obsList = pairer.getObsList();
            List<BasicID> synList = pairer.getSynList();

            profilePlotSetup();

            // calculate the average of the maximum amplitudes of waveforms
            obsMeanMax = obsList.stream().collect(Collectors.averagingDouble(id -> new ArrayRealVector(id.getData()).getLInfNorm()));
            synMeanMax = synList.stream().collect(Collectors.averagingDouble(id -> new ArrayRealVector(id.getData()).getLInfNorm()));

            // for each pair of observed and synthetic waveforms
            for (int i = 0; i < obsList.size(); i++) {
                BasicID obsID = obsList.get(i);
                BasicID synID = synList.get(i);

                double distance = obsID.getGlobalCMTID().getEventData().getCmtLocation()
                        .computeEpicentralDistance(obsID.getObserver().getPosition()) * 180. / Math.PI;
                double azimuth = obsID.getGlobalCMTID().getEventData().getCmtLocation()
                        .computeAzimuth(obsID.getObserver().getPosition()) * 180. / Math.PI;

                // skip waveform if distance or azimuth is out of bounds
                if (distance < lowerDistance || upperDistance < distance
                        || MathAid.checkAngleRange(azimuth, lowerAzimuth, upperAzimuth) == false) {
                    continue;
                }

                // in flipAzimuth mode, change azimuth range from [0:360) to [-180:180)
                if (flipAzimuth == true && 180 <= azimuth) {
                    profilePlotContent(obsID, synID, distance, azimuth - 360);
                } else {
                    profilePlotContent(obsID, synID, distance, azimuth);
                }
            }

            // plot travel times
            if (displayPhases != null) {
                //TODO
            }

            profilePlot.write();
            if (!profilePlot.execute()) System.err.println("gnuplot failed!!");
        }

        private void profilePlotSetup() {
            profilePlot = new GnuplotFile(eventDir.toPath().resolve(fileNameRoot + ".plt"));

            profilePlot.setOutput("pdf", fileNameRoot + ".pdf", 21, 29.7, true);
            profilePlot.setMarginH(15, 25);
            profilePlot.setMarginV(15, 15);
            profilePlot.setFont("Arial", 20, 15, 15, 15, 10);
            profilePlot.unsetKey();

            profilePlot.setTitle(eventDir.toString());
            if (alignPhase != null) {
                profilePlot.setXlabel("Time aligned on " + alignPhase + "-wave arrival (s)");
            } else {
                profilePlot.setXlabel("Reduced time (T - " + reductionSlowness + " Δ) (s)");
            }
            if (!byAzimuth) {
                profilePlot.setYlabel("Distance (deg)");
                profilePlot.addLabel("station network azimuth", "graph", 1.0, 1.0);
            } else {
                profilePlot.setYlabel("Azimuth (deg)");
                profilePlot.addLabel("station network distance", "graph", 1.0, 1.0);
            }
        }

        private void profilePlotContent(BasicID obsID, BasicID synID, double distance, double azimuth)
                throws IOException, TauModelException {

            // output waveform data to text file if it has not already been done so
            String filename = BasicIDFile.getWaveformTxtFileName(obsID);
            if (!Files.exists(eventDir.toPath().resolve(filename))) {
                BasicIDFile.outputWaveformTxt(eventDir.toPath(), obsID, synID);
            }

            // decide the coefficient to amplify each waveform
            RealVector obsDataVector = new ArrayRealVector(obsID.getData());
            RealVector synDataVector = new ArrayRealVector(synID.getData());
            double obsMax = obsDataVector.getLInfNorm();
            double synMax = synDataVector.getLInfNorm();
            double obsAmp = selectAmp(obsAmpStyle, obsMax, synMax, obsMeanMax, synMeanMax);
            double synAmp = selectAmp(synAmpStyle, obsMax, synMax, obsMeanMax, synMeanMax);

            // Compute reduce time by distance or phase travel time.
            double reduceTime = 0;
            if (alignPhase != null) { //TODO use TravelTimeInformationFile instead?
                timeTool.calculate(distance);
                if (timeTool.getNumArrivals() < 1) {
                    System.err.println("Could not get arrival time of " + alignPhase + " for " + obsID + " , skipping.");
                    return;
                }
                reduceTime = timeTool.getArrival(0).getTime();
            } else {
                reduceTime = reductionSlowness * distance;
            }

            // Set "using" part. For x values, reduce time by distance or phase travel time. For y values, add either distance or azimuth.
            String obsUsingString;
            String synUsingString;
            if (!byAzimuth) {
                profilePlot.addLabel(obsID.getObserver().toPaddedString() + " " + MathAid.padToString(azimuth, 3, 2, " "),
                        "graph", 1.01, "first", distance);
                obsUsingString = String.format("($3-%.3f):($2/%.3e+%.2f) ", reduceTime, obsAmp, distance);
                synUsingString = String.format("($3-%.3f):($4/%.3e+%.2f) ", reduceTime, synAmp, distance);
            } else {
                profilePlot.addLabel(obsID.getObserver().toPaddedString() + " " + MathAid.padToString(distance, 3, 2, " "),
                        "graph", 1.01, "first", azimuth);
                obsUsingString = String.format("($3-%.3f):($2/%.3e+%.2f) ", reduceTime, obsAmp, azimuth);
                synUsingString = String.format("($3-%.3f):($4/%.3e+%.2f) ", reduceTime, synAmp, azimuth);
            }

            // add plot
            profilePlot.addLine(filename, obsUsingString, obsAppearance, "observed");
            profilePlot.addLine(filename, synUsingString, synAppearance, "synthetic");
        }

        private double selectAmp(AmpStyle style, double obsEachMax, double synEachMax, double obsMeanMax, double synMeanMax) {
            switch (style) {
            case obsEach:
                return obsEachMax / ampScale;
            case synEach:
                return synEachMax / ampScale;
            case obsMean:
                return obsMeanMax / ampScale;
            case synMean:
                return synMeanMax / ampScale;
            default:
                throw new IllegalArgumentException("Input AmpStyle is unknown.");
            }
        }
    }

}
