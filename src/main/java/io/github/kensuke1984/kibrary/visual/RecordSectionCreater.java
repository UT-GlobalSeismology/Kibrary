package io.github.kensuke1984.kibrary.visual;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotColorName;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotLineAppearance;
import io.github.kensuke1984.kibrary.util.DatasetUtils;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.GadgetUtils;
import io.github.kensuke1984.kibrary.util.MathUtils;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;

/**
 * Creates record section for each event included in a BasicID file.
 * A pair of a basic ID file and basic waveform file is required as input.
 * <p>
 * Waveform data for each observer must be written in txt files under event directories.
 * Output pdf files will be created under each of the existing event directories.
 *
 * @author otsuru
 * @since 2021/12/11
 */
public class RecordSectionCreater implements Operation {

    private final Properties property;
    /**
     * Path of the work folder
     */
    private Path workPath;
    /**
     * {@link Path} of a basic ID file
     */
    private Path basicIDPath;
    /**
     * {@link Path} of a basic file
     */
    private Path basicPath;
    /**
     * A tag to include in output file names. When this is empty, no tag is used.
     */
    private String tag;
    /**
     * components to be included in the dataset
     */
    private Set<SACComponent> components;
    /**
     * Events to work for. If this is empty, work for all events in workPath.
     */
    private Set<GlobalCMTID> tendEvents = new HashSet<>();
    private boolean createProfile;
    private boolean createBinStack;
    private double binWidth;
    /**
     * apparent velocity to use when reducing time [s/deg]
     */
    private double reductionSlowness;
    private AmpStyle obsAmpStyle;
    private AmpStyle synAmpStyle;
    private double ampScale;

    private boolean byAzimuth;
    private boolean flipAzimuth;
    private double lowerDistance;
    private double upperDistance;
    private double lowerAzimuth;
    private double upperAzimuth;

