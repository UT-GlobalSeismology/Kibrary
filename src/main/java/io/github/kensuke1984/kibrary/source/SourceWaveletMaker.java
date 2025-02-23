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
import io.github.kensuke1984.kibrary.correction.StaticCorrectionData;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
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
    private Path timewindowPath;
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
            pw.println("#timewindowPath selectedTimeWindow.dat");
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

        timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
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
    }

    @Override
    public void run() throws IOException {
        // read time window file and select based on component and entries
        sourceTimeWindowSet = TimeWindowDataFile.readAndSelect(timewindowPath, dataEntryPath, components);
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

            // compute signed max amplitude of synthetic (If wavelet is on negative side, this amplitude is negative.)
            double synMin = synTrace.getMinY();
            double synMax = synTrace.getMaxY();
            double synAmp = (-synMin > synMax) ? synMin : synMax;

            // divide observed trace by signed max amplitude of synthetic (This should result on positive side for most cases.)
            RealVector normalizedVector = obsTrace.multiply(1 / synAmp).getYVector();

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
            // divide by the number of time windows added to get average, and half duration to normalize the amplitude
            double[] yArray = sumVector.mapDivide(num).mapDivide(halfDuration).toArray();
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
            Path waveletTimePath = outPath.resolve(eventID + "_time.txt");
            try {
                waveletTrace.write(waveletTimePath);
            } catch (IOException e) {
                e.printStackTrace();
            }

            // zero-pad, with wavelet placed at the center of the time series
            //   Here, arrival + halfDuration is set at center.
            int npts = SPCFileAid.findNpts(tlen, sacSamplingHz);
            int shiftNpts = (int) MathAid.roundForPrecision(2 * halfDuration * sacSamplingHz);
            double[] paddedArray = new double[npts];
            for (int i = 0; i < yArray.length; i++) {
                paddedArray[npts / 2 - shiftNpts + i] = yArray[i];
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

            // output
            SourceTimeFunction sourceTimeFunction = new SourceTimeFunction(complexWave, tlen);
            Path waveletPath = outPath.resolve(eventID + ".stf");
            try {
                sourceTimeFunction.write(waveletPath);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

    }

}
