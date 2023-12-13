package io.github.kensuke1984.kibrary.util.globalcmt;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.apache.commons.lang3.StringUtils;

import io.github.kensuke1984.kibrary.source.MomentTensor;
import io.github.kensuke1984.kibrary.source.SourceTimeFunctionType;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * NDK format in Global CMT Catalog.
 * This class is <b>IMMUTABLE</b>.
 * <p>
 * ==================================
 * <p>
 * This file contains an explanation of the "ndk" file format used to store and
 * distribute the Global Centroid-Moment-Tensor (CMT) catalog (formerly the
 * Harvard CMT catalog).
 * <p>
 * The "ndk" format replaces the earlier "dek" format.
 * <p>
 * ============================================================================
 * <p>
 * 12345678901234567890123456789012345678901234567890123456789012345678901234567890
 * <p>
 * The format is ASCII and uses five 80-character lines per earthquake.
 * <p>
 * ============================================================================
 * <p>
 * Notes (additional information):
 * <p>
 * (1) CMT event names follow two conventions. Older events use an 8-character
 * name with the structure XMMDDYYZ, where MMDDYY represents the date of the
 * event, Z is a letter (A-Z followed by a-z) distinguishing different events on
 * the same day, and X is a letter (B,M,Z,C,...) used to identify the types of
 * data used in the inversion. Newer events use 14-character event names with
 * the structure XYYYYMMDDhhmmZ, in which the time is given to greater
 * precision, and the initial letter is limited to four possibilities: B - body
 * waves only, S - surface waves only, M - mantle waves only, C - a combination
 * of data types.
 * <p>
 * (2) The source duration is generally estimated using an empirically
 * determined relationship such that the duration increases as the cube root of
 * the scalar moment. Specifically, we currently use a relationship where the
 * half duration for an event with moment 10**24 is 1.05 seconds, and for an
 * event with moment 10**27 is 10.5 seconds.
 * <p>
 * (3) For some small earthquakes for which the azimuthal distribution of
 * stations with useful seismograms is poor, we constrain the epicenter of the
 * event to the reference location. This is reflected in the catalog by standard
 * errors of 0.0 for both the centroid latitude and the centroid longitude.
 * <p>
 * (4) For some very shallow earthquakes, the CMT inversion does not well
 * constrain the vertical-dip-slip components of the moment tensor (Mrt and
 * Mrp), and we constrain these components to zero in the inversion. The
 * standard errors for Mrt and Mrp are set to zero in this case.
 * <p>
 * ============================================================================
 *
 * @author Kensuke Konishi
 * @since version 0.0.6.5
 * @see <a href=http://www.ldeo.columbia.edu/~gcmt/projects/CMT/catalog/allorder.ndk_explained>official guide</a>
 */
public final class NDK implements GlobalCMTAccess {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.S");

    /**
     * [1-4] Hypocenter reference catalog (e.g., PDE for USGS location, ISC for ISC
     * catalog, SWE for surface-wave location, [Ekstrom, BSSA, 2006])
     */
    private String hypocenterReferenceCatalog;
    /**
     * reference date and time [6-15] Date of reference event [17-26] Time of reference event
     */
    private LocalDateTime referenceDateTime;
    /**
     * hypocenter position [28-33] Latitude [35-41] Longitude [43-47] Depth
     */
    private FullPosition hypocenterPosition;
    /**
     * [49-55] Reported magnitudes, usually mb and MS
     */
    private double mb;
    /**
     * [49-55] Reported magnitudes, usually mb and MS
     */
    private double ms;
    /**
     * [57-80] Geographical location (24 characters)
     */
    private String geographicalLocation;

    /**
     * [1-16] CMT event name.
     * This string is a unique CMT-event identifier. Older events have
     * 8-character names, current ones have 14-character names. See note (1)
     * below for the naming conventions used. (The first letter is ignored.)
     */
    private GlobalCMTID id;
    /**
     * [18-61] Data used in the CMT inversion. Three data types may be used:
     * Long-period body waves (B), Intermediate-period surface waves (S), and
     * long-period mantle waves (M). For each data type, three values are given:
     * the number of stations used, the number of components used, and the
     * shortest period used.
     */
    private int[] b;
    private int[] s;
    private int[] m;
    /**
     * [63-68] Type of source inverted for: "CMT: 0" - general moment tensor;
     * "CMT: 1" - moment tensor with constraint of zero trace (standard);
     * "CMT: 2" - double-couple source.
     */
    private int cmtType;
    /**
     * [70-80] Type and duration of moment-rate function assumed in the
     * inversion. "TRIHD" indicates a triangular moment-rate function, "BOXHD"
     * indicates a boxcar moment-rate function. The value given is half the
     * duration of the moment-rate function. This value is assumed in the
     * inversion, following a standard scaling relationship (see note (2)
     * below), and is not derived from the analysis.
     */
    private SourceTimeFunctionType momentRateFunctionType;
    /**
     * half duration of the moment rate function
     */
    private double halfDuration;

