package io.github.kensuke1984.kibrary.correction;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.timewindow.Timewindow;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.ThreadAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.data.Trace;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 * Operation that computes values of Static correction after Fuji <i>et al</i>., (2010).
 * <p>
 * Timewindows in the input {@link TimewindowDataFile} that satisfy the following criteria will be worked for:
 * <ul>
 * <li> the component is included in the components specified in the property file </li>
 * <li> observed waveform data exists for the (event, observer, component)-pair </li>
 * <li> synthetic waveform data exists for the (event, observer, component)-pair </li>
 * </ul>
 * Both observed and synthetic data must be in event folders under obsDir and synDir (they can be the same folder).
 * Resulting static correction entries will be created for each timewindow,
 * thus specified by a (event, observer, component, timeframe)-pair.
 * <p>
 * The time shift value <i>t</i> for the ray path is for the observed timewindow
 * (i.e. synthetic window [t1, t2], observed [t1-t, t2-t]).
 * <p>
 * The time shift values are computed as follows:
 * <blockquote>ワーキングディレクトリ以下のイベントたちの中にいく<br>
 * 理論波形のstartMkからendMkまでに間で最大ピークを探し冨士さんの 感じで探す とりあえず±sLimit秒で探してみる <br>
 * 観測波形にマーカーがない場合書いてしまう <br>
 * マーカーはrenewパラメタがtrueなら観測波形のマーカーは上書き<br>
 * time shiftの値は小数点２位以下切捨て Algorithm startMkからendMkまでの間で最大振幅を取り、
 * それ以前の時間からthreshold（最大振幅ベース）を超えた振幅の一番早いものをえらびstartMkから、
 * そこまでのタイムウインドウのコリレーションをあわせる <br>
 * </blockquote>
 * <p>
 * Static correction information is written in binary format in "staticCorrection*.dat".
 * See {@link StaticCorrectionDataFile}.
 *
 * @author Kensuke Konishi
 * @version 0.2.2
 * @author anselme modify amplitude ratio computation; modify align method to consider the peak value
 */
public class FujiStaticCorrection extends Operation {

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
     * Path of the output file
     */
    private Path outputPath;
    /**
     * components for computation
     */
    private Set<SACComponent> components;
    /**
     * sampling Hz [Hz] in sac files
     */
    private double sacSamplingHz;

    /**
     * the timewindow data file to work for
     */
    private Path timewindowPath;
    /**
     * the directory of observed data
     */
    private Path obsPath;
    /**
     * the directory of synthetic data
     */
    private Path synPath;

    /**
     * コンボリューションされている波形かそうでないか （両方は無理）
     */
    private boolean convolved;
    /**
     * シグナルとみなすかどうかの最大振幅から見ての比率
     */
    private double threshold;
    /**
     * range for search [s] ±searchRange
     */
    private double searchRange;
    private boolean mediantime;

