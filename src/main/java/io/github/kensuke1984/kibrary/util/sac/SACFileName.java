package io.github.kensuke1984.kibrary.util.sac;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * Style of names of SAC files. (SAC: seismic analysis code)
 * <p>
 * This class is <b>IMMUTABLE.</b><br>
 * This class is a sub class of {@link File}. {@link #equals(Object)} and
 * {@link #hashCode()} are under {@link File}.
 * <p>
 * The names take the form:
 * <ul>
 * <li> (synthetic) "ObserverID.{@link GlobalCMTID}.{@link SACExtension}" </li>
 * <li> (parital) "ObserverID.{@link GlobalCMTID}.partialType.x.y.z.{@link SACExtension}" </li>
 * </ul>
 * ObserverID takes the form "station_network", where station and network are String with 8 or less characters.
 * x, y, z are also String.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 * @see <a href=http://ds.iris.edu/ds/nodes/dmc/forms/sac/>SAC</a>
 */
public class SACFileName extends File {

    private static final long serialVersionUID = 7479212925997274364L;

    private static final int SYN_FILE_PARTS = 3;
    private static final int PARTIAL_FILE_PARTS = 7;

    private String stationCode;
    private String networkCode;
    private GlobalCMTID globalCMTID;
    private SACExtension extension;
    private String x, y, z;
    private WaveformType sacType;

    //-------------------- generate a new name --------------------//

    /**
     * If the inputs are invalid, the generated name would be invalid, but the name will be returned nonetheless.
     * @param observerID
     * @param eventID
     * @param extension
     * @return
     */
    public static String generate(Observer observerID, GlobalCMTID eventID, SACExtension extension) {
        return observerID.toString() + "." + eventID.toString() + "." + extension;
    }
    public static String generate(SACFileName sacName, SACExtension extension) {
        return sacName.getObserverID() + "." + sacName.getGlobalCMTID().toString() + "." + extension;
    }

    //-------------------- create instance and read name --------------------//

    /**
     * @param parent {@link File} of a parent folder
     * @param child  name of a file
     */
    public SACFileName(File parent, String child) {
        super(parent, child);
        readName(child);
    }

    /**
     * @param parent path of a parent folder
     * @param child file name
     */
    public SACFileName(String parent, String child) {
        super(parent, child);
        readName(child);
    }

    /**
     * @param pathname path of a file
     */
    public SACFileName(String pathname) {
        super(pathname);
        readName(getName());
    }

    /**
     * @param uri of a file
     */
    public SACFileName(URI uri) {
        super(uri);
        readName(getName());
    }

    /**
     * @param path {@link Path} of a file
     */
    public SACFileName(Path path) {
        this(path.toString());
    }

    /**
     * Check whether the input file has a valid SAC file name.
     * @param path (Path) File to check.
     * @return (boolean) Whether the file is a valid {@link SACFileName}.
     */
    public static boolean isSacFileName(Path path) {
        return isSacFileName(path.getFileName().toString());
    }

    /**
     * Check whether the input file name is a valid SAC file name.
     * @param fileName (String) Name of file to check.
     * @return (boolean) Whether the name is a valid {@link SACFileName}.
     */
    public static boolean isSacFileName(String fileName) {
        String[] parts = fileName.split("\\.");
        String[] fields = parts[0].split("_");

        if (parts.length != SYN_FILE_PARTS && parts.length != PARTIAL_FILE_PARTS)
            return false;

        // must include station and network
        if (fields.length != 2)
            return false;

        // station and network must be 8 letters or shorter.
        if (8 < fields[0].length() || 8 < fields[1].length())
            return false;

        // test if it is a global cmt id
        if (!GlobalCMTID.isGlobalCMTID(parts[1]))
            return false;

        try {
            SACExtension.valueOf(parts[parts.length - 1]);
        } catch (Exception e) {
            return false;
        }

        return true;
    }

    private void readName(String fileName) {
        if (!isSacFileName(fileName))
            throw new IllegalArgumentException(fileName + " is an invalid sac file name");

        String[] parts = fileName.split("\\.");

        String eventID = parts[1];
        if (!GlobalCMTID.isGlobalCMTID(eventID))
            throw new IllegalArgumentException(fileName + " contains an invalid Global CMT ID");
        globalCMTID = new GlobalCMTID(eventID);

        stationCode = parts[0].split("_")[0];
        networkCode = parts[0].split("_")[1];

        x = (parts.length != 7) ? null : parts[3];
        y = (parts.length != 7) ? null : parts[4];
        z = (parts.length != 7) ? null : parts[5];

        extension = SACExtension.valueOf(parts[parts.length - 1]);
        if (parts.length == SYN_FILE_PARTS) {
            sacType = extension.isOBS() ? WaveformType.OBS : WaveformType.SYN;
        } else {
            sacType = WaveformType.PARTIAL;
        }
    }


    //-------------------- get info of a certain instance --------------------//

    @Override
    public String toString() {
        return getName();
    }

    /**
     * @return {@link SACExtension} of this sacfile name
     */
    public SACExtension getExtension() {
        return extension;
    }

    public boolean isConvolved() {
        return extension.isConvoluted();
    }

    public SACComponent getComponent() {
        return extension.getComponent();
    }

    public WaveformType getSacType() {
        return sacType;
    }

    /**
     * @return if it is observed
     */
    public boolean isOBS() {
        return extension.isOBS();
    }

    /**
     * @return if it is synthetic (no partial included)
     */
    public boolean isSYN() {
        return !extension.isOBS() && !extension.isTemporalPartial();
    }

    /**
     * @return if it is a time partial
     * @author anselme
     */
    public boolean isTemporalPartial() {
        return extension.isTemporalPartial();
    }

    public GlobalCMTID getGlobalCMTID() {
        return globalCMTID;
    }

    public String getObserverID() {
        return stationCode + "_" + networkCode;
    }

    @Deprecated
    public String getStationCode() { // TODO: delete
        return stationCode;
    }

    @Deprecated
    public String getNetworkCode() { // TODO: delete
        return networkCode;
    }

    public String getX() {
        return x;
    }

    public String getY() {
        return y;
    }

    public String getZ() {
        return z;
    }


    //-------------------- read data of this name --------------------//

    /**
     * @return (immutable) SACHeaderData of this file
     * @throws IOException if an I/O error occurs
     */
    public SACHeaderAccess readHeader() throws IOException {
        return new SACHeader(this);
    }

    /**
     * Reads header. Any exceptions are suppressed and null is returned instead.
     * @return (immutable) SACHeaderData of this file, or null on failure
     *
     * @author otsuru
     * @since 2022/4/22
     */
    public SACHeaderAccess readHeaderWithNullOnFailure() {
        try {
            return readHeader();
        } catch (Exception e) {
            System.err.println(this.toString() + " is an invalid SAC file.");
            return null;
        }
    }

    /**
     * @return (immutable) SACData of this file
     * @throws IOException if an I/O error occurs
     */
    public SACFileAccess read() throws IOException {
        return new SACFile(this);
    }

}