    /**
     * Third line: CMT info (2) <br>
     * [1-58] Centroid parameters determined in the inversion. Centroid time,
     * given with respect to the reference time, centroid latitude, centroid
     * longitude, and centroid depth. The value of each variable is followed by
     * its estimated standard error. See note (3) below for cases in which the
     * hypocentral coordinates are held fixed. Centroidとreference Timeとの違い
     */
    private double timeDifference;
    private FullPosition centroidPosition;
    /**
     * [60-63] Type of depth. "FREE" indicates that the depth was a result of
     * the inversion; "FIX " that the depth was fixed and not inverted for;
     * "BDY " that the depth was fixed based on modeling of broad-band P
     * waveforms.
     */
    private String depthType;
    /**
     * [65-80] Timestamp. This 16-character string identifies the type of
     * analysis that led to the given CMT results and, for recent events, the
     * date and time of the analysis. This is useful to distinguish Quick CMTs
     * ("Q-"), calculated within hours of an event, from Standard CMTs ("S-"),
     * which are calculated later. The format for this string should not be
     * considered fixed.
     */
    private String timeStamp;

    /**
     * Fourth line: CMT info (3)
     * [1-2] The exponent for all following moment values. For example, if the
     * exponent is given as 24, the moment values that follow, expressed in
     * dyne-cm, should be multiplied by 10**24.
     * [3-80] The six moment-tensor
     * elements: Mrr, Mtt, Mpp, Mrt, Mrp, Mtp, where r is up, t is south, and p
     * is east. See Aki and Richards for conversions to other coordinate
     * systems. The value of each moment-tensor element is followed by its
     * estimated standard error. See note (4) below for cases in which some
     * elements are constrained in the inversion.
     *
     * Fifth line: CMT info (4)
     * [50-56] Scalar moment, to be multiplied by 10**(exponent) as given on
     * line four. dyne*cm
     */
    private MomentTensor momentTensor;

    /**
     * [1-3] Version code. This three-character string is used to track the
     * version of the program that generates the "ndk" file.
     */
    private String versionCode;
    /**
     * [4-48] Moment tensor expressed in its principal-axis system: eigenvalue,
     * plunge, and azimuth of the three eigenvectors. The eigenvalue should be
     * multiplied by 10**(exponent) as given on line four.
     */
    private double eigenValue0;
    private double plunge0;
    private double azimuth0;
    private double eigenValue1;
    private double plunge1;
    private double azimuth1;
    private double eigenValue2;
    private double plunge2;
    private double azimuth2;
    /**
     * [58-80] Strike, dip, and rake for first nodal plane of the
     * best-double-couple mechanism, repeated for the second nodal plane. The
     * angles are defined as in Aki and Richards.
     */
    private int strike0;
    private int dip0;
    private int rake0;
    private int strike1;
    private int dip1;
    private int rake1;

    NDK(String hypocenterReferenceCatalog, LocalDateTime referenceDateTime, FullPosition hypocenterPosition, double mb,
            double ms, String geographicalLocation, GlobalCMTID id, int[] b, int[] s, int[] m, int cmtType,
            SourceTimeFunctionType momentRateFunctionType, double halfDuration, double timeDifference,
            FullPosition centroidPosition, String depthType, String timeStamp,
            MomentTensor momentTensor, String versionCode, double eigenValue0, double plunge0, double azimuth0,
            double eigenValue1, double plunge1, double azimuth1, double eigenValue2, double plunge2, double azimuth2,
            int strike0, int dip0, int rake0, int strike1, int dip1, int rake1) {
        this.hypocenterReferenceCatalog = hypocenterReferenceCatalog;
        this.referenceDateTime = referenceDateTime;
        this.hypocenterPosition = hypocenterPosition;
        this.mb = mb;
        this.ms = ms;
        this.geographicalLocation = geographicalLocation;
        this.id = id;
        this.b = b;
        this.s = s;
        this.m = m;
        this.cmtType = cmtType;
        this.momentRateFunctionType = momentRateFunctionType;
        this.halfDuration = halfDuration;
        this.timeDifference = timeDifference;
        this.centroidPosition = centroidPosition;
        this.depthType = depthType;
        this.timeStamp = timeStamp;
        this.momentTensor = momentTensor;
        this.versionCode = versionCode;
        this.eigenValue0 = eigenValue0;
        this.plunge0 = plunge0;
        this.azimuth0 = azimuth0;
        this.eigenValue1 = eigenValue1;
        this.plunge1 = plunge1;
        this.azimuth1 = azimuth1;
        this.eigenValue2 = eigenValue2;
        this.plunge2 = plunge2;
        this.azimuth2 = azimuth2;
        this.strike0 = strike0;
        this.dip0 = dip0;
        this.rake0 = rake0;
        this.strike1 = strike1;
        this.dip1 = dip1;
        this.rake1 = rake1;
    }

