package io.github.kensuke1984.kibrary.source;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.Test_temp;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionData;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.external.gnuplot.GnuplotFile;
import io.github.kensuke1984.kibrary.math.FourierTransform;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.timewindow.TimeWindow;
import io.github.kensuke1984.kibrary.timewindow.TimeWindowData;
import io.github.kensuke1984.kibrary.timewindow.TimeWindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.spc.SPCFileAid;

/**
 * Operation to create source wavelets by stacking waveforms along a certain phase for each event.
 *
 * CAUTION: Time windows shold be the same length for all records of the same event, taken for a single phase.
 *
 * @author otsuru
 * @since 2024/6/14
 */
public class SourceWaveletMaker extends Operation {

    private static final double TAPER_LENGTH_PERCENT = 15.0;

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
     * Components to use.
     */
    private Set<SACComponent> components;
    /**
     * Path of the output folder.
     */
    private Path outPath;

    /**
     * Path of a time window information file.
     */
    private Path timeWindowPath;
    /**
     * Time length that the time window includes before main phase arrival [s].
     * If the value is 5 (not -5), each time window starts 5 sec before the main phase arrival.
     */
    private double frontShift;
    /**
     * Path of a root folder containing observed dataset.
     */
    private Path obsPath;
    /**
     * Path of a root folder containing synthetic dataset.
     */
    private Path synPath;
    /**
     * Whether the synthetic waveforms are convolved.
     */
    private boolean convolved;
    /**
     * Sampling frequency of input SAC files [Hz].
     */
    private double sacSamplingHz;

    /**
     * Path of a data entry file.
     */
    private Path dataEntryPath;
    /**
     * Path of static correction file.
     */
    private Path staticCorrectionPath;

    /**
     * Time length of output STFs [s], must be (a power of 2)/samplingHz.
     */
    private double tlen;
    /**
     * Number of steps in frequency domain, should not exceed tlen*samplingHz/2.
     */
    private int np;

    /**
     * Whether to normalize input waveforms by synthetic waveforms or observed waveforms.
     */
    private boolean normalizeBySyn;

    private Set<TimeWindowData> sourceTimeWindowSet;
    private Set<StaticCorrectionData> staticCorrectionSet;

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
            pw.println("##Path of a time window file, must be set.");
            pw.println("#timeWindowPath selectedTimeWindow.dat");
            pw.println("##(double) Time length before phase arrival in time window [s]. (20)");
            pw.println("#frontShift ");
            pw.println("##Path of a root folder containing observed dataset. (.)");
            pw.println("#obsPath ");
            pw.println("##Path of a root folder containing synthetic dataset. (.)");
            pw.println("#synPath ");
            pw.println("##(boolean) Whether the synthetics have already been convolved. (true)");
            pw.println("#convolved false");
            pw.println("##(double) Sampling frequency of input SAC files [Hz]. (20)");
            pw.println("#sacSamplingHz ");
            pw.println("##Path of a data entry list file, if you want to select raypaths.");
            pw.println("#dataEntryPath selectedEntry.lst");
            pw.println("##Path of a static correction file, if static correction time-shift shall be applied.");
            pw.println("#staticCorrectionPath staticCorrection.dat");
            pw.println("##Time length of output STFs [s], must be (a power of 2)/sacSamplingHz. (3276.8)");
            pw.println("#tlen ");
            pw.println("##(int) Number of points to compute in frequency domain, should not exceed tlen*sacSamplingHz/2. (512)");
            pw.println("#np ");
            pw.println("##(boolean) Whether to normalize input waveforms by synthetic waveforms. (true)");
            pw.println("#normalizeBySyn ");
        }
        System.err.println(outPath + " is created.");
    }

    public SourceWaveletMaker(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("folderTag")) folderTag = property.parseStringSingle("folderTag", null);
        appendFolderDate = property.parseBoolean("appendFolderDate", "true");
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());

        timeWindowPath = Test_temp.getTimeWindowPath_temp(property, workPath);  //TODO delete (This is here for backward compatibility.)
