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
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;

/**
 * Creates record section.
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
     * components to be included in the dataset
     */
    private Set<SACComponent> components;
    /**
     * apparent velocity to use when reducing time [s/deg]
     */
    private double reductionSlowness;
    private AmpStyle obsAmpStyle;
    private AmpStyle synAmpStyle;
    private double ampScale;

    private boolean byAzimuth;
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
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components");
            pw.println("##Path of a basic ID file, must be defined");
            pw.println("#basicIDPath actualID.dat");
            pw.println("##Path of a basic file, must be defined");
            pw.println("#basicPath actual.dat");
            pw.println("##(double) The apparent slowness to use for time reduction [s/deg] (0)");
            pw.println("#reductionSlowness");
            pw.println("##Method for standarization of observed waveform amplitude, from [obsEach,synEach,obsMean,synMean] (obsEach)");
            pw.println("#obsAmpStyle");
            pw.println("##Method for standarization of synthetic waveform amplitude, from [obsEach,synEach,obsMean,synMean] (obsEach)");
            pw.println("#synAmpStyle");
            pw.println("##(double) Coefficient to multiply to all waveforms (1.0)");
            pw.println("#ampScale");
            pw.println("##(boolean) Whether to plot the figure with azimuth as the Y-axis (false)");
            pw.println("#byAzimuth");
            pw.println("##(double) Lower limit of epicentral distance range [0:upperDistance) (0)");
            pw.println("#lowerDistance");
            pw.println("##(double) Upper limit of epicentral distance range (lowerDistance:180] (180)");
            pw.println("#upperDistance");
            pw.println("##(double) Lower limit of azimuth range [-360:upperAzimuth) (0)");
            pw.println("#lowerAzimuth");
            pw.println("##(double) Upper limit of azimuth range (lowerAzimuth:360] (360)");
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
        if (!property.containsKey("components")) property.setProperty("components", "Z R T");
        if (!property.containsKey("basicIDPath"))
            throw new IllegalArgumentException("There is no information about basicIDPath.");
        if (!property.containsKey("basicPath"))
            throw new IllegalArgumentException("There is no information about basicPath.");
        if (!property.containsKey("reductionSlowness")) property.setProperty("reductionSlowness", "0");
        if (!property.containsKey("obsAmpStyle")) property.setProperty("obsAmpStyle", "obsEach");
        if (!property.containsKey("synAmpStyle")) property.setProperty("synAmpStyle", "obsEach");
        if (!property.containsKey("ampScale")) property.setProperty("ampScale", "1.0");

        if (!property.containsKey("byAzimuth")) property.setProperty("byAzimuth", "false");
        if (!property.containsKey("lowerDistance")) property.setProperty("lowerDistance", "0");
        if (!property.containsKey("upperDistance")) property.setProperty("upperDistance", "180");
        if (!property.containsKey("lowerAzimuth")) property.setProperty("lowerAzimuth", "0");
        if (!property.containsKey("upperAzimuth")) property.setProperty("upperAzimuth", "360");
    }

    private void set() throws IOException {
        checkAndPutDefaults();
        workPath = Paths.get(property.getProperty("workPath"));
        if (!Files.exists(workPath)) throw new NoSuchFileException("The workPath " + workPath + " does not exist");

        components = Arrays.stream(property.getProperty("components").split("\\s+")).map(SACComponent::valueOf)
                .collect(Collectors.toSet());
        basicIDPath = getPath("basicIDPath");
        if (!Files.exists(basicIDPath))
            throw new NoSuchFileException("The basic ID file " + basicIDPath + " does not exist");
        basicPath = getPath("basicPath");
        if (!Files.exists(basicPath))
            throw new NoSuchFileException("The basic file " + basicPath + " does not exist");

        reductionSlowness = Double.parseDouble(property.getProperty("reductionSlowness"));
        obsAmpStyle = AmpStyle.valueOf(property.getProperty("obsAmpStyle"));
        synAmpStyle = AmpStyle.valueOf(property.getProperty("synAmpStyle"));
        ampScale = Double.parseDouble(property.getProperty("ampScale"));

        byAzimuth = Boolean.parseBoolean(property.getProperty("byAzimuth"));
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
       Set<EventFolder> eventDirs = DatasetUtils.eventFolderSet(workPath);
       if (!DatasetUtils.checkEventNum(eventDirs.size())) {
           return;
       }

       BasicID[] ids = BasicIDFile.read(basicIDPath, basicPath);

       for (EventFolder eventDir : eventDirs) {
           for (SACComponent component : components) {
               BasicID[] useIds = Arrays.stream(ids).filter(id -> id.getGlobalCMTID().equals(eventDir.getGlobalCMTID())
                       && id.getSacComponent().equals(component))
                       .sorted(Comparator.comparing(BasicID::getObserver))
                       .collect(Collectors.toList()).toArray(new BasicID[0]);

               String fileNameRoot = "profile_" + eventDir.toString() + "_" + component.toString();
               createRecordSection(eventDir, useIds, fileNameRoot);
           }
       }
   }

    private void createRecordSection(EventFolder eventDir, BasicID[] ids, String fileNameRoot) throws IOException {
        if (ids.length == 0) {
            return;
        }

        List<BasicID> obsList = new ArrayList<>();
        List<BasicID> synList = new ArrayList<>();
        BasicIDFile.pairUp(ids, obsList, synList);

        GnuplotFile gnuplot = new GnuplotFile(eventDir.toPath().resolve(fileNameRoot + ".plt"));

        GnuplotLineAppearance obsAppearance = new GnuplotLineAppearance(1, GnuplotColorName.black, 1);
        GnuplotLineAppearance synAppearance = new GnuplotLineAppearance(1, GnuplotColorName.red, 1);

        gnuplot.setOutput("pdf", fileNameRoot + ".pdf", 21, 29.7, true);
        gnuplot.setMarginH(15, 25);
        gnuplot.setMarginV(15, 15);
        gnuplot.setFont("Arial", 20, 15, 15, 15, 10);
        gnuplot.unsetKey();

        gnuplot.setTitle(eventDir.toString());
//        gnuplot.setXlabel("Time aligned on S-wave arrival (s)"); //TODO
        gnuplot.setXlabel("Reduced time (T - " + reductionSlowness + " Δ) (s)");
        if (!byAzimuth) {
            gnuplot.setYlabel("Distance (deg)");
            gnuplot.setYrange(lowerDistance, upperDistance);
            gnuplot.addLabel("station network azimuth", "graph", 1.01, 1.05);
        } else {
            gnuplot.setYlabel("Azimuth (deg)");
            gnuplot.setYrange(lowerAzimuth, upperAzimuth);
            gnuplot.addLabel("station network distance", "graph", 1.01, 1.05);
        }


        // calculate the average of the maximum amplitudes of waveforms
        double obsMeanMax = obsList.stream().collect(Collectors.averagingDouble(id -> new ArrayRealVector(id.getData()).getLInfNorm()));
        double synMeanMax = synList.stream().collect(Collectors.averagingDouble(id -> new ArrayRealVector(id.getData()).getLInfNorm()));

        // for each pair of observed and synthetic waveforms
        for (int i = 0; i < obsList.size(); i++) {
            BasicID obsID = obsList.get(i);
            BasicID synID = synList.get(i);

            double distance = obsID.getGlobalCMTID().getEvent().getCmtLocation()
                    .getEpicentralDistance(obsID.getObserver().getPosition()) * 180. / Math.PI;
            double azimuth = obsID.getGlobalCMTID().getEvent().getCmtLocation()
                    .getAzimuth(obsID.getObserver().getPosition()) * 180. / Math.PI;

            if (lowerDistance <= distance && distance <= upperDistance && MathUtils.checkAngleRange(azimuth, lowerAzimuth, upperAzimuth)) {
                if (azimuth < lowerAzimuth) {
                    azimuth += 360;
                } else if (upperAzimuth < azimuth) {
                    azimuth -= 360;
                }

                RealVector obsDataVector = new ArrayRealVector(obsID.getData());
                RealVector synDataVector = new ArrayRealVector(synID.getData());
                double obsMax = obsDataVector.getLInfNorm();
                double synMax = synDataVector.getLInfNorm();

                // decide the coefficient to amplify each wavefrom
                double obsAmp = selectAmp(obsAmpStyle, obsMax, synMax, obsMeanMax, synMeanMax);
                double synAmp = selectAmp(synAmpStyle, obsMax, synMax, obsMeanMax, synMeanMax);

                String filename = obsID.getObserver() + "." + obsID.getGlobalCMTID() + "." + obsID.getSacComponent() + ".txt";
                String obsUsingString;
                String synUsingString;
                if (!byAzimuth) {
                    gnuplot.addLabel(obsID.getObserver().toPaddedString() + " " + MathUtils.padToString(azimuth, 3, 2),
                            "graph", 1.0, "first", distance);
                    obsUsingString = String.format("($3-%.3f*%.2f):($2/%.3e+%.2f) ", reductionSlowness, distance, obsAmp, distance);
                    synUsingString = String.format("($3-%.3f*%.2f):($4/%.3e+%.2f) ", reductionSlowness, distance, synAmp, distance);
                } else {
                    gnuplot.addLabel(obsID.getObserver().toPaddedString() + " " + MathUtils.padToString(distance, 3, 2),
                            "graph", 1.0, "first", azimuth);
                    obsUsingString = String.format("($3-%.3f*%.2f):($2/%.3e+%.2f) ", reductionSlowness, distance, obsAmp, azimuth);
                    synUsingString = String.format("($3-%.3f*%.2f):($4/%.3e+%.2f) ", reductionSlowness, distance, synAmp, azimuth);
                }
                gnuplot.addLine(filename, obsUsingString, obsAppearance, "observed");
                gnuplot.addLine(filename, synUsingString, synAppearance, "synthetic");
            }
        }

        gnuplot.write();
        if (!gnuplot.execute(eventDir.toPath())) System.err.println("gnuplot failed!!");

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

}
