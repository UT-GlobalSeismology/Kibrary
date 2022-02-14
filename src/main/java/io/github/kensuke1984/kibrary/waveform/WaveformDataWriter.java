package io.github.kensuke1984.kibrary.waveform;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;

/**
 * Writer of BasicDataset and PartialDataset.
 * <p>
 * This class creates a new set of ID and waveform files in binary-format.
 *
 */
public class WaveformDataWriter implements Closeable, Flushable {
    /**
     * id information file
     */
    private final Path idPath;
    /**
     * wavedata file
     */
    private final Path dataPath;
    /**
     * Because the header part is decided when this is constructed, the mode is
     * also decided(0: BasicID, 1: PartialID)
     */
    private final int mode;
    /**
     * stream for id
     */
    private DataOutputStream idStream;
    /**
     * stream for wavedata
     */
    private DataOutputStream dataStream;
    /**
     * index map for stations
     */
    private Map<Observer, Integer> observerMap;
    /**
     * index map for global CMT IDs
     */
    private Map<GlobalCMTID, Integer> globalCMTIDMap;
    /**
     * index map for perturbation location
     */
    private Map<FullPosition, Integer> perturbationLocationMap;
    /**
     * index map for phase name
     */
    private Map<Phase, Integer> phaseMap;
    /**
     * index for period ranges
     */
    private double[][] periodRanges;
    /**
     * The file size (byte). (should be StartByte)
     */
    private long dataLength;
    /**
     * This constructor is only for BasicID. All write ID must have a station,
     * a Global CMT ID and period ranges in the input ones.
     *
     * @param idPath         Path for ID file (must not exist)
     * @param dataPath       Path for data file (must not exist)
     * @param stationSet     must contain all information of the IDs to write
     * @param globalCMTIDSet must contain all information of the IDs to write
     * @param periodRanges   must contain all information of the IDs to write. If you want
     *                       to use ranges [10, 30] and [50,100] then the periodRanges
     *                       should be new double[][]{{10,30},{50,100}}
     * @param phases		 Array of phase names
     * @throws IOException if an error occurs
     */
    public WaveformDataWriter(Path idPath, Path dataPath, Set<Observer> stationSet, Set<GlobalCMTID> globalCMTIDSet,
            double[][] periodRanges, Phase[] phases) throws IOException {
        this(idPath, dataPath, stationSet, globalCMTIDSet, periodRanges, phases, null);
    }

