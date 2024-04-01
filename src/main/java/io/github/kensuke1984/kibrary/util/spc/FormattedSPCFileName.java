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
 * @since a long time ago
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
        String[] parts = fileName.split("\\.");

        receiverID = parts[0];
        sourceID = parts[1];
        fileType = (parts.length == SYN_FILE_PARTS ? SPCType.SYNTHETIC : SPCType.valueOf(fileName.split("\\.")[2]));
        x = (parts.length == SYN_FILE_PARTS ? null : parts[3]);
        y = (parts.length == SYN_FILE_PARTS ? null : parts[4]);
        mode = SPCMode.valueOf(parts[parts.length - 2]);
    }

    //-------------------- get info of this certain instance --------------------//

    @Override
    public boolean isSynthetic() {
        return fileType == SPCType.SYNTHETIC;
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
     * (PB, UB) Return observer code (source of back propagate wave; STATION_NETWORK)
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
     * (else) Return obsever code (STATION_NETWORK)
     */
    @Override
    public String getReceiverID() {
        return receiverID;
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
