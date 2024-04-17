package io.github.kensuke1984.kibrary.util.spc;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.github.kensuke1984.kibrary.filter.ButterworthFilter;
import io.github.kensuke1984.kibrary.math.Trace;
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

    private final SPCFileAccess primarySPC;
    private final SPCFileAccess secondarySPC;
    private final SourceTimeFunction sourceTimeFunction;
    /**
     * Sampling frequency [Hz].
     */
    private final double samplingHz;

    private GlobalCMTID globalCMTID;
    private Observer observer;
    private LocalDateTime beginDateTime;
    private int npts;

    /**
     * Output components. (Default: all)
     */
    private Set<SACComponent> components = EnumSet.allOf(SACComponent.class);
    /**
     * Catalog. true: PDE time, false: CMT time.
     */
    private boolean pde;
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
     * @param primarySPC ({@link SPCFileAccess}) First spectrum file.
     * @param secondarySPC ({@link SPCFileAccess}) Pair spectrum file.
     * @param sourceTimeFunction ({@link SourceTimeFunction}) Source time function to convolve.
     * @param samplingHz (double) Sampling frequency [Hz].
     */
    public SACMaker(SPCFileAccess primarySPC, SPCFileAccess secondarySPC, SourceTimeFunction sourceTimeFunction, double samplingHz) {
        if (secondarySPC != null && !check(primarySPC, secondarySPC)) throw new RuntimeException("Input spc files are not a pair.");
        this.primarySPC = primarySPC;
        this.secondarySPC = secondarySPC;
        this.sourceTimeFunction = sourceTimeFunction;
        this.samplingHz = samplingHz;
        setInformation();
    }

    private void setInformation() {
        try {
            globalCMTID = new GlobalCMTID(primarySPC.getSourceID());
        } catch (Exception e) {
            System.err.println(primarySPC.getSourceID() + " is not in Global CMT catalogue.");
        }
        observer = new Observer(primarySPC.getReceiverID(), primarySPC.getReceiverPosition());
        if (globalCMTID != null && beginDateTime == null)
            beginDateTime = pde ? globalCMTID.getEventData().getPDETime() : globalCMTID.getEventData().getCMTTime();
        npts = findNpts();
    }

    private int findNpts() {
        // npts = tlen * samplingHz must be a power of 2.
        if (!MathAid.isInteger(primarySPC.tlen() * samplingHz)) throw new IllegalArgumentException("tlen * samplingHz must be a power of 2.");
        int npts = (int) MathAid.roundForPrecision(primarySPC.tlen() * samplingHz);
        if (npts != Integer.highestOneBit(npts)) throw new IllegalArgumentException("tlen * samplingHz must be a power of 2.");
        return npts;
    }

    /**
     * Check if 2 spectrum files are pairs.
     * @param spc1 ({@link SPCFileAccess}) First spectrum file.
     * @param spc2 ({@link SPCFileAccess}) Pair spectrum file.
     * @return (boolean) Whether the 2 spectrum files have the same parameters.
     */
    public static boolean check(SPCFileAccess spc1, SPCFileAccess spc2) {
        if (spc1.np() != spc2.np()) {
            System.err.println("!! nps are different: " + spc1.np() + " , " + spc2.np());
            return false;
        }
        if (spc1.tlen() != spc2.tlen()) {
            System.err.println("!! tlens are different: " + spc1.tlen() + " , " + spc2.tlen());
            return false;
        }
        if (spc1.nbody() != spc2.nbody()) {
            System.err.println("!! Numbers of bodies (nbody) are different: " + spc1.nbody() + " , " + spc2.nbody());
            return false;
        }
        if (!Arrays.equals(spc1.getBodyR(), spc2.getBodyR())) {
            System.err.println("!! Depths are different as below:");
            for (int i = 0; i < spc1.nbody(); i++)
                System.err.println("    " + spc1.getBodyR()[i] + " , " + spc2.getBodyR()[i]);
            return false;
        }
        if (!spc1.getSourceID().equals(spc2.getSourceID())) {
            System.err.println("!! Source IDs are different: " + spc1.getSourceID() + " , " + spc2.getSourceID());
            return false;
        }
        if (!spc1.getReceiverID().equals(spc2.getReceiverID())) {
            System.err.println("!! Receiver IDs are different: " + spc1.getReceiverID() + " , " + spc2.getReceiverID());
            return false;
        }
        if (!spc1.getSourcePosition().equals(spc2.getSourcePosition())) {
            System.err.println("!! Sources positions are different: " + spc1.getSourcePosition() + " , " + spc2.getSourcePosition());
            return false;
        }
        if (!spc1.getReceiverPosition().equals(spc2.getReceiverPosition())) {
            System.err.println("!! Receiver positions are different: " + spc1.getReceiverPosition() + " , " + spc2.getReceiverPosition());
            return false;
        }
        return true;
    }

    @Override
    public void run() {
        SAC sac = new SAC();
        setHeaderOn(sac);
        SPCBody body = primarySPC.getSpcBodyList().get(0).copy();
        if (secondarySPC != null)
            body.addBody(secondarySPC.getSpcBodyList().get(0));

        compute(body);

        for (SACComponent component : components) {
            SACExtension ext;
            if (asObserved) {
                ext = SACExtension.valueOfObserved(component);
            } else if (sourceTimeFunction != null) {
                ext = SACExtension.valueOfConvolutedSynthetic(component);
            } else {
                ext = SACExtension.valueOfSynthetic(component);
            }
            try {
                sac.of(component).setSACData(body.getTimeseries(component))
                        .writeSAC(outPath.resolve(SACFileName.generate(observer, globalCMTID, ext)));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (temporalDifferentiation) {
            SPCBody bodyT = body.copy();
            bodyT.differentiate(primarySPC.tlen());
            compute(bodyT);
            for (SACComponent component : components) {
                SACExtension extT = sourceTimeFunction != null
                        ? SACExtension.valueOfConvolutedTemporalPartial(component)
                        : SACExtension.valueOfTemporalPartial(component);
                try {
                    sac.of(component).setSACData(bodyT.getTimeseries(component))
                            .writeSAC(outPath.resolve(SACFileName.generate(observer, globalCMTID, extT)));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Set headers of a SAC file.
     * @param sac ({@link SAC}) SAC to set headers.
     */
    private void setHeaderOn(SAC sac) {
        if (beginDateTime != null) sac.withEventTime(beginDateTime);
        sac.withValue(SACHeaderEnum.B, 0);

        sac.withObserver(observer);
        FullPosition eventPosition = primarySPC.getSourcePosition();
        sac.withEventLocation(eventPosition);
        sac.withSACString(SACHeaderEnum.KEVNM, primarySPC.getSourceID());

        sac.withValue(SACHeaderEnum.GCARC, eventPosition.computeEpicentralDistanceDeg(observer.getPosition()));
        sac.withValue(SACHeaderEnum.AZ, eventPosition.computeAzimuthDeg(observer.getPosition()));
        sac.withValue(SACHeaderEnum.BAZ, eventPosition.computeBackAzimuthDeg(observer.getPosition()));

        sac.withInt(SACHeaderEnum.NPTS, npts);
        sac.withValue(SACHeaderEnum.E, MathAid.roundForPrecision(npts / samplingHz));
        sac.withValue(SACHeaderEnum.DELTA, MathAid.roundForPrecision(1.0 / samplingHz));
    }

    /**
     * Compute {@link SPCBody} for write.
     * @param body ({@link SPCBody}) SPC body to compute.
     */
    private void compute(SPCBody body) {
        if (sourceTimeFunction != null)
            body.applySourceTimeFunction(sourceTimeFunction);
        body.toTimeDomain(npts);
        body.applyGrowingExponential(primarySPC.omegai(), samplingHz);
        body.amplitudeCorrection(samplingHz);
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

}