    /**
     * This constructor is only for PartialID. All write ID must have a
     * station, a Global CMT ID and period ranges in the input ones.
     *
     * @param idPath             Path for ID file (must not exist)
     * @param dataPath           Path for data file (must not exist)
     * @param observerSet         must contain all information of the IDs to write
     * @param globalCMTIDSet     must contain all information of the IDs to write
     * @param periodRanges       must contain all information of the IDs to write. If you want
     *                           to use ranges [10, 30] and [50,100] then the periodRanges
     *                           should be new double[][]{{10,30},{50,100}}
     * @param phases			 Array of phase names
     * @param perturbationPoints must contain all information of the IDs to write
     * @throws IOException if an error occurs
     */
    public WaveformDataWriter(Path idPath, Path dataPath, Set<Observer> observerSet, Set<GlobalCMTID> globalCMTIDSet,
            double[][] periodRanges, Phase[] phases, Set<FullPosition> perturbationPoints) throws IOException {
        this.idPath = idPath;
        this.dataPath = dataPath;
        if (checkDuplication(periodRanges)) throw new RuntimeException("Input periodRanges have duplication.");
        this.periodRanges = periodRanges;
        idStream = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(idPath)));
        dataStream = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(dataPath)));
        dataLength = Files.size(dataPath);
        idStream.writeShort(observerSet.size());
        idStream.writeShort(globalCMTIDSet.size());
        idStream.writeShort(periodRanges.length);
        idStream.writeShort(phases.length);
        if (perturbationPoints != null) idStream.writeShort(perturbationPoints.size());
        makeObserverMap(observerSet);
        makeGlobalCMTIDMap(globalCMTIDSet);
        for (int i = 0; i < periodRanges.length; i++) {
            idStream.writeDouble(periodRanges[i][0]);
            idStream.writeDouble(periodRanges[i][1]);
        }
        makePhaseMap(phases);
        if (perturbationPoints != null) makePerturbationMap(perturbationPoints);
        mode = (perturbationPoints == null ? 0 : 1);
    }

    private static boolean checkDuplication(double[][] periodRanges) {
        for (int i = 0; i < periodRanges.length - 1; i++)
            for (int j = i + 1; j < periodRanges.length; j++)
                if (Arrays.equals(periodRanges[i], periodRanges[j])) return true;
        return false;
    }

    public Path getIDPath() {
        return idPath;
    }

    public Path getDataPath() {
        return dataPath;
    }

    @Override
    public void close() throws IOException {
        idStream.close();
        dataStream.close();
    }

    @Override
    public void flush() throws IOException {
        idStream.flush();
        dataStream.flush();
    }

    private void makeGlobalCMTIDMap(Set<GlobalCMTID> globalCMTIDSet) throws IOException {
        int i = 0;
        globalCMTIDMap = new HashMap<>();
        for (GlobalCMTID id : globalCMTIDSet) {
            globalCMTIDMap.put(id, i++);
            idStream.writeBytes(StringUtils.rightPad(id.toString(), 15));
        }
    }

    private void makePerturbationMap(Set<FullPosition> perturbationMap) throws IOException {
        int i = 0;
        perturbationLocationMap = new HashMap<>();
        for (FullPosition loc : perturbationMap) {
            perturbationLocationMap.put(loc, i++);
            idStream.writeDouble(loc.getLatitude());
            idStream.writeDouble(loc.getLongitude());
            idStream.writeDouble(loc.getR());
        }
    }

    private void makePhaseMap(Phase[] phases) throws IOException {
        int i = 0;
        phaseMap = new HashMap<>();
        for (Phase phase : phases)	{
            phaseMap.put(phase, i++);
            idStream.writeBytes(StringUtils.rightPad(phase.toString(), 16));
        }
    }

    private void makeObserverMap(Set<Observer> observerSet) throws IOException {
        int i = 0;
        observerMap = new HashMap<>();
        for (Observer observer : observerSet) {
            observerMap.put(observer, i++);
            idStream.writeBytes(StringUtils.rightPad(observer.getStation(), 8));
            idStream.writeBytes(StringUtils.rightPad(observer.getNetwork(), 8));
            HorizontalPosition pos = observer.getPosition();
            idStream.writeDouble(pos.getLatitude());
            idStream.writeDouble(pos.getLongitude());
        }
    }

    /**
     * Writes a waveform
     *
     * @param data waveform data
     */
    private void addWaveform(double[] data) throws IOException {
        for (double aData : data) dataStream.writeDouble(aData);
        dataLength += 8 * data.length;
    }

    /**
     * An ID information contains<br>
     * obs or syn(1)<br>
     * observer number(2)<br>
     * event number(2)<br>
     * component(1)<br>
     * period range(1) <br>
     * phases numbers(10*2)<br>
     * start time(4)<br>
     * number of points(4)<br>
     * sampling hz(4) <br>
     * convoluted(or observed) or not(1)<br>
     * position of a waveform for the ID in the datafile(8)
     *
     * @param basicID StartByte will be ignored and set properly in the write file.
     * @throws IOException if an I/O error occurs
     */
    public synchronized void addBasicID(BasicID basicID) throws IOException {
        if (mode != 0) throw new RuntimeException("BasicID please, would you.");

        Integer ista = observerMap.get(basicID.observer);
        if (ista == null) {
            throw new RuntimeException("No such observer: " + basicID.observer + " " + basicID);
        }

        switch (basicID.TYPE) { // if it is obs 1Byte
        case OBS:
            idStream.writeBoolean(true);
            break;
        case SYN:
            idStream.writeBoolean(false);
            break;
        default:
            throw new RuntimeException("This is a partial derivative.");
        }
        long startByte = dataLength;
        addWaveform(basicID.getData());
        idStream.writeShort(ista);
        idStream.writeShort(globalCMTIDMap.get(basicID.event));
        idStream.writeByte(basicID.COMPONENT.valueOf());
        idStream.writeByte(getIndexOfRange(basicID.MIN_PERIOD, basicID.MAX_PERIOD));
        Phase[] phases = basicID.PHASES;
        for (int i = 0; i < 10; i++) { // 10 * 2 Byte
            if (i < phases.length) {
                idStream.writeShort(phaseMap.get(phases[i]));
            }
            else
                idStream.writeShort(-1);
        }

        // 4Byte * 3
        idStream.writeFloat((float) basicID.getStartTime()); // start time
        idStream.writeInt(basicID.getNpts()); // number of points
        idStream.writeFloat((float) basicID.getSamplingHz()); // sampling Hz

        // if its convolute  true for obs
        idStream.writeBoolean(basicID.getWaveformType() == WaveformType.OBS || basicID.CONVOLUTE); // 1Byte
        idStream.writeLong(startByte); // data address 8 Byte

    }

    private int getIndexOfRange(double min, double max) {
        for (int i = 0; i < periodRanges.length; i++) // TODO
            if (Math.abs(periodRanges[i][0] - min) < 0.000000001 && Math.abs(periodRanges[i][1] - max) < 0.000000001)
                return i;
        throw new RuntimeException("A range is N/A");
    }

    /**
     * @param partialID {@link PartialID} must contain waveform data. StartByte will
     *                  be ignored and set properly in the write file.
     * @throws IOException if an I/O error occurs
     */
    public synchronized void addPartialID(PartialID partialID) throws IOException {
        if (partialID.TYPE != WaveformType.PARTIAL) throw new RuntimeException(
                    "This is not a partial derivative. " + Thread.currentThread().getStackTrace()[1].getMethodName());
        if (mode != 1) throw new RuntimeException("No Partial please, would you.");
        long startByte = dataLength;
        addWaveform(partialID.getData());
        idStream.writeShort(observerMap.get(partialID.observer));
        idStream.writeShort(globalCMTIDMap.get(partialID.event));
        idStream.writeByte(partialID.COMPONENT.valueOf());
        idStream.writeByte(getIndexOfRange(partialID.MIN_PERIOD, partialID.MAX_PERIOD));
        Phase[] phases = partialID.PHASES;
        for (int i = 0; i < 10; i++) { // 10 * 2 Byte
            if (i < phases.length) {
                idStream.writeShort(phaseMap.get(phases[i]));
            }
            else
                idStream.writeShort(-1);
        }
        idStream.writeFloat((float) partialID.START_TIME); // start time 4 Byte
        idStream.writeInt(partialID.NPTS); // データポイント数 4 Byte
        idStream.writeFloat((float) partialID.SAMPLINGHZ); // sampling Hz 4 Byte
        // convolutionされているか
        idStream.writeBoolean(partialID.CONVOLUTE); // 1Byte
        idStream.writeLong(startByte); // データの格納場所 8 Byte
        // partial type 1 Byte
        idStream.writeByte(partialID.getPartialType().getValue());
        idStream.writeShort(perturbationLocationMap.get(partialID.POINT_LOCATION));
    }
}
