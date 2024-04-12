package io.github.kensuke1984.kibrary.util.spc;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import io.github.kensuke1984.kibrary.filter.ButterworthFilter;
import io.github.kensuke1984.kibrary.math.Trace;
import io.github.kensuke1984.kibrary.source.SCARDEC;
import io.github.kensuke1984.kibrary.source.SourceTimeFunction;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACExtension;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.SACHeaderEnum;

/**
 * This class creates SAC files from one or two spector files ({@link SPCFile})
 * <p>
 * SPC file name must be "station.eventID(PSV, SH).spc".
 * If the eventID is included in Global CMT catalog, the information for the event is written in SAC.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 * @see <a href=http://ds.iris.edu/ds/nodes/dmc/forms/sac/>SAC</a>
 */
public class SACMaker implements Runnable {

    private static final Map<SACHeaderEnum, String> INITIAL_MAP = new EnumMap<>(SACHeaderEnum.class);

    static {
        INITIAL_MAP.put(SACHeaderEnum.DEPMIN, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.DEPMAX, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.SCALE, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.ODELTA, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.O, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.A, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.T0, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.T1, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.T2, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.T3, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.T4, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.T5, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.T6, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.T7, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.T8, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.T9, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.F, "-12345.0");

        INITIAL_MAP.put(SACHeaderEnum.RESP0, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.RESP1, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.RESP2, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.RESP3, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.RESP4, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.RESP5, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.RESP6, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.RESP7, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.RESP8, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.RESP9, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.STEL, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.STDP, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.EVEL, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.MAG, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.USER0, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.USER1, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.USER2, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.USER3, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.USER4, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.USER5, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.USER6, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.USER7, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.USER8, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.USER9, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.DIST, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.DEPMEN, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.CMPAZ, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.CMPINC, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.XMINIMUM, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.XMAXIMUM, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.YMINIMUM, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.YMAXIMUM, "-12345.0");
        INITIAL_MAP.put(SACHeaderEnum.NVHDR, "6");
        INITIAL_MAP.put(SACHeaderEnum.NORID, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.NEVID, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.NWFID, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.NXSIZE, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.NYSIZE, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.NZYEAR, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.NZJDAY, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.NZHOUR, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.NZMIN, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.NZSEC, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.NZMSEC, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.IFTYPE, "1");
        INITIAL_MAP.put(SACHeaderEnum.IDEP, "5");
        INITIAL_MAP.put(SACHeaderEnum.IZTYPE, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.IINST, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.ISTREG, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.IEVREG, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.IEVTYP, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.IQUAL, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.ISYNTH, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.IMAGTYP, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.IMAGSRC, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.LEVEN, "true");
        INITIAL_MAP.put(SACHeaderEnum.LPSPOL, "false");
        INITIAL_MAP.put(SACHeaderEnum.LOVROK, "true");
        INITIAL_MAP.put(SACHeaderEnum.LCALDA, "true");
        INITIAL_MAP.put(SACHeaderEnum.KHOLE, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KO, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KA, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KT0, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KT1, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KT2, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KT3, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KT4, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KT5, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KT6, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KT7, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KT8, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KT9, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KF, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KUSER0, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KUSER1, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KUSER2, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KDATRD, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.KINST, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num100, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num101, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num102, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num103, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num104, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num109, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num54, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num55, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num63, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num64, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num65, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num66, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num67, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num68, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num69, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num80, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num84, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num88, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num9, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num97, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num98, "-12345");
        INITIAL_MAP.put(SACHeaderEnum.num99, "-12345");
    }

    private SPCFileAccess primeSPC;
    private SPCFileAccess secondarySPC;
    private GlobalCMTID globalCMTID;
    private Observer observer;
    private SourceTimeFunction sourceTimeFunction;
    /**
     * Output components. (Default: all)
     */
    private Set<SACComponent> components = EnumSet.allOf(SACComponent.class);
    /**
     * Sampling frequency [Hz].
     */
    private double samplingHz = 20;
    /**
     * Catalog. true: PDE time, false: CMT time. TODO scardec??
     */
    private boolean pde;
    private LocalDateTime beginDateTime;
    private int lsmooth;
    private double delta;
    private int npts;
    /**
     * Output path.
     */
    private Path outPath;
    /**
     * Whether to differentiate by time.
     */
    private boolean temporalDifferentiation;
    /**
     * Whether to set SAC extension of output files as observed ones.
     */
    private boolean asObserved;

