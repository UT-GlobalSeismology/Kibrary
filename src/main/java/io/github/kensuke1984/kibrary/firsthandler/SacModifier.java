package io.github.kensuke1984.kibrary.firsthandler;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.external.SAC;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;
import io.github.kensuke1984.kibrary.util.sac.SACUtil;

/**
 * Modification of SAC when running {@link EventProcessor}
 *
 * @author Kensuke Konishi
 */
class SacModifier {

    /**
     * [msec] time window for taper
     */
    private static final int taperTime = 60 * 1000;

    private final GlobalCMTAccess event;
    private final Path sacPath;
    private final Path modifiedPath;
    private Map<SACHeaderEnum, String> headerMap;

    /**
     * extract with PDE (true), CMT (false)
     */
    private final boolean byPDE;


    /**
     * sac start time when this instance is made
     */
    private LocalDateTime initialSacStartTime;
    /**
     * sac end time when this instance is made
     */
    private LocalDateTime initialSacEndTime;
    /**
     * event time
     */
    private LocalDateTime eventTime;

    /**
     * @param globalCMTData cmt data
     * @param sacPath       path of sac file
     * @param byPDE         true: PDE, false: CMT
     * @throws IOException
     */
    SacModifier(GlobalCMTAccess globalCMTData, Path sacPath, boolean byPDE) throws IOException {
        this.sacPath = sacPath;
        this.headerMap = SACUtil.readHeader(sacPath);
        SacFileName sacFile = new SacFileName(sacPath.getFileName().toString());
        String modifiedFileName = sacFile.getModifiedFileName();
        this.modifiedPath = sacPath.resolveSibling(modifiedFileName);
        this.event = globalCMTData;
        this.byPDE = byPDE;
        eventTime = byPDE ? event.getPDETime() : event.getCMTTime();
        setInitialSacTimes();
    }