    private NDK() {
    }

    /**
     * Creates an NDK from 5 lines of the catalog.
     *
     * @param lines Lines expressing one NDK
     */
    static NDK constructFromLines(String... lines) {
        if (lines.length != 5) throw new IllegalArgumentException("Invalid input for an NDK");

        NDK ndk = new NDK();
        String[] parts;
        // line 1
        parts = lines[0].split("\\s+");
        ndk.hypocenterReferenceCatalog = parts[0];
        ndk.referenceDateTime = LocalDateTime.parse(parts[1] + " " + parts[2], DATE_FORMAT);
        ndk.hypocenterPosition = FullPosition.constructByDepth(Double.parseDouble(parts[3]), Double.parseDouble(parts[4]),
                Double.parseDouble(parts[5]));
        ndk.mb = Double.parseDouble(parts[6]);
        ndk.ms = Double.parseDouble(parts[7]);
        ndk.geographicalLocation = lines[0].substring(56).trim();

        // line2
        parts = lines[1].split("\\s+");
        ndk.id = new GlobalCMTID(parts[0].substring(1));
        String[] bsmParts =
                lines[1].substring(17, 61).replace("B:", "").replace("S:", "").trim().replace("M:", "").split("\\s+");
        ndk.b = new int[3];
        ndk.b[0] = Integer.parseInt(bsmParts[0]);
        ndk.b[1] = Integer.parseInt(bsmParts[1]);
        ndk.b[2] = Integer.parseInt(bsmParts[2]);
        ndk.s = new int[3];
        ndk.s[0] = Integer.parseInt(bsmParts[3]);
        ndk.s[1] = Integer.parseInt(bsmParts[4]);
        ndk.s[2] = Integer.parseInt(bsmParts[5]);
        ndk.m = new int[3];
        ndk.m[0] = Integer.parseInt(bsmParts[6]);
        ndk.m[1] = Integer.parseInt(bsmParts[7]);
        ndk.m[2] = Integer.parseInt(bsmParts[8]);
        String[] cmtParts = lines[1].substring(61).trim().split("\\s+");
        ndk.cmtType = Integer.parseInt(cmtParts[1]);
        ndk.momentRateFunctionType = SourceTimeFunctionType.ofCode(cmtParts[2].substring(0, 5));
        ndk.halfDuration = Double.parseDouble(cmtParts[3]);

        // line3
        parts = lines[2].split("\\s+");
        ndk.timeDifference = Double.parseDouble(parts[1]);
        ndk.centroidPosition = FullPosition.constructByDepth(Double.parseDouble(parts[3]), Double.parseDouble(parts[5]),
                Double.parseDouble(parts[7]));
        ndk.depthType = parts[9];
        ndk.timeStamp = parts[10];

        // line 4
        parts = lines[3].split("\\s+");
        int momentExponent = Integer.parseInt(parts[0]);
        double mrrCoeff = Double.parseDouble(parts[1]);
        double mttCoeff = Double.parseDouble(parts[3]);
        double mppCoeff = Double.parseDouble(parts[5]);
        double mrtCoeff = Double.parseDouble(parts[7]);
        double mrpCoeff = Double.parseDouble(parts[9]);
        double mtpCoeff = Double.parseDouble(parts[11]);

        // line5
        parts = lines[4].split("\\s+");
        ndk.versionCode = parts[0];
        double m0Coeff = Double.parseDouble(parts[10]);
        ndk.momentTensor = new MomentTensor(m0Coeff, mrrCoeff, mttCoeff, mppCoeff, mrtCoeff, mrpCoeff, mtpCoeff, momentExponent);
        ndk.eigenValue0 = Double.parseDouble(parts[1]);
        ndk.eigenValue1 = Double.parseDouble(parts[4]);
        ndk.eigenValue2 = Double.parseDouble(parts[7]);
        ndk.plunge0 = Double.parseDouble(parts[2]);
        ndk.plunge1 = Double.parseDouble(parts[5]);
        ndk.plunge2 = Double.parseDouble(parts[8]);
        ndk.azimuth0 = Double.parseDouble(parts[3]);
        ndk.azimuth1 = Double.parseDouble(parts[6]);
        ndk.azimuth2 = Double.parseDouble(parts[9]);
        ndk.strike0 = Integer.parseInt(parts[11]);
        ndk.dip0 = Integer.parseInt(parts[12]);
        ndk.rake0 = Integer.parseInt(parts[13]);
        ndk.strike1 = Integer.parseInt(parts[14]);
        ndk.dip1 = Integer.parseInt(parts[15]);
        ndk.rake1 = Integer.parseInt(parts[16]);
        return ndk;
    }

