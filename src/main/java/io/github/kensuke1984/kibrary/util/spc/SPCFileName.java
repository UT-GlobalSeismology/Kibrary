package io.github.kensuke1984.kibrary.util.spc;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * A name of a spectrum file made by DSM.
 * <p>
 * The names take the form:
 * <ul>
 * <li> Synthetic: "ObserverID.GlobalCMTID.(PSV,SV).spc" </li>
 * <li> Partial derivatives: "ReceiverID.SourceID.type(MU1D,PF,UB,etc.).x.y.(PSV,SH).spc" </li>
 * </ul>
 * where ObserverID is "station_network".
 * In synthetic files, 'station' and 'network' must be 8 or less letters.
 * 'PSV', 'SH' must be upper case.
 *
 * @author Kensuke Konishi
 * @version 0.2.0
 * @author anselme add network
 */
public abstract class SPCFileName extends File {

    static final int SYN_FILE_PARTS = 4;
    static final int PARTIAL_FILE_PARTS = 7;

    //-------------------- generate a new name --------------------//

    /**
     * Genarates an SPC file name as String.
     * If the inputs are invalid, the generated name would be invalid, but the name will be returned nonetheless.
     * @param observerID
     * @param eventID
     * @param mode
     * @return
     */
    public static String generate(Observer observerID, GlobalCMTID eventID, SPCMode mode) {
        return observerID.toString() + "." + eventID.toString() + "." + mode + ".spc";
    }

    //-------------------- create instance and read name --------------------//

    static boolean isFormatted(String name) {
        if (!name.endsWith(".spc")) return false;
        if (!name.endsWith("PSV.spc") && !name.endsWith("SH.spc")) {
            System.err.println("SPC file name must end with '[PSV, SH].spc'.");
            return false;
        }
        String[] parts = name.split("\\.");
        if (parts.length != SYN_FILE_PARTS && parts.length != PARTIAL_FILE_PARTS) {
            System.err.println("SPC file name must be 'ObserverID.GlobalCMTID.(PSV, SV).spc' or " +
                    "'ReceiverID.SourceID.type(MU1D,PF,UB,etc.).x.y.(PSV,SH).spc'");
            return false;
        }

        if (parts.length == SYN_FILE_PARTS) {
            // synthetics files have both station name and network name
            String station = name.split("\\.")[0].split("_")[0];
            String network = name.split("\\.")[0].split("_")[1];
            if (8 < station.length()) System.err.println(station + "Name of station cannot be over 8 characters");
            if (8 < network.length()) System.err.println(network + "Name of network cannot be over 8 characters");
        }

        return true;
    }

    /**
     * @param path for check
     * @return if the filePath is formatted.
     */
    public static boolean isFormatted(Path path) {
        return isFormatted(path.getFileName().toString());
    }

    /**
     * @param fileName file name for chack
     * @return if the fileName is synthetic (not partial)
     */
    static boolean isSynthetic(String fileName) {
        return fileName.split("\\.").length == SYN_FILE_PARTS;
    }

    SPCFileName(String pathname) {
        super(pathname);
    }

    SPCFileName(String parent, String child) {
        super(parent, child);
    }

    SPCFileName(File parent, String child) {
        super(parent, child);
    }

    SPCFileName(URI uri) {
        super(uri);
    }

    SPCFileName(Path path) {
        this(path.toString());
    }

    //-------------------- get info of a certain instance --------------------//

    /**
     * @return if this is synthetic (not partial)
     */
    public abstract boolean isSynthetic();

    /**
     * @return type (MU1D,PF,UB,SYNTHETIC,etc.) of the file.
     */
    public abstract SPCType getFileType();

    /**
     * @return psv or sh
     */
    public abstract SPCMode getMode();

    /**
     * @return ID of source
     */
    public abstract String getSourceID();

    /**
     * @return ID of receiver (for observers, STATION_NETWORK)
     */
    public abstract String getReceiverID();

    /**
     * @return the PSV/SH pair file name
     */
    public abstract String pairFileName();


    //-------------------- read data of this name --------------------//

    /**
     * @return output of DSM
     * @throws IOException if an I/O error occurs
     */
    public abstract SPCFileAccess read() throws IOException;

}