    public static void writeDefaultPropertiesFile() throws IOException {
        Path outPath = Property.generatePath(RecordSectionCreater.class);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan RecordSectionCreater");
            pw.println("##Path of a working directory. This must contain event directories with waveform txt files. (.)");
            pw.println("#workPath");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, set this blank.");
            pw.println("#tag");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components");
            pw.println("##Path of a basic ID file, must be defined");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##Path of a basic file, must be defined");
            pw.println("#basicPath actual.dat");
            pw.println("##GlobalCMTIDs of events to work for, listed using spaces. To use all events, set this blank.");
            pw.println("#tendEvents");
            pw.println("##(boolean) Whether to create a profile (true)");
            pw.println("#createProfile");
            pw.println("##(boolean) Whether to create a binned stack (false)");
            pw.println("#createBinStack");
            pw.println("##(double) The width of each bin [deg] (1.0)");
            pw.println("#binWidth");
            pw.println("##(double) The apparent slowness to use for time reduction [s/deg] (0)");
            pw.println("#reductionSlowness");
            pw.println("##Method for standarization of observed waveform amplitude, from [obsEach,synEach,obsMean,synMean] (synEach)");
            pw.println("#obsAmpStyle");
            pw.println("##Method for standarization of synthetic waveform amplitude, from [obsEach,synEach,obsMean,synMean] (synEach)");
            pw.println("#synAmpStyle");
            pw.println("##(double) Coefficient to multiply to all waveforms (1.0)");
            pw.println("#ampScale");
            pw.println("##(boolean) Whether to plot the figure with azimuth as the Y-axis (false)");
            pw.println("#byAzimuth");
            pw.println("##(boolean) Whether to set the azimuth range to [-180:180) instead of [0:360) (false)");
            pw.println("##This is effective when using south-to-north raypaths in byAzimuth mode.");
            pw.println("#flipAzimuth");
            pw.println("##(double) Lower limit of range of epicentral distance to be used [0:upperDistance) (0)");
            pw.println("#lowerDistance");
            pw.println("##(double) Upper limit of range of epicentral distance to be used (lowerDistance:180] (180)");
            pw.println("#upperDistance");
            pw.println("##(double) Lower limit of range of azimuth to be used [-360:upperAzimuth) (0)");
            pw.println("#lowerAzimuth");
            pw.println("##(double) Upper limit of range of azimuth to be used (lowerAzimuth:360] (360)");
            pw.println("#upperAzimuth");
        }
        System.err.println(outPath + " is created.");
    }

    public RecordSectionCreater(Properties property) throws IOException {
        this.property = (Properties) property.clone();
        set();
    }

    private void checkAndPutDefaults() {
        if (!property.containsKey("workPath")) property.setProperty("workPath", ".");
        if (!property.containsKey("tag")) property.setProperty("tag", "");
        if (!property.containsKey("components")) property.setProperty("components", "Z R T");
        if (!property.containsKey("basicIDPath"))
            throw new IllegalArgumentException("There is no information about basicIDPath.");
        if (!property.containsKey("basicPath"))
            throw new IllegalArgumentException("There is no information about basicPath.");

        if (!property.containsKey("tendEvents")) property.setProperty("tendEvents", "");
        if (!property.containsKey("createProfile")) property.setProperty("createProfile", "true");
        if (!property.containsKey("createBinStack")) property.setProperty("createBinStack", "false");
        if (!property.containsKey("binWidth")) property.setProperty("binWidth", "1.0");
        if (!property.containsKey("reductionSlowness")) property.setProperty("reductionSlowness", "0");
        if (!property.containsKey("obsAmpStyle")) property.setProperty("obsAmpStyle", "synEach");
        if (!property.containsKey("synAmpStyle")) property.setProperty("synAmpStyle", "synEach");
        if (!property.containsKey("ampScale")) property.setProperty("ampScale", "1.0");

        if (!property.containsKey("byAzimuth")) property.setProperty("byAzimuth", "false");
        if (!property.containsKey("flipAzimuth")) property.setProperty("flipAzimuth", "false");
        if (!property.containsKey("lowerDistance")) property.setProperty("lowerDistance", "0");
        if (!property.containsKey("upperDistance")) property.setProperty("upperDistance", "180");
        if (!property.containsKey("lowerAzimuth")) property.setProperty("lowerAzimuth", "0");
        if (!property.containsKey("upperAzimuth")) property.setProperty("upperAzimuth", "360");
    }

    private void set() throws IOException {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        if (!Files.exists(workPath)) throw new NoSuchFileException("The workPath " + workPath + " does not exist");
        tag = property.getProperty("tag");

        components = Arrays.stream(property.getProperty("components").split("\\s+")).map(SACComponent::valueOf)
                .collect(Collectors.toSet());
        basicIDPath = getPath("basicIDPath");
        if (!Files.exists(basicIDPath))
            throw new NoSuchFileException("The basic ID file " + basicIDPath + " does not exist");
        basicPath = getPath("basicPath");
        if (!Files.exists(basicPath))
            throw new NoSuchFileException("The basic file " + basicPath + " does not exist");

        if (!property.getProperty("tendEvents").isEmpty()) {
            tendEvents = Arrays.stream(property.getProperty("tendEvents").split("\\s+")).map(GlobalCMTID::new)
                    .collect(Collectors.toSet());
        }

        createProfile = Boolean.parseBoolean(property.getProperty("createProfile"));
        createBinStack = Boolean.parseBoolean(property.getProperty("createBinStack"));
        binWidth = Double.parseDouble(property.getProperty("binWidth"));
        reductionSlowness = Double.parseDouble(property.getProperty("reductionSlowness"));
        obsAmpStyle = AmpStyle.valueOf(property.getProperty("obsAmpStyle"));
        synAmpStyle = AmpStyle.valueOf(property.getProperty("synAmpStyle"));
        ampScale = Double.parseDouble(property.getProperty("ampScale"));

        byAzimuth = Boolean.parseBoolean(property.getProperty("byAzimuth"));
        flipAzimuth = Boolean.parseBoolean(property.getProperty("flipAzimuth"));
        lowerDistance = Double.parseDouble(property.getProperty("lowerDistance"));
        upperDistance = Double.parseDouble(property.getProperty("upperDistance"));
        lowerAzimuth = Double.parseDouble(property.getProperty("lowerAzimuth"));
        upperAzimuth = Double.parseDouble(property.getProperty("upperAzimuth"));
    }

   /**
    *
    * @param args [a property file name]
    * @throws Exception if any
    */
   public static void main(String[] args) throws IOException {
       RecordSectionCreater rsc = new RecordSectionCreater(Property.parse(args));
       long startTime = System.nanoTime();
       System.err.println(RecordSectionCreater.class.getName() + " is operating.");
       rsc.run();
       System.err.println(RecordSectionCreater.class.getName() + " finished in "
               + GadgetUtils.toTimeString(System.nanoTime() - startTime));
   }

   @Override
   public void run() throws IOException {
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
       if (!DatasetUtils.checkEventNum(eventDirs.size())) {
           return;
       }

       for (EventFolder eventDir : eventDirs) {
           // create event directory if it does not exist
           Files.createDirectories(eventDir.toPath());

           for (SACComponent component : components) {
               BasicID[] useIds = Arrays.stream(ids).filter(id -> id.getGlobalCMTID().equals(eventDir.getGlobalCMTID())
                       && id.getSacComponent().equals(component))
                       .sorted(Comparator.comparing(BasicID::getObserver))
                       .toArray(BasicID[]::new);
                       //.collect(Collectors.toList()).toArray(new BasicID[0]);

               String fileNameRoot;
               if (tag.isEmpty()) {
                   fileNameRoot = eventDir.toString() + "_" + component.toString();
               }
               else {
                   fileNameRoot = tag + "_" + eventDir.toString() + "_" + component.toString();
               }

               Plotter plotter = new Plotter(eventDir, useIds, fileNameRoot);
               plotter.plot();
           }
       }
   }

    @Override
    public Properties getProperties() {
        return (Properties) property.clone();
    }

    @Override
    public Path getWorkPath() {
        return workPath;
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
        private GnuplotFile binStackPlot;
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

        public void plot() throws IOException {
            if (ids.length == 0) {
                return;
            }

            List<BasicID> obsList = new ArrayList<>();
            List<BasicID> synList = new ArrayList<>();
            BasicIDFile.pairUp(ids, obsList, synList);

            if (createProfile) {
                profilePlotSetup();
            }

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

                double distance = obsID.getGlobalCMTID().getEvent().getCmtLocation()
                        .getEpicentralDistance(obsID.getObserver().getPosition()) * 180. / Math.PI;
                double azimuth = obsID.getGlobalCMTID().getEvent().getCmtLocation()
                        .getAzimuth(obsID.getObserver().getPosition()) * 180. / Math.PI;

                // skip waveform if distance or azimuth is out of bounds
                if (distance < lowerDistance || upperDistance < distance
                        || MathUtils.checkAngleRange(azimuth, lowerAzimuth, upperAzimuth) == false) {
                    continue;
                }

                RealVector obsDataVector = new ArrayRealVector(obsID.getData());
                RealVector synDataVector = new ArrayRealVector(synID.getData());

                if (createProfile) {
                    // in flipAzimuth mode, change azimuth range from [0:360) to [-180:180)
                    if (flipAzimuth == true && 180 <= azimuth) {
                        profilePlotContent(obsID, synID, obsDataVector, synDataVector, distance, azimuth - 360);
                    } else {
                        profilePlotContent(obsID, synID, obsDataVector, synDataVector, distance, azimuth);
                    }
                }

                if (createBinStack) {
                    int k;
                    if (!byAzimuth) {
                        k = (int) Math.floor(distance / binWidth);
                    } else {
                        k = (int) Math.floor(azimuth / binWidth);
                    }
                    obsStacks[k] = (obsStacks[k] == null ? obsDataVector : add(obsStacks[k], obsDataVector));
                    synStacks[k] = (synStacks[k] == null ? synDataVector : add(synStacks[k], synDataVector));
                }
            }

            if (createProfile) {
                profilePlot.write();
                if (!profilePlot.execute(eventDir.toPath())) System.err.println("gnuplot failed!!");
            }

            if (createBinStack) {
                binStackPlotSetup();

                for (int j = 0; j < obsStacks.length; j++) {
                    if (obsStacks[j] != null && synStacks[j] != null) {
                        binStackPlotContent(obsStacks[j], synStacks[j], (j + 0.5) * binWidth);
                    }
                }

                binStackPlot.write();
                if (!binStackPlot.execute(eventDir.toPath())) System.err.println("gnuplot failed!!");
            }

        }

        private void profilePlotSetup() {
            String profileFileNameRoot = "profile_" + fileNameRoot;

            profilePlot = new GnuplotFile(eventDir.toPath().resolve(profileFileNameRoot + ".plt"));

            profilePlot.setOutput("pdf", profileFileNameRoot + ".pdf", 21, 29.7, true);
            profilePlot.setMarginH(15, 25);
            profilePlot.setMarginV(15, 15);
            profilePlot.setFont("Arial", 20, 15, 15, 15, 10);
            profilePlot.unsetKey();

            profilePlot.setTitle(eventDir.toString());
//            gnuplot.setXlabel("Time aligned on S-wave arrival (s)"); //TODO
            profilePlot.setXlabel("Reduced time (T - " + reductionSlowness + " Δ) (s)");
            if (!byAzimuth) {
                profilePlot.setYlabel("Distance (deg)");
                profilePlot.addLabel("station network azimuth", "graph", 1.0, 1.0);
            } else {
                profilePlot.setYlabel("Azimuth (deg)");
                profilePlot.addLabel("station network distance", "graph", 1.0, 1.0);
            }
        }

        private void binStackPlotSetup() {
            String binStackFileNameRoot = "binStack_" + fileNameRoot;

            binStackPlot = new GnuplotFile(eventDir.toPath().resolve(binStackFileNameRoot + ".plt"));

            binStackPlot.setOutput("pdf", binStackFileNameRoot + ".pdf", 21, 29.7, true);
            binStackPlot.setMarginH(15, 10);
            binStackPlot.setMarginV(15, 15);
            binStackPlot.setFont("Arial", 20, 15, 15, 15, 10);
            binStackPlot.unsetKey();

            binStackPlot.setTitle(eventDir.toString());
            binStackPlot.setXlabel("Time in window (s)");
            if (!byAzimuth) {
                binStackPlot.setYlabel("Distance (deg)");
            } else {
                binStackPlot.setYlabel("Azimuth (deg)");
            }
        }

        private void profilePlotContent(BasicID obsID, BasicID synID, RealVector obsDataVector, RealVector synDataVector,
                double distance, double azimuth) throws IOException {

            // output waveform data to text file if it has not already been done so
            String filename = BasicIDFile.getWaveformTxtFileName(obsID);
            if (!Files.exists(eventDir.toPath().resolve(filename))) {
                BasicIDFile.outputWaveformTxt(eventDir.toPath(), obsID, synID);
            }

            // decide the coefficient to amplify each waveform
            double obsMax = obsDataVector.getLInfNorm();
            double synMax = synDataVector.getLInfNorm();
            double obsAmp = selectAmp(obsAmpStyle, obsMax, synMax, obsMeanMax, synMeanMax);
            double synAmp = selectAmp(synAmpStyle, obsMax, synMax, obsMeanMax, synMeanMax);

            // Set "using" part. For x values, reduce time by distance. For y values, add either distance or azimuth.
            String obsUsingString;
            String synUsingString;
            if (!byAzimuth) {
                profilePlot.addLabel(obsID.getObserver().toPaddedString() + " " + MathUtils.padToString(azimuth, 3, 2),
                        "graph", 1.01, "first", distance);
                obsUsingString = String.format("($3-%.3f*%.2f):($2/%.3e+%.2f) ", reductionSlowness, distance, obsAmp, distance);
                synUsingString = String.format("($3-%.3f*%.2f):($4/%.3e+%.2f) ", reductionSlowness, distance, synAmp, distance);
            } else {
                profilePlot.addLabel(obsID.getObserver().toPaddedString() + " " + MathUtils.padToString(distance, 3, 2),
                        "graph", 1.01, "first", azimuth);
                obsUsingString = String.format("($3-%.3f*%.2f):($2/%.3e+%.2f) ", reductionSlowness, distance, obsAmp, azimuth);
                synUsingString = String.format("($3-%.3f*%.2f):($4/%.3e+%.2f) ", reductionSlowness, distance, synAmp, azimuth);
            }
            profilePlot.addLine(filename, obsUsingString, obsAppearance, "observed");
            profilePlot.addLine(filename, synUsingString, synAppearance, "synthetic");
        }

        private void binStackPlotContent(RealVector obsStack, RealVector synStack, double y) throws IOException {
            SACComponent component = ids[0].getSacComponent();
            double samplingHz = ids[0].getSamplingHz();

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

        private double selectAmp (AmpStyle style, double obsEachMax, double synEachMax, double obsMeanMax, double synMeanMax) {
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
