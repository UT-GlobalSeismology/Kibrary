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
 * <li> Synthetic: "ObserverID.GlobalCMTID.(PSV,SV).spc" </li>
 * <li> Partial derivatives: "ReceiverID.SourceID.type(MU1D,PF,UB,etc.).x.y.(PSV,SH).spc" </li>
 * </ul>
 * where ObserverID is "station_network".
 * 'PSV', 'SH' must be upper case. 'station' and 'network' must be 8 or less letters.
 *
 * @author Kensuke Konishi
 */
public class FormattedSPCFileName extends SPCFileName {

    private static final long serialVersionUID = -6340811322023603513L;

    /**
     * toroidal mode: SH, spheroidal mode: PSV
     */
    private SPCMode mode;
    /**
     * MU1D: mu, PF: forward u'(t), UB: backward u(t), etc.
     */
    private SPCType fileType;
    private String x, y;
    private String receiverID;
    private String sourceID;

    //-------------------- create instance and read name --------------------//

    /**
     * @param parent {@link File} of a parent folder of the spectrum file
     * @param child  a name of spectrum file
     */
    public FormattedSPCFileName(File parent, String child) {
        super(parent, child);
        readName(getName());
    }

    /**
     * @param parent of a parent folder of the spectrum file
     * @param child  a name of spectrum file
     */
    public FormattedSPCFileName(String parent, String child) {
        super(parent, child);
        readName(getName());
    }

    /**
     * @param pathname path of a spectrum file
     */
    public FormattedSPCFileName(String pathName) {
        super(pathName);
        readName(getName());
    }

    /**
     * @param path {@link Path} of a spectrum file
     */
    public FormattedSPCFileName(Path path) {
        this(path.toString());
    }

    public FormattedSPCFileName(URI uri) {
        super(uri);
        readName(getName());
    }

    /**
     * @param fileName
     * @author anselme add network
     */
    private void readName(String fileName) {
        if (!isFormatted(fileName)) throw new IllegalArgumentException(fileName + " is not a valid SPC file name.");
        fileType = getFileType(fileName);
        mode = getMode(fileName);
        sourceID = getSourceID(fileName);
        receiverID = getReceiverID(fileName);
        x = getX(fileName);
        y = getY(fileName);
    }

    /**
     * @param fileName name of SPC file
     * @return which par or syn...なんのスペクトルファイルか
     */
    private static SPCType getFileType(String fileName) {
        if (fileName.split("\\.").length == SYN_FILE_PARTS) return SPCType.SYNTHETIC;
        else return SPCType.valueOf(fileName.split("\\.")[2]);
    }

    /**
     * @param fileName name of SPC file
     * @return PSV or SH
     */
    private static SPCMode getMode(String fileName) {
        String[] parts = fileName.split("\\.");
        return SPCMode.valueOf(parts[parts.length - 2]);
    }

    /**
     * @param fileName name of spc file
     * @return event ID
     */
    private static String getSourceID(String fileName) {
        return fileName.split("\\.")[1];
    }

    private static String getReceiverID(String fileName) {
        return fileName.split("\\.")[0];
    }

    private static String getX(String fileName) {
        String[] parts = fileName.split("\\.");
        return parts.length == SYN_FILE_PARTS ? null : parts[3];
    }

    private static String getY(String fileName) {
        String[] parts = fileName.split("\\.");
        return parts.length == SYN_FILE_PARTS ? null : parts[4];
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

    /**
     * (PB, UB) Return observer code (source of back propagate wave)
     * <p>
     * (else) Return source ID
     */
    @Override
    public String getSourceID() {
        return sourceID;
    }

    /**
     * (PB, PF, UB, UF) Return perturbation point code
     * <p>
     * (else) Return obsever code
     */
    @Override
    public String getReceiverID() {
        return receiverID;
    }

    @Override
    @Deprecated
    public String getStationCode() {
        return null;
    }

    @Override
    @Deprecated
    public String getNetworkCode() {
        return null;
    }

    public String getX() {
        return x;
    }

    public String getY() {
        return y;
    }

    @Override
    public String pairFileName() {
        if (mode == SPCMode.PSV)
            return getName().replace("PSV.spc", "SH.spc");
        else
            return getName().replace("SH.spc", "PSV.spc");
    }

    //-------------------- read data of this name --------------------//

    @Override
    public SPCFileAccess read() throws IOException {
        return SPCFile.getInstance(this);
    }

}
