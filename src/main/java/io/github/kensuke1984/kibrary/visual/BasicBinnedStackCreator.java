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
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
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

    private int mainSynStyle;
    private String mainSynName;
    private int refSynStyle1;
    private String refSynName1;
    private int refSynStyle2;
    private String refSynName2;

    private List<BasicID> refBasicIDs1;
    private List<BasicID> refBasicIDs2;

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
        reductionSlowness = property.parseDouble("reductionSlowness", "0");

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

       for (GlobalCMTID event : events) {

           // create plots under outPath
           Path eventPath = outPath.resolve(event.toString());
           Files.createDirectories(eventPath);

           for (SACComponent component : components) {
               List<BasicID> useIds = mainBasicIDs.stream()
                       .filter(id -> id.getSacComponent().equals(component) && id.getGlobalCMTID().equals(event))
                       .sorted(Comparator.comparing(BasicID::getObserver))
                       .collect(Collectors.toList());

               String fileNameRoot = "binStack_" + component.toString();

               Plotter plotter = new Plotter(eventPath, useIds, fileNameRoot);
               plotter.plot();
           }
       }
   }

    private class Plotter {
        private Path eventPath;
        private List<BasicID> ids;
        private String fileNameRoot;

        private GnuplotFile gnuplot;
        private double obsMeanMax;
        private double synMeanMax;

        /**
         * @param eventDir
         * @param ids (BasicID[]) BasicIDs to be plotted. All must be of the same event and component.
         * @param fileNameRoot
         */
        private Plotter(Path eventPath, List<BasicID> ids, String fileNameRoot) {
            this.eventPath = eventPath;
            this.ids = ids;
            this.fileNameRoot = fileNameRoot;
        }

        public void plot() throws IOException {
            if (ids.size() == 0) {
                return;
            }

            // prepare IDs
            BasicIDPairUp pairer = new BasicIDPairUp(ids);
            List<BasicID> obsList = pairer.getObsList();
            List<BasicID> mainSynList = pairer.getSynList();

            // create array to insert stacked waveforms
            RealVector[] obsStacks;
            RealVector[] mainSynStacks;
            RealVector[] refSynStacks1;
            RealVector[] refSynStacks2;
            if (!byAzimuth) {
                obsStacks = new ArrayRealVector[(int) Math.ceil(180 / binWidth)];
                mainSynStacks = new ArrayRealVector[(int) Math.ceil(180 / binWidth)];
                refSynStacks1 = new ArrayRealVector[(int) Math.ceil(180 / binWidth)];
                refSynStacks2 = new ArrayRealVector[(int) Math.ceil(180 / binWidth)];
            } else {
                obsStacks = new ArrayRealVector[(int) Math.ceil(360 / binWidth)];
                mainSynStacks = new ArrayRealVector[(int) Math.ceil(360 / binWidth)];
                refSynStacks1 = new ArrayRealVector[(int) Math.ceil(360 / binWidth)];
                refSynStacks2 = new ArrayRealVector[(int) Math.ceil(360 / binWidth)];
            }

            // calculate the average of the maximum amplitudes of waveforms
            obsMeanMax = obsList.stream().collect(Collectors.averagingDouble(id -> new ArrayRealVector(id.getData()).getLInfNorm()));
            synMeanMax = mainSynList.stream().collect(Collectors.averagingDouble(id -> new ArrayRealVector(id.getData()).getLInfNorm()));

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

                // decide which bin to add waveform to
                int k;
                if (!byAzimuth) {
                    k = (int) Math.floor(distance / binWidth);
                } else {
                    k = (int) Math.floor(azimuth / binWidth);
                }

                // add waveform
                // observed
                RealVector obsDataVector = new ArrayRealVector(obsID.getData());
                obsStacks[k] = (obsStacks[k] == null ? obsDataVector : add(obsStacks[k], obsDataVector));
                // main synthetic
                RealVector mainSynDataVector = new ArrayRealVector(mainSynID.getData());
                mainSynStacks[k] = (mainSynStacks[k] == null ? mainSynDataVector : add(mainSynStacks[k], mainSynDataVector));
                // reference synthetic 1
                if (refBasicIDs1 != null) {
                    List<BasicID> refSynIDCandidates1 = refBasicIDs1.stream()
                            .filter(id -> BasicID.isPair(id, obsID)).collect(Collectors.toList());
                    if (refSynIDCandidates1.size() != 1)
                        throw new IllegalStateException("0 or more than 1 refSynID1 matching obsID" + obsID.toString());
                    BasicID refSynID1 = refSynIDCandidates1.get(0);
                    RealVector refSynDataVector1 = new ArrayRealVector(refSynID1.getData());
                    refSynStacks1[k] = (refSynStacks1[k] == null ? refSynDataVector1 : add(refSynStacks1[k], refSynDataVector1));
                }
                // reference synthetic 2
                if (refBasicIDs2 != null) {
                    List<BasicID> refSynIDCandidates2 = refBasicIDs2.stream()
                            .filter(id -> BasicID.isPair(id, obsID)).collect(Collectors.toList());
                    if (refSynIDCandidates2.size() != 1)
                        throw new IllegalStateException("0 or more than 1 refSynID2 matching obsID" + obsID.toString());
                    BasicID refSynID2 = refSynIDCandidates2.get(0);
                    RealVector refSynDataVector2 = new ArrayRealVector(refSynID2.getData());
                    refSynStacks2[k] = (refSynStacks2[k] == null ? refSynDataVector2 : add(refSynStacks2[k], refSynDataVector2));
                }
            }

            binStackPlotSetup();

            for (int j = 0; j < obsStacks.length; j++) {
                if (obsStacks[j] != null && mainSynStacks[j] != null) {
                    binStackPlotContent(obsStacks[j], mainSynStacks[j], refSynStacks1[j], refSynStacks2[j], (j + 0.5) * binWidth);
                }
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
            gnuplot.setCommonXlabel("Time in window (s)");
            if (!byAzimuth) {
                gnuplot.setCommonYlabel("Distance (deg)");
            } else {
                gnuplot.setCommonYlabel("Azimuth (deg)");
            }
        }

        private void binStackPlotContent(RealVector obsStack, RealVector mainSynStack,
                RealVector refSynStack1, RealVector refSynStack2, double y) throws IOException {
            SACComponent component = ids.get(0).getSacComponent();
            double samplingHz = ids.get(0).getSamplingHz();

            String fileName = y + "." + component + ".txt";
            outputBinStackTxt(obsStack, mainSynStack, refSynStack1, refSynStack2, fileName, samplingHz);

            double obsMax = obsStack.getLInfNorm();
            double synMax = mainSynStack.getLInfNorm();
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
         * <li> column 3: syn </li>
         * </ul>
         *
         * @param obsStack
         * @param synStack
         * @param fileName
         * @param samplingHz
         * @throws IOException
         */
        private void outputBinStackTxt(RealVector obsStack, RealVector mainSynStack,
                RealVector refSynStack1, RealVector refSynStack2, String fileName, double samplingHz) throws IOException {
            Path outputPath = eventPath.resolve(fileName);

            try (PrintWriter pwTrace = new PrintWriter(Files.newBufferedWriter(outputPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))){
                for (int j = 0; j < obsStack.getDimension(); j++) {
                    double time = j * samplingHz;
                    String line = time + " " + obsStack.getEntry(j) + " " + mainSynStack.getEntry(j)
                            + " " + (refSynStack1 != null ? refSynStack1.getEntry(j) : 0)
                            + " " + (refSynStack2 != null ? refSynStack2.getEntry(j) : 0);
                    pwTrace.println(line);
                }
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