    /**
     * @param oneSPC Spectrum for SAC
     */
    public SACMaker(SPCFileAccess oneSPC) {
        this(oneSPC, null, null);
    }

    /**
     * @param oneSPC  one spc
     * @param pairSPC pair spc
     */
    SACMaker(SPCFileAccess oneSPC, SPCFileAccess pairSPC) {
        this(oneSPC, pairSPC, null);
    }

    /**
     * @param oneSPC             one spc
     * @param pairSPC            pair spc
     * @param sourceTimeFunction to consider
     */
    public SACMaker(SPCFileAccess oneSPC, SPCFileAccess pairSPC, SourceTimeFunction sourceTimeFunction) {
        if (pairSPC != null && !check(oneSPC, pairSPC)) throw new RuntimeException("Input spc files are not a pair.");
        primeSPC = oneSPC;
        secondarySPC = pairSPC;
        try {
            globalCMTID = new GlobalCMTID(oneSPC.getSourceID());
        } catch (Exception e) {
            System.err.println(oneSPC.getSourceID() + " is not in Global CMT catalogue.");
        }
        this.sourceTimeFunction = sourceTimeFunction;
    }

    /**
     * @param spc1 primary
     * @param spc2 secondary
     * @return if spc1 and spc2 have same information
     */
    public static boolean check(SPCFileAccess spc1, SPCFileAccess spc2) {
        boolean isOK = true;
        if (spc1.nbody() != spc2.nbody()) {
            System.err
                    .println("Numbers of bodies (nbody) are different. fp, bp: " + spc1.nbody() + " ," + spc2.nbody());
            isOK = false;
        }

        if (!spc1.getSourceID().equals(spc2.getSourceID())) {
            System.err.println("Source IDs are different " + spc1.getSourceID() + " " + spc2.getSourceID());
            isOK = false;
        }

        if (!spc1.getReceiverID().equals(spc2.getReceiverID())) {
            System.err.println("Observer IDs are different " + spc1.getReceiverID() + " " + spc2.getReceiverID());
            isOK = false;
        }

        if (isOK) {
            if (!Arrays.equals(spc1.getBodyR(), spc2.getBodyR()))
                isOK = false;

            if (!isOK) {
                System.err.println("the depths are invalid(different) as below  fp : bp");
                for (int i = 0; i < spc1.nbody(); i++)
                    System.err.println(spc1.getBodyR()[i] + " : " + spc2.getBodyR()[i]);
            }
        }
        if (spc1.np() != spc2.np()) {
            System.err.println("nps are different. fp, bp: " + spc1.np() + ", " + spc2.np());
            isOK = false;
        }

        // double tlen
        if (spc1.tlen() != spc2.tlen()) {
            System.err.println("tlens are different. fp, bp: " + spc1.tlen() + " ," + spc2.tlen());
            isOK = false;
        }

        if (!spc1.getSourcePosition().equals(spc2.getSourcePosition())) {
            System.err.println("locations of sources of input spcfiles are different");
            System.err.println(spc1.getSourcePosition() + " " + spc2.getSourcePosition());
            isOK = false;
        }

        if (!spc1.getReceiverPosition().equals(spc2.getReceiverPosition())) {
            System.err.println("locations of stations of input spcfiles are different");
            isOK = false;
        }
        return isOK;
    }

    /**
     * Create sacFiles for partials in outDirectory.
     *
     * @param outDirectoryPath {@link Path} of an write folder
     * @throws IOException if an I/O error occurs
     */
    public void outputPAR(Path outDirectoryPath) throws IOException {
        Files.createDirectories(outDirectoryPath);
        setInformation();
        SAC sac = new SAC();
        setHeaderOn(sac);
        for (int i = 0; i < primeSPC.nbody(); i++) {
            SPCBody body = primeSPC.getSpcBodyList().get(i).copy();
            if (secondarySPC != null)
                body.addBody(secondarySPC.getSpcBodyList().get(i));
            compute(body);
            String bodyR = MathAid.simplestString(primeSPC.getBodyR()[i], "d");
            for (SACComponent component : components) {
                // System.out.println(component);
                SACExtension ext = sourceTimeFunction != null ? SACExtension.valueOfConvolutedSynthetic(component)
                        : SACExtension.valueOfSynthetic(component);
                SACFileName sacFileName = new SACFileName(outDirectoryPath.resolve(observer + "."
                        + globalCMTID + "." + primeSPC.getSpcFileType() + "..." + bodyR + "." + ext));
                if (sacFileName.exists()) {
                    System.err.println(sacFileName + " already exists..");
                    return;
                }
                sac.of(component).setSACData(body.getTimeseries(component)).writeSAC(sacFileName.toPath());
            }
        }

    }