    private Set<TimewindowData> sourceTimewindowSet;
    private Set<StaticCorrectionData> staticCorrectionSet;

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
            pw.println("##Path of a working folder (.)");
            pw.println("#workPath ");
            pw.println("##(String) A tag to include in output file names. If no tag is needed, leave this unset.");
            pw.println("#fileTag ");
            pw.println("##SacComponents to be used, listed using spaces (Z R T)");
            pw.println("#components ");
            pw.println("##(double) sacSamplingHz (20)");
            pw.println("#sacSamplingHz cant change now");
            pw.println("##Path of a timewindow file, must be set");
            pw.println("#timewindowPath timewindow.dat");
            pw.println("##Path of a root directory containing observed dataset (.)");
            pw.println("#obsPath ");
            pw.println("##Path of a root directory containing synthetic dataset (.)");
            pw.println("#synPath ");
            pw.println("##(boolean) Whether the synthetics have already been convolved (true)");
            pw.println("#convolved ");
            pw.println("##(double) Threshold for peak finder (0.2)");
            pw.println("#threshold ");
            pw.println("##(double) searchRange [s] (10)");
            pw.println("#searchRange ");
            pw.println("##(boolean) Use median time (false)");
            pw.println("#mediantime ");
        }
        System.err.println(outPath + " is created.");
    }

    public FujiStaticCorrection(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {
        workPath = property.parsePath("workPath", ".", true, Paths.get(""));
        if (property.containsKey("fileTag")) fileTag = property.parseStringSingle("fileTag", null);
        components = Arrays.stream(property.parseStringArray("components", "Z R T"))
                .map(SACComponent::valueOf).collect(Collectors.toSet());
        sacSamplingHz = 20; // TODO property.parseDouble("sacSamplingHz", "20");

        timewindowPath = property.parsePath("timewindowPath", null, true, workPath);
        obsPath = property.parsePath("obsPath", ".", true, workPath);
        synPath = property.parsePath("synPath", ".", true, workPath);
        convolved = property.parseBoolean("convolved", "true");

        threshold = property.parseDouble("threshold", "0.2");
        searchRange = property.parseDouble("searchRange", "10");
        mediantime = property.parseBoolean("mediantime", "false");

        String dateStr = GadgetAid.getTemporaryString();
        outputPath = workPath.resolve(DatasetAid.generateOutputFileName("staticCorrection", fileTag, dateStr, ".dat"));
        staticCorrectionSet = Collections.synchronizedSet(new HashSet<>());
    }

    @Override
    public void run() throws IOException {
        // gather all timewindows to be processed
        sourceTimewindowSet = TimewindowDataFile.read(timewindowPath)
                .stream().filter(window -> components.contains(window.getComponent())).collect(Collectors.toSet());
        // collect all events that exist in the timewindow set
        Set<GlobalCMTID> eventSet = sourceTimewindowSet.stream().map(TimewindowData::getGlobalCMTID)
                .collect(Collectors.toSet());

        ExecutorService es = ThreadAid.createFixedThreadPool();
        // for each event, execute run() of class Worker, which is defined at the bottom of this java file
        eventSet.stream().map(Worker::new).forEach(es::execute);
        es.shutdown();
        while (!es.isTerminated()) {
            ThreadAid.sleep(1000);
        }
        // this println() is for starting new line after writing "."s
        System.err.println();

        StaticCorrectionDataFile.write(staticCorrectionSet, outputPath);
    }

    /**
     * @param u u[]
     * @return uが最大絶対値を取る場所 a where |u[a]| is maximum value.
     */
    private static int getMaxPoint(double[] u) {
        int i = 0;
        double max = 0;
        double max2 = 0;
        for (int j = 0; j < u.length; j++)
            if (max2 < (max = u[j] * u[j])) {
                max2 = max;
                i = j;
            }
        return i;
    }

    /**
     * search peaks (f[a] - f[a-1]) * (f[a+1] - f[a]) < 0
     *
     * @param u u[i]
     * @return array of a, where (f[a] - f[a-1]) * (f[a+1] - f[a]) < 0
     */
    private static int[] findPeaks(double[] u) {
        List<Integer> peakI = new ArrayList<>();
        for (int i = 1; i < u.length - 1; i++) {
            double du1 = u[i] - u[i - 1];
            double du2 = u[i + 1] - u[i];
            if (du1 * du2 < 0)
                peakI.add(i);
        }
        int[] peaks = new int[peakI.size()];
        for (int i = 0; i < peaks.length; i++)
            peaks[i] = peakI.get(i);
        return peaks;
    }

    private static double[] cutSac(SACFileAccess sacData, double tStart, double tEnd) {
        Trace t = sacData.createTrace();
        t = t.cutWindow(tStart, tEnd);
        return t.getY();
    }

    /**
     * Search the max point of synthetic in the time window for a pair. and then
     * search the max value within the search range and same positive and
     * negative. Relative ratio of synthetic is 1;
     *
     * @param obsSac observed sac data
     * @param synSac synthetic sac data
     * @param shift  time shift for correction
     * @param window time window
     * @return ratio of maximum values
     */
    private double computeMaxRatio(SACFileAccess obsSac, SACFileAccess synSac, double shift, Timewindow window) {
        double delta = 1 / sacSamplingHz;

        double startSec = window.getStartTime();
        double endSec = window.getEndTime();

        // create synthetic timewindow
        double[] syn = cutSac(synSac, startSec, endSec);
        // which point gives the maximum value
        int maxPoint = getMaxPoint(syn);
        double maxSyn = syn[maxPoint];

        // create observed timewindow
        double[] obs = cutSac(obsSac, startSec - shift + maxPoint * delta - searchRange,
                startSec - shift + maxPoint * delta + searchRange);
        double maxObs = maxSyn < 0 ? Arrays.stream(obs).min().getAsDouble() : Arrays.stream(obs).max().getAsDouble();

        return maxObs / maxSyn;
    }

    private double computeP2PRatio(SACFileAccess obsSac, SACFileAccess synSac, double shift, Timewindow window) {
        double delta = 1 / sacSamplingHz;

        double startSec = window.getStartTime();
        double endSec = window.getEndTime();

        // create synthetic timewindow
        Trace synTrace = synSac.createTrace().cutWindow(startSec, endSec);
        // which point gives the maximum value
        double synP2P = synTrace.getMaxValue() - synTrace.getMinValue();
        int maxPoint = synTrace.getIndexOfPeak()[0];

        // create observed timewindow
        Trace obsTrace = obsSac.createTrace().cutWindow(startSec - shift, endSec - shift);
        double obsP2P = obsTrace.getMaxValue() - obsTrace.getMinValue();

        return obsP2P / synP2P;
    }

    /**
     * synthetic ->[t1, t2], observed ->[t1-t(returning value), t2-t]
     *
     * @param obsSac observed sac data
     * @param synSac synthetic sac data
     * @param window time window
     * @return value for time shift
     */
    private double computeTimeshiftForBestCorrelation(SACFileAccess obsSac, SACFileAccess synSac, Timewindow window) {
        double delta = 1 / sacSamplingHz;

        double startSec = window.getStartTime();
        double endSec = window.getEndTime();

        // create synthetic timewindow
        double[] syn = cutSac(synSac, startSec, endSec);

        // which point gives the maximum value
        int maxPoint = getMaxPoint(syn);

        // startpointから、一番早くしきい値（割合が最大振幅のthreshold）を超える点までのポイント数
        int endPoint = getEndPoint(syn, maxPoint);
        double endtime = startSec + endPoint * synSac.getValue(SACHeaderEnum.DELTA);

        if (mediantime) {
            double medianTime = startSec + (endPoint + maxPoint) / 2. * synSac.getValue(SACHeaderEnum.DELTA);
            endtime = medianTime;
            startSec = endtime - 15;
        }

        // recreate synthetic timewindow
        syn = cutSac(synSac, startSec, endtime);

        // create observed timewindow
        double obsStartSec = startSec - searchRange;
        double obsEndSec = endtime + searchRange;
        double[] obs = cutSac(obsSac, obsStartSec, obsEndSec);

        int pointshift = getBestPoint(obs, syn, delta, searchRange);
        double timeshift = pointshift * delta;
        return Precision.round(timeshift, 2);
    }

    private double computeTimeshiftForBestCorrelation_peak(SACFileAccess obsSac, SACFileAccess synSac, Timewindow window) {
        double delta = 1 / sacSamplingHz;

        double startSec = window.getStartTime();
        double endSec = window.getEndTime();

        // create synthetic timewindow
        double[] syn = cutSac(synSac, startSec, endSec);

        // which point gives the maximum value
        int maxPoint = getMaxPoint(syn);

        double endtime = startSec + maxPoint * synSac.getValue(SACHeaderEnum.DELTA);

        // recreate synthetic timewindow
        syn = cutSac(synSac, startSec, endtime);

        // create observed timewindow
        double obsStartSec = startSec - searchRange;
        double obsEndSec = endtime + searchRange;
        double[] obs = cutSac(obsSac, obsStartSec, obsEndSec);

        int pointshift = getBestPoint(obs, syn, delta, searchRange);
        double timeshift = pointshift * delta;
        return Precision.round(timeshift, 2);
    }

    /**
     * @param obs observed waveform
     * @param syn synthetic waveform
     * @return the number of points we should shift so that obs(t-shift) and
     * syn(t) are good correlation
     */
    private int getBestPoint(double[] obs, double[] syn, double delta, double range) {
        int shift = 0;
        double cor = 0;

        // double synsum = 0;
        // for (int j = 0; j < syn.length; j++)
        // synsum += syn[j] * syn[j];
        // synsum = Math.sqrt(synsum);
        // searchWidthから 相関のいいshiftを探す
        int width = obs.length - syn.length; // searchWidth
        for (int shiftI = 0; shiftI < width; shiftI++) {
            // double variance = 0;
            double tmpcor = 0;
            double obssum = 0;
            double synsum = 0;
            for (int j = 0; j < syn.length; j++) {
                tmpcor += syn[j] * obs[j + shiftI];
                obssum += obs[j + shiftI] * obs[j + shiftI];
                // variance += (syn[j] - obs[j + shiftI])
                // * (syn[j] - obs[j + shiftI]);
                synsum += syn[j] * syn[j];
            }
            obssum = Math.sqrt(obssum);
            synsum = Math.sqrt(synsum);
            // tmpcor /= obssum;
            tmpcor /= synsum * obssum;
            // variance /= obssum;
            // System.out.println(((int) (-searchRange / delta) + shiftI) + " "
            // + tmpcor + " " + variance);
            if (tmpcor > cor) {
                shift = shiftI;
                cor = tmpcor;
            }
        }
        // System.out.println(pointWidth+" "+shift);
        return (int) (range / delta) - shift;
    }

    /**
     * search the earliest time when |u| > threshold*max
     *
     * @param u        u[x]
     * @param maxPoint u[maxPoint] is maximum
     * @return the end time for a time window used in calculation of correlation
     */
    private int getEndPoint(double[] u, int maxPoint) {
        double max = u[maxPoint];
        // search peaks
        int[] iPeaks = findPeaks(u);
        double minLimit = Math.abs(threshold * max);
        for (int ipeak : iPeaks)
            if (minLimit < Math.abs(u[ipeak])) return ipeak;
        return maxPoint;
    }

    private class Worker extends DatasetAid.FilteredDatasetWorker {

        private Worker(GlobalCMTID eventID) {
            super(eventID, obsPath, synPath, convolved, sourceTimewindowSet);
        }

        @Override
        public void actualWork(TimewindowData timewindow, SACFileAccess obsSac, SACFileAccess synSac) {
            Observer observer = timewindow.getObserver();
            SACComponent component = timewindow.getComponent();

            // check delta
            double delta = 1 / sacSamplingHz;
            if (delta != obsSac.getValue(SACHeaderEnum.DELTA) || delta != synSac.getValue(SACHeaderEnum.DELTA)) {
                System.err.println(
                        "!! Deltas are invalid. " + obsSac + " " + obsSac.getValue(SACHeaderEnum.DELTA) + " , " +
                                synSac + " " + synSac.getValue(SACHeaderEnum.DELTA) + " ; must be " + delta);
                return;
            }

            // compute correction
            try {
                double shift = 0;
                if (!mediantime) shift = computeTimeshiftForBestCorrelation(obsSac, synSac, timewindow);
                else shift = computeTimeshiftForBestCorrelation_peak(obsSac, synSac, timewindow);
//                  double ratio = computeMaxRatio(obsSac, synSac, shift, window);
                double ratio = computeP2PRatio(obsSac, synSac, 0., timewindow);
                StaticCorrectionData correction = new StaticCorrectionData(observer, eventID, component,
                        timewindow.getStartTime(), shift, ratio, timewindow.getPhases());
                staticCorrectionSet.add(correction);
            } catch (Exception e) {
                System.err.println("!! " + timewindow + " is ignored because an error occurs.");
                e.printStackTrace();
            }
        }

    }
}
