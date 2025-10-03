package io.github.kensuke1984.kibrary.util.globalcmt;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.source.MomentTensor;
import io.github.kensuke1984.kibrary.source.SourceTimeFunctionType;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

/**
 * Operation to register a virtual event in a custom global CMT catalog.
 *
 * @author otsuru
 * @since 2023/5/30
 */
public class VirtualEventRegistration extends Operation {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.S");

    private final Property property;

    /**
     * CMT event name. Older events: 6 numbers + 1 alphabet. Current events: 12 numbers + 1 alphabet.
     */
    private GlobalCMTID globalCMTID;
    /**
     * Date and time of event.
     */
    private LocalDateTime centroidDateTime;
    private double centroidLatitude;
    private double centroidLongitude;
    private double centroidDepth;
    /**
     * Type of moment-rate function. 1: boxcar moment-rate function, 2: triangular moment-rate function.
     */
    private SourceTimeFunctionType momentRateFunctionType;
    /**
     * Half duration of the moment rate function.
     */
    private double halfDuration;

    /**
     * [1-2] The exponent for all following moment values. For example, if the
     * exponent is given as 24, the moment values that follow, expressed in
     * dyne-cm, should be multiplied by 10**24.
     */
    private int momentExponent;
    /**
     * [3-80] The six moment-tensor
     * elements: Mrr, Mtt, Mpp, Mrt, Mrp, Mtp, where r is up, t is south, and p
     * is east. See Aki and Richards for conversions to other coordinate
     * systems. The value of each moment-tensor element is followed by its
     * estimated standard error. See note (4) below for cases in which some
     * elements are constrained in the inversion.
     */
    private double mrrCoeff;
    private double mttCoeff;
    private double mppCoeff;
    private double mrtCoeff;
    private double mrpCoeff;
    private double mtpCoeff;
    /**
     * [50-56] Scalar moment, to be multiplied by 10**(exponent) as given on
     * line four. dyne*cm
     */
    private double m0Coeff;

    private FullPosition centroidPosition;
    private MomentTensor momentTensor;

    /**
     * @param args  none to create a property file <br>
     *              [property file] to run
     * @throws IOException if any
     */
    public static void main(String[] args) throws IOException {
        if (args.length == 0) writeDefaultPropertiesFile();
        else Operation.mainFromSubclass(args);
    }