    @Override
    public void run() {
        setInformation();
        SAC sac = new SAC();
        setHeaderOn(sac);
        SPCBody body = primeSPC.getSpcBodyList().get(0).copy();
        if (secondarySPC != null)
            body.addBody(secondarySPC.getSpcBodyList().get(0));

        compute(body);

        for (SACComponent component : components) {
            SACExtension ext;
            if (asObserved)
                ext = SACExtension.valueOfObserved(component);
            else
                ext = sourceTimeFunction != null ? SACExtension.valueOfConvolutedSynthetic(component)
                        : SACExtension.valueOfSynthetic(component);
            try {
                sac.of(component).setSACData(body.getTimeseries(component)).writeSAC(
                        outPath.resolve(SACFileName.generate(observer, globalCMTID, ext)));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (temporalDifferentiation) {
            SPCBody bodyT = body.copy();
            bodyT.differentiate(primeSPC.tlen());
            compute(bodyT);
            for (SACComponent component : components) {
                SACExtension extT = sourceTimeFunction != null
                        ? SACExtension.valueOfConvolutedTemporalPartial(component)
                        : SACExtension.valueOfTemporalPartial(component);
                try {
                    sac.of(component).setSACData(bodyT.getTimeseries(component)).writeSAC(
                            outPath.resolve(SACFileName.generate(observer, globalCMTID, extT)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void setInformation() {
        observer = new Observer(primeSPC.getReceiverID(), primeSPC.getReceiverPosition());
        if (globalCMTID != null && beginDateTime == null)
            beginDateTime = pde ? globalCMTID.getEventData().getPDETime() : globalCMTID.getEventData().getCMTTime();
        npts = findNPTS();
        lsmooth = findLsmooth();
        delta = primeSPC.tlen() / npts;
    }

    private int findNPTS() {
        int npts = (int) (primeSPC.tlen() * samplingHz);
        int pow2 = Integer.highestOneBit(npts);
        return pow2 < npts ? pow2 * 2 : pow2;
    }

    private int findLsmooth() {
        int np = Integer.highestOneBit(primeSPC.np());
        if (np < primeSPC.np()) np *= 2;
        int lsmooth = npts / np / 2;
        int i = Integer.highestOneBit(lsmooth);
        return i < lsmooth ? i * 2 : i;
    }

    /**
     * set headers on the input sacFile
     *
     * @param sac to set header on
     */
    private void setHeaderOn(SAC sac) {
        if (beginDateTime != null) sac.withEventTime(beginDateTime);
        sac.withValue(SACHeaderEnum.B, 0);

        sac.withObserver(observer);
        FullPosition eventPosition = primeSPC.getSourcePosition();
        sac.withEventLocation(eventPosition);
        sac.withSACString(SACHeaderEnum.KEVNM, primeSPC.getSourceID());

        sac.withValue(SACHeaderEnum.GCARC, eventPosition.computeEpicentralDistanceDeg(observer.getPosition()));
        sac.withValue(SACHeaderEnum.AZ, eventPosition.computeAzimuthDeg(observer.getPosition()));
        sac.withValue(SACHeaderEnum.BAZ, eventPosition.computeBackAzimuthDeg(observer.getPosition()));

        sac.withInt(SACHeaderEnum.NPTS, npts);
        sac.withValue(SACHeaderEnum.E, delta * npts);
        sac.withValue(SACHeaderEnum.DELTA, delta);
    }

    /**
     * compute {@link SPCBody} for write.
     *
     * @param body to compute
     */
    private void compute(SPCBody body) {
        if (sourceTimeFunction != null)
            body.applySourceTimeFunction(sourceTimeFunction);
        body.toTimeDomain(lsmooth);
        body.applyGrowingExponential(primeSPC.omegai(), primeSPC.tlen());
        body.amplitudeCorrection(primeSPC.tlen());
    }

    public void setComponents(Set<SACComponent> components) {
        this.components = components;
    }

    /**
     * @param (boolean) If set true, the time in SAC is PDE time.
     */
    public void setPDE(boolean bool) {
        pde = bool;
    }

    public void setSourceTimeFunction(SourceTimeFunction sourceTimeFunction) {
        this.sourceTimeFunction = sourceTimeFunction;
    }

    public void setTemporalDifferentiation(boolean temporalDifferentiation) {
        this.temporalDifferentiation = temporalDifferentiation;
    }

    public void setAsObserved(boolean asObserved) {
        this.asObserved = asObserved;
    }

    /**
     * @param outPath ({@link Path}) The folder where SAC files should be created.
     */
    public void setOutPath(Path outPath) {
        this.outPath = outPath;
    }

    private class SAC implements SACFileAccess, Cloneable {
        private double[] waveData;

        @Override
        protected SAC clone() {
            try {
                SAC sac = (SAC) super.clone();
                sac.headerMap = new EnumMap<>(headerMap);
                return sac;
            } catch (Exception e) {
                throw new RuntimeException("UneXPectED");
            }
        }

        private SAC of(SACComponent component) {
            SAC sac = clone();
            sac = sac.withSACString(SACHeaderEnum.KCMPNM, component.toString());
//            switch (component.valueOf()) {
//            case 1:
//                sac = sac.setSACString(SACHeaderEnum.KCMPNM, "vertical");
//                break;
//            case 2:
//                sac = sac.setSACString(SACHeaderEnum.KCMPNM, "radial");
//                break;
//            case 3:
//                sac = sac.setSACString(SACHeaderEnum.KCMPNM, "trnsvers");
//                break;
//            default:
//            }
            return sac;
        }

        private Map<SACHeaderEnum, String> headerMap = new EnumMap<>(INITIAL_MAP);

        private SAC() {
        }

        @Override
        public boolean getBoolean(SACHeaderEnum sacHeaderEnum) {
            return Boolean.parseBoolean(headerMap.get(sacHeaderEnum));
        }

        @Override
        public int getInt(SACHeaderEnum sacHeaderEnum) {
            return Integer.parseInt(headerMap.get(sacHeaderEnum));
        }

        @Override
        public int getSACEnumerated(SACHeaderEnum sacHeaderEnum) {
            return Integer.parseInt(headerMap.get(sacHeaderEnum));
        }

        @Override
        public String getSACString(SACHeaderEnum sacHeaderEnum) {
            return headerMap.get(sacHeaderEnum);
        }

        @Override
        public double getValue(SACHeaderEnum sacHeaderEnum) {
            return Double.parseDouble(headerMap.get(sacHeaderEnum));
        }

        @Override
        public Trace createTrace() {
            throw new RuntimeException("UnEXPEcteD");
        }

        @Override
        public SAC withBoolean(SACHeaderEnum sacHeaderEnum, boolean bool) {
            if (headerMap.containsKey(sacHeaderEnum))
                throw new RuntimeException("UNEeXpExted");
            headerMap.put(sacHeaderEnum, String.valueOf(bool));
            return this;
        }

        @Override
        public SAC applyButterworthFilter(ButterworthFilter filter) {
            throw new RuntimeException("UnEXPEcteD");
        }

        @Override
        public SAC withValue(SACHeaderEnum sacHeaderEnum, double value) {
            if (headerMap.containsKey(sacHeaderEnum))
                throw new RuntimeException("UNEeXpExted");
            headerMap.put(sacHeaderEnum, String.valueOf(value));
            return this;
        }

        @Override
        public SAC withInt(SACHeaderEnum sacHeaderEnum, int value) {
            if (headerMap.containsKey(sacHeaderEnum) && headerMap.get(sacHeaderEnum) != "-12345")
                throw new RuntimeException("UNEeXpExted");
            headerMap.put(sacHeaderEnum, String.valueOf(value));
            return this;
        }

        @Override
        public SAC withSACEnumerated(SACHeaderEnum sacHeaderEnum, int value) {
            if (headerMap.containsKey(sacHeaderEnum))
                throw new RuntimeException("UNEeXpExted");
            headerMap.put(sacHeaderEnum, String.valueOf(value));
            return this;
        }

        @Override
        public SAC withSACString(SACHeaderEnum sacHeaderEnum, String string) {
            if (headerMap.containsKey(sacHeaderEnum))
                throw new RuntimeException("UNEeXpExted");
            headerMap.put(sacHeaderEnum, string);
            return this;
        }

        @Override
        public SAC setSACData(double[] waveData) {
            Objects.requireNonNull(waveData);
            if (waveData.length != getInt(SACHeaderEnum.NPTS))
                throw new RuntimeException("UNEeXpExted");
            this.waveData = waveData;
            return this;
        }

        @Override
        public double[] getData() {
            return waveData.clone();
        }

    }


    /**
     * Creates and outputs synthetic SAC files of Z R T from input spectra
     *
     * @param args (option) [onespc] [pairspc]
     * @throws IOException    if an I/O error occurs
     * @throws ParseException if any
     */
    public static void main(String[] args) throws IOException, ParseException {
        if (args == null || args.length == 0)
             throw new IllegalArgumentException("\"Usage:(options) spcfile1 (spcfile2)\"");

        for (String o : args)
            if (o.equals("-help") || o.equals("--help")) {
                printHelp();
                return;
            }
        CommandLine cli = new DefaultParser().parse(OPTIONS, args);

        if (cli.getArgs().length < 1 || 2 < cli.getArgs().length)
            throw new IllegalArgumentException("\"Usage:(options) spcfile1 (spcfile2)\"");

        Path outPath = Paths.get(cli.getOptionValue("o", "."));
        if (!Files.exists(outPath)) throw new RuntimeException(outPath + " does not exist.");

        if (cli.hasOption("scardec") && cli.hasOption("gcmt"))
            throw new IllegalArgumentException("Options -gcmt and -scardec cannot be used simultaneously.");

        Set<SACComponent> components = SACComponent.componentSetOf(cli.getOptionValue('c', "ZRT"));

        SCARDEC scardec = null;
        if (cli.hasOption("scardec")) {
            String dateStr = cli.getOptionValue("scardec");
            try {
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").parse(dateStr);
            } catch (Exception e) {
                throw new IllegalArgumentException("SCARDEC id must be yyyyMMdd_HHmmss");
            }
            Predicate<SCARDEC.SCARDEC_ID> predicate =
                    id -> id.getOriginTime().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                            .equals(cli.getOptionValue("scardec"));
            SCARDEC.SCARDEC_ID id = SCARDEC.pick(predicate);
            scardec = id.toSCARDEC();
        }
        GlobalCMTID id = null;
        String stfshape = null;
        if (cli.hasOption("gcmt") && cli.hasOption("gid")) {
            id = new GlobalCMTID(cli.getOptionValue("gid"));
            stfshape = cli.getOptionValue("gcmt");
            if (!(stfshape.equals("triangle") || stfshape.equals("boxcar")))
                throw new IllegalArgumentException("The option -gcmt only accepts 'boxcar' or 'triangle'.");
        } else if (cli.hasOption("gcmt") || cli.hasOption("gid")) {
            throw new RuntimeException("The options 'gcmt' and 'gid' must be specified at the same time.");
        }

        String[] spcfiles = cli.getArgs();
        SPCFileName oneName = new FormattedSPCFileName(args[0]);
        SPCFileAccess oneSPC = SPCFile.getInstance(oneName);

        SPCFileAccess pairSPC = null;
        if (1 < args.length) {
            SPCFileName pairName = new FormattedSPCFileName(args[1]);
            pairSPC = SPCFile.getInstance(pairName);
        }

        SACMaker sm = new SACMaker(oneSPC, pairSPC);
        if (scardec != null) {
            sm.beginDateTime = scardec.getOriginTime();
            sm.setSourceTimeFunction(scardec.getOptimalSTF(oneSPC.np(), oneSPC.tlen()));
        }
        if (id != null) {
            double halfDuration = id.getEventData().getHalfDuration();
            sm.setSourceTimeFunction(stfshape.equals("boxcar") ?
                    SourceTimeFunction.boxcarSourceTimeFunction(oneSPC.np(), oneSPC.tlen(), 20, halfDuration) :
                    SourceTimeFunction.triangleSourceTimeFunction(oneSPC.np(), oneSPC.tlen(), 20, halfDuration));
        }
        sm.setOutPath(outPath);
        sm.components = components;
        sm.run();
    }

    /**
     * Options
     */
    private static final Options OPTIONS = new Options();
    /**
     * Help Formatter
     */
    private static final HelpFormatter helpFormatter = new HelpFormatter();

    static void printHelp() {
        helpFormatter.printHelp("SACMaker", OPTIONS);
    }

}