    /**
     * Produce lines to be output in catalog.
     * @return
     *
     * @author otsuru
     * @since 2023/5/31
     */
    String[] toLines() {
        String[] lines = new String[5];

        lines[0] = StringUtils.rightPad(hypocenterReferenceCatalog, 4) + " " + referenceDateTime.format(DATE_FORMAT) + " "
                + MathAid.padToString(hypocenterPosition.getLatitude(), 3, 2, false) + " "
                + MathAid.padToString(hypocenterPosition.getLongitude(), 4, 2, false) + " "
                + MathAid.padToString(hypocenterPosition.getDepth(), 3, 1, false) + " "
                + MathAid.padToString(mb, 1, 1, false) + " " + MathAid.padToString(ms, 1, 1, false) + " " + geographicalLocation;
        lines[1] = "C" + id.toPaddedString() + "  "
                + " B:" + MathAid.padToString(b[0], 3, false) + " " + MathAid.padToString(b[1], 4, false) + " " + MathAid.padToString(b[2], 3, false)
                + " S:" + MathAid.padToString(s[0], 3, false) + " " + MathAid.padToString(s[1], 4, false) + " " + MathAid.padToString(s[2], 3, false)
                + " M:" + MathAid.padToString(m[0], 3, false) + " " + MathAid.padToString(m[1], 4, false) + " " + MathAid.padToString(m[2], 3, false)
                + " CMT: " + cmtType + " " + momentRateFunctionType.toCode() + ":" + MathAid.padToString(halfDuration, 3, 1, false);
        lines[2] = "CENTROID:" + MathAid.padToString(timeDifference, 7, 1, false) + " 0.0 "
                + MathAid.padToString(centroidPosition.getLatitude(), 3, 2, false) + " 0.00 "
                + MathAid.padToString(centroidPosition.getLongitude(), 4, 2, false) + " 0.00 "
                + MathAid.padToString(centroidPosition.getDepth(), 3, 1, false) + "  0.0 "
                + StringUtils.rightPad(depthType, 4) + " " + timeStamp;
        lines[3] = MathAid.padToString(momentTensor.getMtExponent(), 2, false) + " "
                + MathAid.padToString(momentTensor.getMrrCoefficient(), 2, 3, false) + " 0.000 "
                + MathAid.padToString(momentTensor.getMttCoefficient(), 2, 3, false) + " 0.000 "
                + MathAid.padToString(momentTensor.getMppCoefficient(), 2, 3, false) + " 0.000 "
                + MathAid.padToString(momentTensor.getMrtCoefficient(), 2, 3, false) + " 0.000 "
                + MathAid.padToString(momentTensor.getMrpCoefficient(), 2, 3, false) + " 0.000 "
                + MathAid.padToString(momentTensor.getMtpCoefficient(), 2, 3, false) + " 0.000";
        lines[4] = StringUtils.rightPad(versionCode, 3) + " "
                + MathAid.padToString(eigenValue0, 3, 3, false) + " "
                + MathAid.padToString((int) plunge0, 2, false) + " " + MathAid.padToString((int) azimuth0, 3, false) + " "
                + MathAid.padToString(eigenValue1, 3, 3, false) + " "
                + MathAid.padToString((int) plunge1, 2, false) + " " + MathAid.padToString((int) azimuth1, 3, false) + " "
                + MathAid.padToString(eigenValue2, 3, 3, false) + " "
                + MathAid.padToString((int) plunge2, 2, false) + " " + MathAid.padToString((int) azimuth2, 3, false) + " "
                + MathAid.padToString(momentTensor.getM0Coefficient(), 3, 3, false) + " "
                + MathAid.padToString(strike0, 3, false) + " " + MathAid.padToString(dip0, 2, false) + " " + MathAid.padToString(rake0, 4, false) + " "
                + MathAid.padToString(strike1, 3, false) + " " + MathAid.padToString(dip1, 2, false) + " " + MathAid.padToString(rake1, 4, false);

        return lines;
    }

