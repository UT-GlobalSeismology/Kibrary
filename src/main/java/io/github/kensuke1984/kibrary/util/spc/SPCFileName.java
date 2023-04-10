package io.github.kensuke1984.kibrary.util.spc;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;

import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * A name of a spectrum file made by DSM<br>
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
 * @version 0.2.0
 * @author anselme add network
 */
public abstract class SPCFileName extends File {

    //-------------------- generate a new name --------------------//

    /**
     * Genarates an SPC file name as String.
     * If the inputs are invalid, the generated name would be invalid, but the name will be returned nonetheless.
     * @param observerID
     * @param eventID
     * @param mode
     * @return
     */
    public static String generate(Observer observerID, GlobalCMTID eventID, String mode) {
        return observerID.toString() + "." + eventID.toString() + "_" + mode + ".spc";
    }

    //-------------------- create instance and read name --------------------//

    static boolean isFormatted(String name) {
        if (!name.endsWith(".spc")) return false;
        if (!name.endsWith("PSV.spc") && !name.endsWith("SH.spc")) {
            System.err.println("SPC file name must end with [PSV, SH].spc (psv, sh not allowed anymore).");
            return false;
        }
        String[] parts = name.split("\\.");
        if (parts.length != 3 && parts.length != 7) {
            System.err.println("SPC file name must be ObserverID.GlobalCMTID_(PSV, SV).spc or " +
                    "ObserverID.GlobalCMTID.type(par2, PF, PB .etc).x.y.(PSV, SH).spc");
            return false;
        }

        String station = name.split("\\.")[0].split("_")[0];
        if (parts.length == 3) {
            String network = name.split("\\.")[0].split("_")[1];
            // synthetics files have both station name and network name
            if (8 < station.length()) System.err.println(station + "Name of station cannot be over 8 characters");
            if (8 < network.length()) System.err.println(network + "Name of network cannot be over 8 characters");
        } else {
            // bp and fp files have only a station name
            if (8 < station.length()) {
                System.err.println("Name of station cannot be over 8 characters.");
                return false;
            }
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
        return fileName.split("\\.").length == 3;
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
     * @return type (PAR0, .., PARQ, synthetic) of the file.
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
     * @return ID of observer (station_network)
     */
    public abstract String getObserverID();

    /**
     * @return STATION code of the observer
     */
    public abstract String getStationCode(); // TODO: delete

    /**
     * @return NETWORK code of the observer
     * @author anselme
     */
    public abstract String getNetworkCode(); // TODO: delete

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
