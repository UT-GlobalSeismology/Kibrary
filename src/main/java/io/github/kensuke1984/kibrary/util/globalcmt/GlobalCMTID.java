package io.github.kensuke1984.kibrary.util.globalcmt;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

import io.github.kensuke1984.kibrary.util.sac.SACHeaderData;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 * Identifier of an event listed in Global CMT project.
 * <p>
 * This class is <b>IMMUTABLE</b>
 *
 * @author Kensuke Konishi
 * @version 0.1.1.2
 * @see <a href=http://www.globalcmt.org/> Global CMT project official page</a>
 */
public class GlobalCMTID implements Comparable<GlobalCMTID> {

    /**
     * recent Harvard ID yyyymmddhhmm[A-Za-z] 2004-
     */
    public static final Pattern RECENT_GLOBALCMTID_PATTERN =
            Pattern.compile("20[0-9]{2}(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])([01][0-9]|2[0-3])[0-5][0-9][A-Za-z]");
    /**
     * previous Harvard ID mmddyy[A-Za-z] 1976-2004
     */
    public static final Pattern PREVIOUS_GLOBALCMTID_PATTERN =
            Pattern.compile("(0[1-9]|1[0-2])(0[1-9]|[12][0-9]|3[01])[0789][0-9][A-Za-z]");

    /**
     * maximum length of ID
     */
    private static final int LENGTH = 13;

    private final String id;
    /**
     * if once {@link #getEvent()} is invoked, this holds it.
     */
    private volatile NDK ndk;

    /**
     * Create an instance for an input
     * If no ID exists for the input, throw {@link RuntimeException}
     *
     * @param idStr global cmt id
     */
    public GlobalCMTID(String idStr) {
        if (isGlobalCMTID(idStr)) id = idStr;
        else throw new IllegalArgumentException(idStr + " does not exist.");
    }

    /**
     * @param sacHeaderData must contain a valid ID in KEVNM
     * @return GlobalCMTID of the input sacHeaderData
     */
    public static GlobalCMTID of(SACHeaderData sacHeaderData) {
        return new GlobalCMTID(sacHeaderData.getSACString(SACHeaderEnum.KEVNM));
    }

    /**
     *
     * @param string global cmt id
     * @return if the string is contained in global cmt catalog
     */
    public static boolean isGlobalCMTID(String string) {
        return RECENT_GLOBALCMTID_PATTERN.matcher(string).matches() ||
                PREVIOUS_GLOBALCMTID_PATTERN.matcher(string).matches();
    }

    /**
     * @param args [Global CMT ID...]
     */
    public static void main(String[] args) {
        if (args.length == 0) throw new IllegalArgumentException("Usage: [Global CMT IDs]");
        for (String idString : args) {
            if (!isGlobalCMTID(idString)) {
                System.err.println(idString + " does not exist.");
                continue;
            }

            GlobalCMTID id = new GlobalCMTID(idString);
            GlobalCMTData event = id.getEvent();

            System.out.println("ID: " + id + " Mw: " + event.getCmt().getMw());
            System.out.println("Centroid Time: " + event.getCMTTime());
            System.out.println("Centroid location(latitude longitude radius): " + event.getCmtLocation());
        }
    }

    /**
     * When you want to create Events not contained in Global CMT Catalog, you
     * can make it by yourself and use this.
     *
     * @param catalogFile arbitrary file containing cmt catalog
     * @return {@link GlobalCMTData} written in catalogFile
     */
    public static Set<GlobalCMTData> readCatalog(Path catalogFile) {
        return new HashSet<>(GlobalCMTCatalog.read(catalogFile));
    }

    /**
     * Compares global CMT IDs by their ID using
     * {@link String#compareTo(String)}
     */
    @Override
    public int compareTo(GlobalCMTID o) {
        return id.compareTo(o.id);
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = prime * result + ((id == null) ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof GlobalCMTID)) return false;
        GlobalCMTID other = (GlobalCMTID) obj;
        if (id == null) {
            if (other.id != null) return false;
        } else if (!id.equals(other.id)) return false;
        return true;
    }

    @Override
    public String toString() {
        return id;
    }

    public String getPaddedString() {
        return StringUtils.rightPad(id, LENGTH);
    }

    /**
     * if there is a certain existing ID, then returns the {@link GlobalCMTData}
     * for the ID if not null will be returned.
     *
     * @return GlobalCMTData for this
     */
    public GlobalCMTData getEvent() {
        if (ndk == null) synchronized (this) {
            if (ndk == null) ndk = GlobalCMTCatalog.getNDK(this);
        }
        return ndk;
    }

}
