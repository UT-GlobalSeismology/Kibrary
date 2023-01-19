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

    private final Property property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String fileTag;
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
    private double binWidth;
    private AmpStyle obsAmpStyle;
    private AmpStyle synAmpStyle;
    private double ampScale;

    private boolean byAzimuth;
    private boolean flipAzimuth;
    /**
     * Name of phase to align the record section
     */
    private String alignPhase;
    /**
     * apparent velocity to use when reducing time [s/deg]
     */
    private double reductionSlowness;

    private double lowerDistance;
    private double upperDistance;
    private double lowerAzimuth;
    private double upperAzimuth;

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
            pw.println("##(String) A tag to include in output file names. If no tag is needed, set this unset.");
            pw.println("#fileTag ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##Path of a basic ID file, must be defined");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##Path of a basic waveform file, must be defined");
            pw.println("#basicPath actual.dat");
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
            pw.println("##Phase name to use for alignment. When unset, the following reductionSlowness will be used.");
            pw.println("#alignPhase ");
            pw.println("##(double) The apparent slowness to use for time reduction [s/deg] (0)");
            pw.println("#reductionSlowness ");
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

    public BasicBinnedStackCreator(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        basicIDPath = property.parsePath("basicIDPath", null, true, workPath);
        basicPath = property.parsePath("basicPath", null, true, workPath);

        if (property.containsKey("tendEvents")) {
            tendEvents = Arrays.stream(property.parseStringArray("tendEvents", null)).map(GlobalCMTID::new)
                    .collect(Collectors.toSet());
        }

        binWidth = property.parseDouble("binWidth", "1.0");
        obsAmpStyle = AmpStyle.valueOf(property.parseString("obsAmpStyle", "synEach"));
        synAmpStyle = AmpStyle.valueOf(property.parseString("synAmpStyle", "synEach"));
        ampScale = property.parseDouble("ampScale", "1.0");

        byAzimuth = property.parseBoolean("byAzimuth", "false");
        flipAzimuth = property.parseBoolean("flipAzimuth", "false");
        reductionSlowness = property.parseDouble("reductionSlowness", "0");

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
       List<BasicID> ids = BasicIDFile.readAsList(basicIDPath, basicPath);

       // get all events included in basicIDs
       Set<GlobalCMTID> allEvents =ids.stream().filter(id -> components.contains(id.getSacComponent()))
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

       for (EventFolder eventDir : eventDirs) {
           // create event directory if it does not exist
           Files.createDirectories(eventDir.toPath());

           for (SACComponent component : components) {
               List<BasicID> useIds = ids.stream().filter(id -> id.getGlobalCMTID().equals(eventDir.getGlobalCMTID())
                       && id.getSacComponent().equals(component))
                       .sorted(Comparator.comparing(BasicID::getObserver))
                       .collect(Collectors.toList());

               String fileNameRoot;
               if (fileTag == null) {
                   fileNameRoot = eventDir.toString() + "_" + component.toString();
               } else {
                   fileNameRoot = fileTag + "_" + eventDir.toString() + "_" + component.toString();
               }

               Plotter plotter = new Plotter(eventDir, useIds, fileNameRoot);
               plotter.plot();
           }
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
        private List<BasicID> ids;
        private String fileNameRoot;

        private GnuplotFile binStackPlot;
        private double obsMeanMax;
        private double synMeanMax;

        /**
         * @param eventDir
         * @param ids (BasicID[]) BasicIDs to be plotted. All must be of the same event and component.
         * @param fileNameRoot
         */
        private Plotter(EventFolder eventDir, List<BasicID> ids, String fileNameRoot) {
            this.eventDir = eventDir;
            this.ids = ids;
            this.fileNameRoot = fileNameRoot;
        }

        public void plot() throws IOException {
            if (ids.size() == 0) {
                return;
            }

            BasicIDPairUp pairer = new BasicIDPairUp(ids);
            List<BasicID> obsList = pairer.getObsList();
            List<BasicID> synList = pairer.getSynList();

            // create array to insert stacked waveforms
            RealVector[] obsStacks;
            RealVector[] synStacks;
            if (!byAzimuth) {
                obsStacks = new ArrayRealVector[(int) Math.ceil(180 / binWidth)];
                synStacks = new ArrayRealVector[(int) Math.ceil(180 / binWidth)];
            } else {
                obsStacks = new ArrayRealVector[(int) Math.ceil(360 / binWidth)];
                synStacks = new ArrayRealVector[(int) Math.ceil(360 / binWidth)];
            }

            // calculate the average of the maximum amplitudes of waveforms
            obsMeanMax = obsList.stream().collect(Collectors.averagingDouble(id -> new ArrayRealVector(id.getData()).getLInfNorm()));
            synMeanMax = synList.stream().collect(Collectors.averagingDouble(id -> new ArrayRealVector(id.getData()).getLInfNorm()));

            // for each pair of observed and synthetic waveforms
            for (int i = 0; i < obsList.size(); i++) {
                BasicID obsID = obsList.get(i);
                BasicID synID = synList.get(i);

                double distance = obsID.getGlobalCMTID().getEventData().getCmtPosition()
                        .computeEpicentralDistance(obsID.getObserver().getPosition()) * 180. / Math.PI;
                double azimuth = obsID.getGlobalCMTID().getEventData().getCmtPosition()
                        .computeAzimuth(obsID.getObserver().getPosition()) * 180. / Math.PI;

                // skip waveform if distance or azimuth is out of bounds
                if (distance < lowerDistance || upperDistance < distance
                        || MathAid.checkAngleRange(azimuth, lowerAzimuth, upperAzimuth) == false) {
                    continue;
                }

                RealVector obsDataVector = new ArrayRealVector(obsID.getData());
                RealVector synDataVector = new ArrayRealVector(synID.getData());

                int k;
                if (!byAzimuth) {
                    k = (int) Math.floor(distance / binWidth);
                } else {
                    k = (int) Math.floor(azimuth / binWidth);
                }
                obsStacks[k] = (obsStacks[k] == null ? obsDataVector : add(obsStacks[k], obsDataVector));
                synStacks[k] = (synStacks[k] == null ? synDataVector : add(synStacks[k], synDataVector));
            }

            binStackPlotSetup();

            for (int j = 0; j < obsStacks.length; j++) {
                if (obsStacks[j] != null && synStacks[j] != null) {
                    binStackPlotContent(obsStacks[j], synStacks[j], (j + 0.5) * binWidth);
                }
            }

            binStackPlot.write();
            if (!binStackPlot.execute()) System.err.println("gnuplot failed!!");
        }

        private void binStackPlotSetup() {
            String binStackFileNameRoot = "binStack_" + fileNameRoot;

            binStackPlot = new GnuplotFile(eventDir.toPath().resolve(binStackFileNameRoot + ".plt"));

            binStackPlot.setOutput("pdf", binStackFileNameRoot + ".pdf", 21, 29.7, true);
            binStackPlot.setMarginH(15, 10);
            binStackPlot.setMarginV(15, 15);
            binStackPlot.setFont("Arial", 20, 15, 15, 15, 10);
            binStackPlot.unsetCommonKey();

            binStackPlot.setCommonTitle(eventDir.toString());
            binStackPlot.setCommonXlabel("Time in window (s)");
            if (!byAzimuth) {
                binStackPlot.setCommonYlabel("Distance (deg)");
            } else {
                binStackPlot.setCommonYlabel("Azimuth (deg)");
            }
        }

        private void binStackPlotContent(RealVector obsStack, RealVector synStack, double y) throws IOException {
            SACComponent component = ids.get(0).getSacComponent();
            double samplingHz = ids.get(0).getSamplingHz();

            String fileName = y + "." + eventDir.toString() + "." + component + ".txt";
            outputBinStackTxt(obsStack, synStack, fileName, samplingHz);

            double obsMax = obsStack.getLInfNorm();
            double synMax = synStack.getLInfNorm();
            double obsAmp = selectAmp(obsAmpStyle, obsMax, synMax, obsMeanMax, synMeanMax);
            double synAmp = selectAmp(synAmpStyle, obsMax, synMax, obsMeanMax, synMeanMax);

            if (byAzimuth == true && flipAzimuth == true && 180 <= y) {
                y -= 360;
            }

            String obsUsingString = String.format("1:($2/%.3e+%.2f)", obsAmp, y);
            String synUsingString = String.format("1:($3/%.3e+%.2f)", synAmp, y);
            binStackPlot.addLine(fileName, obsUsingString, obsAppearance, "observed");
            binStackPlot.addLine(fileName, synUsingString, synAppearance, "synthetic");
        }

        /**
         * Outputs a text file including stacked waveform.
         * Column 1: time ;
         * column 2: obs ;
         * column 3: syn.
         *
         * @param obsStack
         * @param synStack
         * @param fileName
         * @param samplingHz
         * @throws IOException
         */
        private void outputBinStackTxt(RealVector obsStack, RealVector synStack, String fileName, double samplingHz) throws IOException {
            Path outputPath = eventDir.toPath().resolve(fileName);

            try (PrintWriter pwTrace = new PrintWriter(Files.newBufferedWriter(outputPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))){
                for (int j = 0; j < obsStack.getDimension(); j++) {
                    double time = j * samplingHz;
                    pwTrace.println(time + " " + obsStack.getEntry(j) + " " + synStack.getEntry(j));
                }
            }
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

        private RealVector add(RealVector v1, RealVector v2) {
            RealVector res = null;

            if (v1.getDimension() == 0)
                res = v2;
            else if (v2.getDimension() == 0)
                res = v1;
            else
                res = v1.getDimension() > v2.getDimension() ? v2.add(v1.getSubVector(0, v2.getDimension()))
                        : v1.add(v2.getSubVector(0, v1.getDimension()));

            return res;
        }

    }

}