    /**
     * Sets {@link #initialSacStartTime}
     */
    private void setInitialSacTimes() {
        int year = Integer.parseInt(headerMap.get(SACHeaderEnum.NZYEAR));
        int jday = Integer.parseInt(headerMap.get(SACHeaderEnum.NZJDAY));
        int hour = Integer.parseInt(headerMap.get(SACHeaderEnum.NZHOUR));
        int min = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMIN));
        int sec = Integer.parseInt(headerMap.get(SACHeaderEnum.NZSEC));
        int msec = Integer.parseInt(headerMap.get(SACHeaderEnum.NZMSEC));

        double b = Double.parseDouble(headerMap.get(SACHeaderEnum.B));
        long bInNanos = (long) (b * 1000 * 1000 * 1000);
        initialSacStartTime =
                LocalDateTime.of(year, 1, 1, hour, min, sec, msec * 1000 * 1000).plusDays(jday - 1).plusNanos(bInNanos);

        double e = Double.parseDouble(headerMap.get(SACHeaderEnum.E));
        long eInNanos = (long) (e * 1000 * 1000 * 1000);
        initialSacEndTime =
                LocalDateTime.of(year, 1, 1, hour, min, sec, msec * 1000 * 1000).plusDays(jday - 1).plusNanos(eInNanos);
    }

    /**
     * If SAC length <= {@link #taperTime}, tapering cannot be done.
     * @return(boolean) Whether SAC length is longer than {@link #taperTime}.
     */
    boolean canBeTapered() {
        return initialSacStartTime.until(initialSacEndTime, ChronoUnit.MILLIS) > taperTime;
    }

    /**
     * If the startTime of this SAC is after the event time and the gap >= {@link #taperTime}, zero-padding is not done.
     * @return (boolean) Whether start time is soon enough.
     *   This is true if start time is before event time,
     *     or if gap between sac starting time and event time is smaller than {@link #taperTime}.
     */
    boolean canBeZeroPadded() {
        // Sac start time in millisec - event time in millisec
        return eventTime.until(initialSacStartTime, ChronoUnit.MILLIS) < taperTime;
    }

    /**
     * If the endTime of this SAC is before the event time, trimming will fail.
     * @return (boolean) Whether sac end time is after event time.
     */
    boolean canBeTrimmed() {
        // Sac end time in millisec - event time in millisec
        return eventTime.until(initialSacEndTime, ChronoUnit.MILLIS) > 0.0;
    }

    /**
     * Operates rtrend and rmean in SAC. The output is written to a new SAC file with the name ??.MOD
     * @throws IOException
     */
    void removeTrend() throws IOException {
        try (SAC sacProcess = SAC.createProcess()) {
            String cwd = sacPath.getParent().toString();
            sacProcess.inputCMD("cd " + cwd);
            sacProcess.inputCMD("r " + sacPath.getFileName());
            sacProcess.inputCMD("ch lovrok true");
            sacProcess.inputCMD("rtrend");
            sacProcess.inputCMD("rmean");
            sacProcess.inputCMD("w " + modifiedPath.getFileName());
        }
    }

    /**
     * Checks whether the waveform data is completely zero or unreadable.
     * @return (boolean) true if the waveform data is completely zero or unreadable
     * @throws IOException
     */
    boolean isCompleteZero() throws IOException {
        // read sacdata
        double[] sacdata = SACUtil.readSACData(modifiedPath);
        RealVector obsVec = new ArrayRealVector(sacdata);

        // check
        if (Double.isNaN(obsVec.getLInfNorm()) || obsVec.getLInfNorm() == 0) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * イベント時刻よりSac開始が遅い場合、Sacを補完する。
     * 補完の際、Sac開始の部分を幅 {@link #taperTime} でtaperをかけ、その前を０で補完する。 <br>
     * イベント時刻よりSac開始が早い場合、時刻の原点をイベント時刻にずらす。<br>
     * その後ヘッダーを更新する。その際、イベント情報も入力するので、震央距離も計算される。
     * {@link #canBeZeroPadded()} should be checked for before executing this method!
     *
     * @return (boolean) true if success
     * @throws IOException
     */
    boolean zeroPad() throws IOException {
        double b = Double.parseDouble(headerMap.get(SACHeaderEnum.B));
        long bInMillis = Math.round(b * 1000);
        double e = Double.parseDouble(headerMap.get(SACHeaderEnum.E));
        long eInMillis = Math.round(e * 1000);

        // read sacdata
        double[] sacdata = SACUtil.readSACData(modifiedPath);

        // イベント時刻と合わせる
        // イベント時刻とSAC時刻の差
        // Sac start time in millisec - event time in millisec
        long timeGapInMillis = eventTime.until(initialSacStartTime, ChronoUnit.MILLIS);

        // if the sac startTime is after the event time, then interpolate the gap.
        // if the gap >= tapertime then the SAC file is skipped.
        if (taperTime <= timeGapInMillis) {
            // this shall not happen, because this should be checked in canBeZeroPadded()
            throw new IllegalStateException("Seismogram starts too late : "
                    + event.getGlobalCMTID() + " - " + modifiedPath.getFileName());
        } else if (0 <= timeGapInMillis) {
            System.err.println("++ seismograms start after the event time, zero-padding : "
                    + event.getGlobalCMTID() + " - " + modifiedPath.getFileName());
            // delta [msec]
            long deltaInMillis = (long) (Double.parseDouble(headerMap.get(SACHeaderEnum.DELTA)) * 1000);

            // number of points in gap
            int gapPoint = (int) (timeGapInMillis / deltaInMillis);

            // number of points for the taper
            int taperPoint = (int) (taperTime / deltaInMillis);

            // taper
            for (int i = 0; i < taperPoint; i++)
                sacdata[i] *= Math.sin(i * Math.PI / taperPoint / 2.0);

            // zero-pad
            double[] neosacdata = new double[sacdata.length + gapPoint];
            // gapの部分は0で、taperをかけたsacdataをくっつける
            System.arraycopy(sacdata, 0, neosacdata, gapPoint, neosacdata.length - gapPoint);

            int npts = neosacdata.length;
            headerMap.put(SACHeaderEnum.NPTS, String.valueOf(npts));
            sacdata = neosacdata;
            timeGapInMillis = 0;
            // headerMap.put(SacHeaderEnum.B, String.valueOf(0));
        }
        // 早く始まってるものは、taperいらない？
        // -> The tapering here is done just for the sake of connecting the zero-value segment and the incomplete waveform.
        //    Tapering of the whole waveform is done in SacDeconvolution.

        headerMap.put(SACHeaderEnum.B, String.valueOf((bInMillis + timeGapInMillis) / 1000.0));
        headerMap.put(SACHeaderEnum.E, String.valueOf((eInMillis + timeGapInMillis) / 1000.0));

        headerMap.put(SACHeaderEnum.NZYEAR, String.valueOf(eventTime.getYear()));
        headerMap.put(SACHeaderEnum.NZJDAY, String.valueOf(eventTime.getDayOfYear()));
        headerMap.put(SACHeaderEnum.NZHOUR, String.valueOf(eventTime.getHour()));
        headerMap.put(SACHeaderEnum.NZMIN, String.valueOf(eventTime.getMinute()));
        headerMap.put(SACHeaderEnum.NZSEC, String.valueOf(eventTime.getSecond()));
        headerMap.put(SACHeaderEnum.NZMSEC, String.valueOf(eventTime.getNano() / 1000 / 1000));

        SACUtil.writeSAC(modifiedPath, headerMap, sacdata);
        return true;
    }


    /**
     * The SAC start time is set to the event time by deleting data before t=0 (set in {@link #zeroPad()}).
     * The ending part of the SAC file is cut so that npts = 2^n.
     *
     * @throws IOException if any
     */
    void trim(int maxNpts) throws IOException {

        // set new npts so that it is a power of 2,
        //  does not surpass the number of points at "E" (the end) of SAC file,
        //  and does not surpass the limit (maxNpts)
        Map<SACHeaderEnum, String> headerMap = SACUtil.readHeader(modifiedPath);
        int npts = (int) (Double.parseDouble(headerMap.get(SACHeaderEnum.E)) /
                Double.parseDouble(headerMap.get(SACHeaderEnum.DELTA)));
        if (npts > maxNpts) npts = maxNpts;
        int newNpts = Integer.highestOneBit(npts);
        // cut SAC file to start at 0 (event time) and end at new npts
        String cwd = sacPath.getParent().toString();
        try (SAC sacP1 = SAC.createProcess()) {
            sacP1.inputCMD("cd " + cwd);
            sacP1.inputCMD("r " + modifiedPath.getFileName());
            sacP1.inputCMD("interpolate b 0");
            sacP1.inputCMD("w over");
        }
        try (SAC sacP2 = SAC.createProcess()) {
            // current directoryをうつす
            sacP2.inputCMD("cd " + cwd);
            // TODO: "cut b n" does not work in new SAC versions (102.0 and later?)
            sacP2.inputCMD("cut b n " + newNpts);
            sacP2.inputCMD("r " + modifiedPath.getFileName());
            sacP2.inputCMD("w over");
        }
        // ヘッダーの更新
        this.headerMap = SACUtil.readHeader(modifiedPath);
    }

    /**
     * @return (Path) Modified SAC path
     */
    Path getModifiedPath() {
        return modifiedPath;
    }
}