//      timeWindowPath = property.parsePath("timeWindowPath", null, true, workPath);
        frontShift = property.parseDouble("frontShift", "20");
        obsPath = property.parsePath("obsPath", ".", true, workPath);
        synPath = property.parsePath("synPath", ".", true, workPath);
        convolved = property.parseBoolean("convolved", "true");
        sacSamplingHz = property.parseDouble("sacSamplingHz", "20");

        if (property.containsKey("dataEntryPath")) {
            dataEntryPath = property.parsePath("dataEntryPath", null, true, workPath);
        }
        if (property.containsKey("staticCorrectionPath")) {
            staticCorrectionPath = property.parsePath("staticCorrectionPath", null, true, workPath);
        }

        tlen = property.parseDouble("tlen", "3276.8");
        np = property.parseInt("np", "512");

        normalizeBySyn = property.parseBoolean("normalizeBySyn", "true");
    }

    @Override
    public void run() throws IOException {
        // read time window file and select based on component and entries
        sourceTimeWindowSet = TimeWindowDataFile.readAndSelect(timeWindowPath, dataEntryPath, components);
        // collect all events that exist in the time window set
        Set<GlobalCMTID> eventSet = sourceTimeWindowSet.stream().map(TimeWindowData::getGlobalCMTID).collect(Collectors.toSet());

        // read static corrections
        staticCorrectionSet = (staticCorrectionPath == null ? Collections.emptySet() :
                StaticCorrectionDataFile.read(staticCorrectionPath));

        outPath = DatasetAid.createOutputFolder(workPath, "wavelets", folderTag, appendFolderDate, null);
        property.write(outPath.resolve("_" + this.getClass().getSimpleName() + ".properties"));

        ExecutorService es = ThreadAid.createFixedThreadPool();
        // for each event, execute run() of class Worker, which is defined at the bottom of this java file
        eventSet.stream().map(Worker::new).forEach(es::execute);
        es.shutdown();
        while (!es.isTerminated()) {
            ThreadAid.sleep(1000);
        }
        // this println() is for starting new line after writing "."s
        System.err.println();
    }

    private class Worker extends DatasetAid.FilteredDatasetWorker {
        RealVector sumVector;
        int num = 0;
        double halfDuration;

        private Worker(GlobalCMTID eventID) {
            super(eventID, obsPath, synPath, convolved, sacSamplingHz, sourceTimeWindowSet);
            halfDuration = eventID.getEventData().getHalfDuration();
        }

        @Override
        public void actualWork(TimeWindowData timeWindow, SACFileAccess obsSac, SACFileAccess synSac) {

            // check SAC file end time
            if (timeWindow.getEndTime() > obsSac.getValue(SACHeaderEnum.E)
                    || timeWindow.getEndTime() > synSac.getValue(SACHeaderEnum.E)) {
                System.err.println();
                System.err.println("!! End of time window too late, skipping: " + timeWindow);
                return;
            }

            // retrieve static correction
            double shift = 0.;
            if (!staticCorrectionSet.isEmpty()) {
                StaticCorrectionData correction = StaticCorrectionData.findForTimeWindow(staticCorrectionSet, timeWindow);
                if (correction == null) {
                    System.err.println();
                    System.err.println("!! No static correction data, skipping: " + timeWindow);
                    return;
                }
                shift = correction.getTimeshift();
            }

            // use window [arrival - halfDuration, arrival + 3 * halfDuration]
            double startTime = timeWindow.getStartTime() + frontShift - halfDuration;
            double endTime = timeWindow.getStartTime() + frontShift + 3 * halfDuration;
            TimeWindow stfWindow = new TimeWindow(startTime, endTime);

            // prepare observed trace, integrated to get displacement waveform
            Trace obsTrace = obsSac.createTrace().cutWindow(stfWindow.shift(-shift), sacSamplingHz);
            obsTrace = obsTrace.integrate();

            // prepare synthetic trace, integrated to get displacement waveform
            Trace synTrace = synSac.createTrace().cutWindow(stfWindow, sacSamplingHz);
            synTrace = synTrace.integrate();

            // normalize by area of wavelet
            double t2 = timeWindow.getStartTime() + frontShift + halfDuration;
            RealVector normalizedVector;
            if (normalizeBySyn) {
                // compute signed area of synthetic (If wavelet is on negative side, this area is negative.)
                Trace integrateSynTrace = synTrace.integrate();
                double synArea = integrateSynTrace.findYAtNearestX(t2);

                // divide observed trace by signed area of synthetic (This should result on positive side for most cases.)
                normalizedVector = obsTrace.multiply(1 / synArea).getYVector();

            } else {
                // compute signed area of observed (If wavelet is on negative side, this area is negative.)
                Trace integrateObsTrace = obsTrace.integrate();
                double obsArea = integrateObsTrace.findYAtNearestX(t2);
                if (obsArea == 0) {
                    System.out.println("obsArea = 0.");
                    return;
                }
                // divide observed trace by signed area of observed (This should result on positive side for most cases.)
                normalizedVector = obsTrace.multiply(1 / obsArea).getYVector();
            }

            // stack
            if (sumVector == null) {
                sumVector = normalizedVector;
            } else {
                int obsLength = normalizedVector.getDimension();
                int sumLength = sumVector.getDimension();
                if (obsLength < sumLength) {
                    sumVector = sumVector.getSubVector(0, obsLength);
                } else if (sumLength < obsLength) {
                    normalizedVector = normalizedVector.getSubVector(0, sumLength);
                }
                sumVector = sumVector.add(normalizedVector);
            }
            num++;
        }

        @Override
        public void finalWork() {

            Path eventDirectryPath = outPath.resolve(eventID + "");

            // omit events with less than 5 observation as in Yamaya et al. (2018)
            if(num < 5) {
                System.out.println(eventID + " has less than 5 observation");
                return;
            }

            // divide by the number of time windows added to get average
            double[] yArray = sumVector.mapDivide(num).toArray();
            // taper
            yArray = FourierTransform.taper(yArray, TAPER_LENGTH_PERCENT, true);

            // create X axis (time)
            double[] xArray = new double[sumVector.getDimension()];
            for (int i = 0; i < sumVector.getDimension(); i++) {
                xArray[i] = i / sacSamplingHz;
            }
            // form Trace
            Trace waveletTrace = new Trace(xArray, yArray);
            // write
            Path waveletTimePath = eventDirectryPath.resolve(eventID + "_time.txt");
            try {
                Files.createDirectory(eventDirectryPath);
                waveletTrace.write(waveletTimePath);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // create triangle STF
            double t0 = waveletTrace.getXforMaxYValue();
            int halfIndex1 = computet1(waveletTrace, t0);
            double t1 = waveletTrace.getXAt(halfIndex1);
            int halfIndex2 = computet2(waveletTrace, t0);
            double t2 = waveletTrace.getXAt(halfIndex2);

            double maxtau1 = t0 - waveletTrace.getXAt(0);
            double maxtau2 = waveletTrace.getXAt(waveletTrace.getLength() - 1) - t0;
            double delta = 0.1;
            double minsum  = Double.POSITIVE_INFINITY;
            double tau1 = 0;
            double tau2 = 0;
            for (double temptau1 = t0 - t1; temptau1 <= maxtau1; temptau1 += delta) {
                for (double temptau2 = t2 - t0; temptau2 <= maxtau2; temptau2 += delta) {
                    double sum = 0;
                    for (int i = halfIndex1; i <= halfIndex2; i++) {
                        double t = waveletTrace.getXAt(i);
                        double x = t - t0;
                        double f = f(temptau1, temptau2, x);
                        double y = waveletTrace.getYAt(i);
                        sum += Math.pow(y - f, 2);
                    }
                    if (sum < minsum) {
                        minsum = sum;
                        tau1 = temptau1;
                        tau2 = temptau2;
                    }
                }
            }

            // write triangle STF
            Path trianglePath = eventDirectryPath.resolve(eventID + "_triangle.txt");
            try {
                writeTriangle(t0, tau1, tau2, maxtau2, trianglePath);
            } catch (IOException e) {
                e.printStackTrace();
            }
            // write cut Trace
            Trace cutTrace = waveletTrace.cutWindow(t0 - tau1, t0 + tau2);
            Path cutPath = eventDirectryPath.resolve(eventID + "_cut.txt");
            try {
                cutTrace.write(cutPath);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // divide by the number of time windows added to get average, and half duration to normalize the amplitude
            double[] cutYArray = cutTrace.getY();
            // taper
            cutYArray = FourierTransform.taper(cutYArray, TAPER_LENGTH_PERCENT, true);

            // zero-pad, with wavelet placed at the center of the time series
            //   Here, arrival + halfDuration is set at center.
            int npts = SPCFileAid.findNpts(tlen, sacSamplingHz);
            int shiftNpts = (int) MathAid.roundForPrecision(tau1 * sacSamplingHz);
            double[] paddedArray = new double[npts];
            for (int i = 0; i < cutYArray.length; i++) {
                paddedArray[npts / 2 - shiftNpts + i] = cutYArray[i];
            }

            // create X axis (time)
            double[] cutXArray = new double[paddedArray.length];
            for (int i = 0; i < paddedArray.length; i++) {
                cutXArray[i] = i / sacSamplingHz;
            }
            // form Trace
            Trace checkTrace = new Trace(cutXArray, paddedArray);
            // write
            Path checkTimePath = eventDirectryPath.resolve(eventID + "_checkTime.txt");
            try {
                checkTrace.write(checkTimePath);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // convert to frequency domain
            Complex[] complexWave = FourierTransform.convertToFrequencyDomain(paddedArray, np);

            for (int i = 0; i < complexWave.length; i++) {
                // divide sampling frequency [Hz] so that the FFT matches with the Fourier transform
                complexWave[i] = complexWave[i].divide(sacSamplingHz);
                // time-shift so that the wavelet is at time zero
                // This is done by reversing sign of odd-number index entries.
                if (i % 2 == 1) complexWave[i] = complexWave[i].multiply(-1.0);
            }

            // output stacked stf
            SourceTimeFunction stackedSourceTimeFunction = new SourceTimeFunction(complexWave, tlen);

            Path stackDirectryPath = outPath.resolve("stack");
            Path stackWaveletPath = stackDirectryPath.resolve(eventID + ".stf");
            try {
                Files.createDirectories(stackDirectryPath);
                stackedSourceTimeFunction.write(stackWaveletPath);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            // output triangle stf
            SourceTimeFunction triangleSourceTimeFunction
                = SourceTimeFunction.asymmetricTriangleSourceTimeFunction(np, tlen, tau1, tau2);

            Path triangleDirectryPath = outPath.resolve("triangle");
            Path triangleWaveletPath = triangleDirectryPath.resolve(eventID + ".stf");
            try {
                Files.createDirectories(triangleDirectryPath);
                triangleSourceTimeFunction.write(triangleWaveletPath);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }

            // make graphs of stacked and triangle stf
            double windowLength = waveletTrace.getMaxX() - waveletTrace.getMinX();
            Path stackPltPath = eventDirectryPath.resolve(eventID + ".plt");
            try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(stackPltPath))) {
                pw.println("set term pngcairo enhanced size 1600,800 font 'Helvetica.20'");
                pw.println("set output 'wave.png'");
                pw.println("set xrange [0:" + windowLength + "]");
                pw.println("p \"" + eventID + "_time.txt\" w l title \"stacked\", \"" + eventID + "_triangle.txt\" w l title \"LSTF\"");
            } catch (IOException e) {
                e.printStackTrace();
            }

            GnuplotFile gnuplot = new GnuplotFile(stackPltPath);
            try {
                if (!gnuplot.execute()) System.err.println("gnuplot failed!!");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }
    //output triangle
    private void writeTriangle(double t0, double tau1, double tau2, double maxtau2, Path outputPath)throws IOException {
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            pw.println(0 + " " + 0);
            pw.println((t0 - tau1) + " " + 0);
            pw.println(t0 +" " + (2 / (tau1 +tau2)));
            pw.println((t0 + tau2) + " " + 0);
            pw.println((t0 + maxtau2)+ " " + 0);
        }
    }
    //t1
    private int computet1(Trace waveletTrace, double t0) {
        double yhalf = waveletTrace.getMaxY() / 2;
        int xIndexforMax = waveletTrace.getXIndexforMaxYValue();
        int halfIndex1 = 0;
        for (int i = xIndexforMax; i >= 0; i--) {
            if (waveletTrace.getYAt(i) < yhalf) {
                halfIndex1 = i;
                break;
            }
        }
        if (halfIndex1 == 0) {
            throw new IllegalStateException();
        }
        return halfIndex1;
    }
    //t2
    private int computet2(Trace waveletTrace, double t0) {
        double yhalf = waveletTrace.getMaxY() / 2;
        int xIndexforMax = waveletTrace.getXIndexforMaxYValue();
        int halfIndex2 = 0;
        for (int i = xIndexforMax; i < waveletTrace.getLength(); i++) {
            if (waveletTrace.getYAt(i) < yhalf) {
                halfIndex2 = i;
                break;
            }
        }
        if (halfIndex2 == 0) {
            throw new IllegalStateException();
        }
        return halfIndex2;
    }

    //f(x)
    private double f(double tau1, double tau2, double x) {
        if (x > -tau1 && x <= 0) {
            return 2 / (tau1 + tau2) * (x + tau1) / tau1;
        } else if (x > 0 && x < tau2) {
            return 2 / (tau1 + tau2) * (tau2 - x) / tau2;
        } else {
            return 0;
        }
    }
}