    /**
     * @param search conditions for NDK
     * @return if this fulfills "search"
     */
    boolean fulfill(GlobalCMTSearch search) {
        // date
        LocalDateTime cmtDate = getCMTTime();
        if (search.getStartDate().isAfter(cmtDate) || search.getEndDate().isBefore(cmtDate)) return false;

        //
        if (!search.getPredicateSet().stream().allMatch(p -> p.test(this))) return false;

        // latitude & longitude
        HorizontalPosition position = new HorizontalPosition(centroidPosition.getLatitude(), centroidPosition.getLongitude());
        if (!position.isInRange(search.getLatitudeRange(), search.getLongitudeRange())) return false;
        // depth
        if (!search.getDepthRange().check(centroidPosition.getDepth())) return false;
        // body wave magnitude
        if (!search.getMbRange().check(mb)) return false;
        // surface wave magnitude
        if (!search.getMsRange().check(ms)) return false;
        // moment magnitude
        if (!search.getMwRange().check(momentTensor.getMw())) return false;
        // centroid timeshift
        if (!search.getCentroidTimeShiftRange().check(timeDifference)) return false;
        // half duration
        if (!search.getHalfDurationRange().check(halfDuration)) return false;
        // tension axis plunge
        if (!search.getTensionAxisPlungeRange().check(plunge0)) return false;
        // null axis plunge
        if (!search.getNullAxisPlungeRange().check(plunge1)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = 1;
        result = prime * result + (id == null ? 0 : id.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null) return false;
        if (getClass() != obj.getClass()) return false;
        NDK other = (NDK) obj;
        if (id == null) return other.id == null;
        else return id.equals(other.id);
    }

    @Override
    public GlobalCMTID getGlobalCMTID() {
        return id;
    }

    @Override
    public double getMb() {
        return mb;
    }

    @Override
    public double getMs() {
        return ms;
    }

    @Override
    public MomentTensor getCmt() {
        return momentTensor;
    }

    @Override
    public GlobalCMTAccess withCMT(MomentTensor mt) {
        return new NDK(hypocenterReferenceCatalog, referenceDateTime, hypocenterPosition, mb, ms, geographicalLocation,
                id, b, s, m, cmtType, momentRateFunctionType, halfDuration,
                timeDifference, centroidPosition, depthType, timeStamp,
                mt, versionCode, eigenValue0, plunge0, azimuth0,
                eigenValue1, plunge1, azimuth1, eigenValue2, plunge2, azimuth2,
                strike0, dip0, rake0, strike1, dip1, rake1);
    }

    @Override
    public FullPosition getCmtPosition() {
        return centroidPosition;
    }

    @Override
    public LocalDateTime getCMTTime() {
        int sec = (int) timeDifference;
        double ddiff = timeDifference - sec;
        long nanosec = Math.round(ddiff * 1000 * 1000 * 1000);
        return referenceDateTime.plusSeconds(sec).plusNanos(nanosec);
    }

    @Override
    public FullPosition getPDEPosition() {
        return hypocenterPosition;
    }

    @Override
    public LocalDateTime getPDETime() {
        return referenceDateTime;
    }

    @Override
    public double getTimeDifference() {
        return timeDifference;
    }

    @Override
    public SourceTimeFunctionType getSTFType() {
        return momentRateFunctionType;
    }

    @Override
    public double getHalfDuration() {
        return halfDuration;
    }

    @Override
    public String getHypocenterReferenceCatalog() {
        return hypocenterReferenceCatalog;
    }

    @Override
    public String getGeographicalLocationName() {
        return geographicalLocation;
    }

    @Override
    public String toString() {
        return id.toString();
    }

}
