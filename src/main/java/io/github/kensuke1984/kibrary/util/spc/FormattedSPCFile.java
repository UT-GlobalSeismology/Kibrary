package io.github.kensuke1984.kibrary.util.spc;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

/**
 * A <b>formatted</b> name of a spectrum file made by DSM<br>
 * <p>
 * This class is <b>IMMUTABLE</b>.
 * <p>
 * The names take the form:
 * <ul>
 * <li> Synthetic: "ObserverID.GlobalCMTID_(PSV, SV).spc" </li>
 * <li> Partial derivatives: "station.GlobalCMTID.type(par2, PF, PB .etc).x.y.(PSV, SH).spc" </li>
 * </ul>
 * where ObserverID is "station_network".
 * 'PSV', 'SH' must be upper case. 'station' and 'network' must be 8 or less letters.
 *
 * @author Kensuke Konishi
 * @version 0.0.1.1
 * @author anselme add network
 */
public class FormattedSPCFile extends SPCFileName {

    private static final long serialVersionUID = -6340811322023603513L;

    /**
     * spheroidal mode PSV, toroidal mode SH
     */
    private SPCMode mode;
    /**
     * PB: backward or PF: forward, PAR2: mu
     */
    private SPCType fileType;
    private String x, y;
    private String stationCode;
    private String networkCode;
    private String sourceID;

    //-------------------- create instance and read name --------------------//

    /**
     * @param parent {@link File} of a parent folder of the spectrum file
     * @param child  a name of spectrum file
     */
    public FormattedSPCFile(File parent, String child) {
        super(parent, child);
        readName(getName());
    }

    /**
     * @param parent of a parent folder of the spectrum file
     * @param child  a name of spectrum file
     */
    public FormattedSPCFile(String parent, String child) {
        super(parent, child);
        readName(getName());
    }

    /**
     * @param pathname path of a spectrum file
     */
    public FormattedSPCFile(String pathname) {
        super(pathname);
        readName(getName());
    }

    /**
     * @param path {@link Path} of a spectrum file
     */
    public FormattedSPCFile(Path path) {
        this(path.toString());
    }

    public FormattedSPCFile(URI uri) {
        super(uri);
        readName(getName());
    }

    /**
     * 入力ファイルのSpcFileTypeを返す
     *
     * @param fileName name of SPC file
     * @return which par or syn...なんのスペクトルファイルか
     */
    private static SPCType getFileType(String fileName) {
        if (fileName.split("\\.").length != 7) return SPCType.SYNTHETIC;
        return SPCType.valueOf(fileName.split("\\.")[2].replace("par", "PAR"));
    }

    /**
     * @param fileName name of SPC file
     * @return PSV or SH
     */
    private static SPCMode getMode(String fileName) {
        return fileName.endsWith("PSV.spc") ? SPCMode.PSV : SPCMode.SH;
    }

    /**
     * @param fileName name of spc file
     * @return event ID
     */
    private static String getEventID(String fileName) {
        switch (fileName.split("\\.").length) {
            case 3:
                return fileName.split("\\.")[1].split("_")[0];
//                return fileName.split("\\.")[1].replace("PSV", "").replace("SH", "");
            case 7:
                return fileName.split("\\.")[1];
            default:
                throw new RuntimeException("Unexpected");
        }
    }

    private static String getX(String fileName) {
        String[] parts = fileName.split("\\.");
        return parts.length != 7 ? null : parts[3];
    }

    private static String getY(String fileName) {
        String[] parts = fileName.split("\\.");
        return parts.length != 7 ? null : parts[4];
    }

    /**
     * @param path for check
     * @return if the filePath is formatted.
     */
    public static boolean isFormatted(Path path) {
        return isFormatted(path.getFileName().toString());
    }

    /**
     * @param fileName
     * @author anselme add network
     */
    private void readName(String fileName) {
        if (!isFormatted(fileName)) throw new IllegalArgumentException(fileName + " is not a valid Spcfile name.");
//        observerID = fileName.split("\\.")[0];
        fileType = getFileType(fileName);
        mode = getMode(fileName);
        sourceID = getEventID(fileName);
        stationCode = fileName.split("\\.")[0].split("_")[0];
        if (fileType.equals(SPCType.PB) || fileType.equals(SPCType.PF))
            networkCode = null;
        else
            networkCode = fileName.split("\\.")[0].split("_")[1];
        x = getX(fileName);
        y = getY(fileName);
    }


    //-------------------- get info of a certain instance --------------------//

    @Override
    public boolean isSynthetic() {
        return isSynthetic(getName());
    }

    @Override
    public SPCType getFileType() {
        return fileType;
    }

    @Override
    public SPCMode getMode() {
        return mode;
    }

    @Override
    public String getSourceID() {
        return sourceID;
    }

    @Override
    public String getObserverID() {
        return stationCode + "_" + networkCode;
    }

    @Override
    public String getStationCode() {
        return stationCode;
    }

    @Override
    public String getNetworkCode() {
        if (fileType.equals(SPCType.PB) || fileType.equals(SPCType.PF))
            throw new RuntimeException("PB and PF waveforms have no network");
        return networkCode;
    }

    public String getX() {
        return x;
    }

    public String getY() {
        return y;
    }


    //-------------------- read data of this name --------------------//

    @Override
    public DSMOutput read() throws IOException {
        return Spectrum.getInstance(this);
    }

}