    public static void writeDefaultPropertiesFile() throws IOException {
        Class<?> thisClass = new Object(){}.getClass().getEnclosingClass();
        Path outPath = Property.generatePath(thisClass);
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
            pw.println("manhattan " + thisClass.getSimpleName());
            pw.println("##GlobalCMT ID to create. (000000A)");
            pw.println("#globalCMTID ");
            pw.println("##Centroid date and time, in the format 'YYYY/MM/DD hh:mm:ss.s'. (1900/01/01 00:00:00.0)");
            pw.println("#centroidDateTime ");
            pw.println("##(double) Centroid latitude [deg]; [-90:90]. (0)");
            pw.println("#centroidLatitude ");
            pw.println("##(double) Centroid longitude [deg]; [-180:360). (0)");
            pw.println("#centroidLongitude ");
            pw.println("##(double) Centroid depth [km]; [0:). (0)");
            pw.println("#centroidDepth ");
            pw.println("##Type of source time function, from {1:boxcar, 2:triangle}. (2)");
            pw.println("#momentRateFunctionType ");
            pw.println("##(double) Half duration of the moment rate function [s]. (0)");
            pw.println("#halfDuration ");
            pw.println("##(int) Exponential number for the moment values. (25)");
            pw.println("#momentExponent ");
            pw.println("##(double) Coefficient for Mrr, to be multiplied by momentExponent [dyne*cm]. (0)");
            pw.println("#mrrCoeff ");
            pw.println("##(double) Coefficient for Mtt, to be multiplied by momentExponent [dyne*cm]. (0)");
            pw.println("#mttCoeff ");
            pw.println("##(double) Coefficient for Mpp, to be multiplied by momentExponent [dyne*cm]. (0)");
            pw.println("#mppCoeff ");
            pw.println("##(double) Coefficient for Mrt, to be multiplied by momentExponent [dyne*cm]. (0)");
            pw.println("#mrtCoeff ");
            pw.println("##(double) Coefficient for Mrp, to be multiplied by momentExponent [dyne*cm]. (0)");
            pw.println("#mrpCoeff ");
            pw.println("##(double) Coefficient for Mtp, to be multiplied by momentExponent [dyne*cm]. (0)");
            pw.println("#mtpCoeff ");
            pw.println("##(double) Coefficient for M0 (scalar moment), to be multiplied by momentExponent [dyne*cm]. (0)");
            pw.println("#m0Coeff ");
        }
        System.err.println(outPath + " is created.");
    }

    public VirtualEventRegistration(Property property) throws IOException {
        this.property = (Property) property.clone();
    }

    @Override
    public void set() throws IOException {

        globalCMTID = new GlobalCMTID(property.parseString("globalCMTID", "000000A"));
        // check that the ID is not used yet
        if (GlobalCMTCatalog.contains(globalCMTID)) {
            throw new IllegalArgumentException(globalCMTID + " already exists in catalog.");
        }

        centroidDateTime = LocalDateTime.parse(property.parseString("centroidDateTime", "1900/01/01 00:00:00.0"), DATE_FORMAT);
        centroidLatitude = property.parseDouble("centroidLatitude", "0");
        centroidLongitude = property.parseDouble("centroidLongitude", "0");
        centroidDepth = property.parseDouble("centroidDepth", "0");

        momentRateFunctionType = SourceTimeFunctionType.valueOf(property.parseInt("momentRateFunctionType", "2"));
        halfDuration = property.parseDouble("halfDuration", "0");

        momentExponent = property.parseInt("momentExponent", "25");
        mrrCoeff = property.parseDouble("mrrCoeff", "0");
        mttCoeff = property.parseDouble("mttCoeff", "0");
        mppCoeff = property.parseDouble("mppCoeff", "0");
        mrtCoeff = property.parseDouble("mrtCoeff", "0");
        mrpCoeff = property.parseDouble("mrpCoeff", "0");
        mtpCoeff = property.parseDouble("mtpCoeff", "0");
        m0Coeff = property.parseDouble("m0Coeff", "0");
    }

    @Override
    public void run() throws IOException {
        centroidPosition = FullPosition.constructByDepth(centroidLatitude, centroidLongitude, centroidDepth);
        momentTensor = new MomentTensor(m0Coeff, mrrCoeff, mttCoeff, mppCoeff, mrtCoeff, mrpCoeff, mtpCoeff, momentExponent);

        NDK ndk = createNDK();
        GlobalCMTCatalog.addInCustom(ndk);
        System.err.println("Added " + ndk.toString() + " in catalog.");
    }

    private NDK createNDK() {
        String hypocenterReferenceCatalog = "USER";
        double mb = 0;
        double ms = 0;
        String geographicalLocation = "AAAAAAAAAAAAAAAAAAAAAAAA";
        int[] b = {0, 0, 0};
        int[] s = {0, 0, 0};
        int[] m = {0, 0, 0};
        int cmtType = 1;
        double timeDifference = 0;
        String depthType = "FIX";
        String timeStamp = "O-00000000000000";
        String versionCode = "V10";
        double eigenValue0 = 0;
        double plunge0 = 0;
        double azimuth0 = 0;
        double eigenValue1 = 0;
        double plunge1 = 0;
        double azimuth1 = 0;
        double eigenValue2 = 0;
        double plunge2 = 0;
        double azimuth2 = 0;
        int strike0 = 0;
        int dip0 = 0;
        int rake0 = 0;
        int strike1 = 0;
        int dip1 = 0;
        int rake1 = 0;
        return new NDK(hypocenterReferenceCatalog, centroidDateTime, centroidPosition, mb, ms, geographicalLocation,
                globalCMTID, b, s, m, cmtType, momentRateFunctionType, halfDuration,
                timeDifference, centroidPosition, depthType, timeStamp,
                momentTensor, versionCode, eigenValue0, plunge0, azimuth0,
                eigenValue1, plunge1, azimuth1, eigenValue2, plunge2, azimuth2,
                strike0, dip0, rake0, strike1, dip1, rake1);
    }
}
