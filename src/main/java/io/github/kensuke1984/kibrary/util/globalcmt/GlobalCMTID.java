package io.github.kensuke1984.kibrary.util.globalcmt;

import java.io.IOException;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.lang3.StringUtils;

import io.github.kensuke1984.kibrary.Summon;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderAccess;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 * Identifier of an event listed in Global CMT project.
 * <p>
 * This class is <b>IMMUTABLE</b>.
 * When a set of GlobalCMTIDs is sorted, they will be in dictionary order, not order of event time.
 *
 * @author Kensuke Konishi
 * @since version 0.1.1.2
 * @see <a href=http://www.globalcmt.org/>Global CMT project official page</a>
 */
public final class GlobalCMTID implements Comparable<GlobalCMTID> {

    /**
     * Recent Harvard ID, used 2004-. yyyymmddhhmm[A-Za-z]
     */
    public static final Pattern RECENT_GLOBALCMTID_PATTERN = Pattern.compile("[0-9]{12}[A-Za-z]");
    /**
     * Previous Harvard ID, used 1976-2004. mmddyy[A-Za-z]
     */
    public static final Pattern PREVIOUS_GLOBALCMTID_PATTERN = Pattern.compile("[0-9]{6}[A-Za-z]");

    /**
     * Maximum length to allow for an event ID.
     */
    public static final int MAX_LENGTH = 15;
    /**
     * Maximum length of ID.
     */
    private static final int LENGTH = 13;

    /**
     * String of ID.
     */
    private final String idString;
    /**
     * If {@link #getEventData()} is once invoked, this holds it.
     */
    private volatile NDK ndk;

    /**
     * Create an instance for an input.
     * If input does not match the GlobalCMTID pattern, throw {@link IllegalArgumentException}.
     * Existence in catalog is not checked here.
     *
     * @param idString (String) Global CMT ID.
     */
    public GlobalCMTID(String idString) {
        if (isGlobalCMTID(idString)) this.idString = idString;
        else throw new IllegalArgumentException(idString + " does not match GlobalCMTID pattern.");
    }

    /**
     * Construct ID from {@link SACHeaderAccess}.
     * @param sacHeaderData ({@link SACHeaderAccess}) A SAC header. Must contain a valid ID in KEVNM.
     * @return ({@link GlobalCMTID}) Created instance.
     */
    public static GlobalCMTID of(SACHeaderAccess sacHeaderData) {
        return new GlobalCMTID(sacHeaderData.getSACString(SACHeaderEnum.KEVNM));
    }

    /**
     * Checks whether a String matches the GlobalCMT ID pattern.
     * @param string (String) String to check.
     * @return (boolean) Whether the String matches the GlobalCMT ID pattern.
     */
    public static boolean isGlobalCMTID(String string) {
        return RECENT_GLOBALCMTID_PATTERN.matcher(string).matches() ||
                PREVIOUS_GLOBALCMTID_PATTERN.matcher(string).matches();
    }

    /**
     * Checks whether this ID exists in the GlobalCMT catalog.
     * @return (boolean) Whether this ID exists in catalog.
     *
     * @author otsuru
     * @since 2023/12/6
     */
    public boolean exists() {
        return GlobalCMTCatalog.contains(this);
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = prime * result + ((idString == null) ? 0 : idString.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (!(obj instanceof GlobalCMTID)) return false;
        GlobalCMTID other = (GlobalCMTID) obj;
        if (idString == null) {
            if (other.idString != null) return false;
        } else if (!idString.equals(other.idString)) return false;
        return true;
    }

    /**
     * Compares global CMT IDs by their ID using {@link String#compareTo(String)}
     */
    @Override
    public int compareTo(GlobalCMTID o) {
        return idString.compareTo(o.idString);
    }

    @Override
    public String toString() {
        return idString;
    }

    /**
     * @return (String) GCMTID padded with spaces at the right.
     */
    public String toPaddedString() {
        return StringUtils.rightPad(idString, LENGTH);
    }

    /**
     * If there is a certain existing ID, then returns the {@link GlobalCMTAccess} for this ID.
     * If not, null will be returned.
     *
     * @return ({@link GlobalCMTAccess}) GlobalCMT data for this ID
     */
    public GlobalCMTAccess getEventData() {
        if (ndk == null) synchronized (this) {
            if (ndk == null) ndk = GlobalCMTCatalog.getNDK(this);
        }
        return ndk;
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Displays information of input event IDs.
     * Results are written in the standard output.
     * @param args Options.
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        Options options = defineOptions();
        try {
            run(Summon.parseArgs(options, args));
        } catch (ParseException e) {
            Summon.showUsage(options);
        }
    }

    /**
     * To be called from {@link Summon}.
     * @return options
     */
    public static Options defineOptions() {
        Options options = Summon.defaultOptions();

        options.addOption(Option.builder("i").longOpt("ids").hasArg().argName("ids").required()
                .desc("Global CMT IDs, listed using commas.").build());
        return options;
    }

    /**
     * To be called from {@link Summon}.
     * @param cmdLine options
     * @throws IOException
     */
    public static void run(CommandLine cmdLine) throws IOException {
        String[] idStrings = cmdLine.getOptionValue("i").split(",");
        for (String idString : idStrings) {
            if (!isGlobalCMTID(idString)) {
                System.err.println(idString + " does not exist.");
                continue;
            }

            GlobalCMTID id = new GlobalCMTID(idString);
            GlobalCMTAccess event = id.getEventData();

            System.out.println("ID: " + id + "   Mw: " + event.getCmt().getMw());
            System.out.println("Centroid time: " + event.getCMTTime());
            System.out.println("Centroid position (latitude longitude radius): " + event.getCmtPosition());
        }
    }

}
