package io.github.kensuke1984.kibrary.util.sac;

import java.io.IOException;

import io.github.kensuke1984.kibrary.filter.BandPassFilter;
import io.github.kensuke1984.kibrary.filter.BandStopFilter;
import io.github.kensuke1984.kibrary.filter.ButterworthFilter;
import io.github.kensuke1984.kibrary.filter.HighPassFilter;
import io.github.kensuke1984.kibrary.filter.LowPassFilter;

/**
 * SAC file (SAC: Seismic analysis code). Binary format.
 * <p>
 * This class is <b>immutable</b>
 *
 * @author Kensuke Konishi
 * @since version 1.0.0.1
 * @see <a href=http://ds.iris.edu/ds/nodes/dmc/forms/sac/>SAC</a>
 */
class SACFile extends SACHeader implements SACFileAccess {

    /**
     * waveform data in the sacfile
     */
    private double[] waveData;

    /**
     * @param sacFileName the {@link SACFileName} of the sacfile
     * @throws IOException If an I/O error occurs. in case like a sac file named the
     *                     sacFileName is broken.
     */
    SACFile(SACFileName sacFileName) throws IOException {
        super(sacFileName);
        read(sacFileName);
    }

    /**
     * filterをかける Apply {@link ButterworthFilter}
     *
     * @param filter to apply on this
     */
    @Override
    public SACFileAccess applyButterworthFilter(ButterworthFilter filter) {
        SACFileAccess sd = clone();
        if (filter instanceof BandPassFilter) {
            BandPassFilter bp = (BandPassFilter) filter;
            double periodMax = 2.0 * Math.PI * sd.getValue(SACHeaderEnum.DELTA) / bp.getOmegaL();
            double periodMin = 2.0 * Math.PI * sd.getValue(SACHeaderEnum.DELTA) / bp.getOmegaH();
            sd = sd.withValue(SACHeaderEnum.USER0, periodMin).withValue(SACHeaderEnum.USER1, periodMax);
        } else if (filter instanceof LowPassFilter) {
            LowPassFilter lp = (LowPassFilter) filter;
            double periodMin = 2.0 * Math.PI * getValue(SACHeaderEnum.DELTA) / lp.getOmegaP();
            sd = sd.withValue(SACHeaderEnum.USER0, periodMin);
        } else if (filter instanceof HighPassFilter) {
            HighPassFilter hp = (HighPassFilter) filter;
            double periodMax = 2.0 * Math.PI * getValue(SACHeaderEnum.DELTA) / hp.getOmegaP();
            sd = sd.withValue(SACHeaderEnum.USER1, periodMax);
        } else if (filter instanceof BandStopFilter) {
            BandStopFilter bsf = (BandStopFilter) filter;
            double periodMin = 2 * Math.PI * getValue(SACHeaderEnum.DELTA) / bsf.getOmegaL();
            double periodMax = 2 * Math.PI * getValue(SACHeaderEnum.DELTA) / bsf.getOmegaH();
            sd = sd.withValue(SACHeaderEnum.USER0, periodMax).withValue(SACHeaderEnum.USER1, periodMin);
        }
        double[] sacdata = filter.applyFilter(waveData.clone());
        sd = sd.setSACData(sacdata);
        return sd;
    }

    /**
     * Sacの波形部分を読み込む read sacdata from this.sacFile
     */
    private void read(SACFileName sacFileName) throws IOException {
        try (SACInputStream stream = new SACInputStream(sacFileName.toPath())) {
            stream.skipBytes(632);
            int npts = getInt(SACHeaderEnum.NPTS);
            waveData = new double[npts];
            // float(4) * 70, int(4) * 40, String (8) * 22 + (16)
            // 4* 70 + 4* 40 + 8* 22 +16 = 632
            for (int i = 0; i < npts; i++)
                waveData[i] = stream.readFloat();
        }
    }

    @Override
    public SACFile setSACData(double[] sacData) {
        // setInt(SacHeaderEnum.NPTS, npts);
        int npts = getInt(SACHeaderEnum.NPTS);
        if (npts != sacData.length)
            throw new IllegalStateException("input npts is invalid. SAC npts" + npts + " input npts:" + npts);
        SACFile sf = clone();
        sf.waveData = sacData.clone();
        return sf;
    }

    @Override
    public SACFile clone() {
        try {
            SACFile sf = (SACFile) super.clone();
            sf.waveData = waveData.clone();
            return sf;
        } catch (Exception e) {
            throw new RuntimeException("UNExPECTed");
        }
    }

    @Override
    public SACFile withBoolean(SACHeaderEnum sacHeaderEnum, boolean bool) {
        return (SACFile) super.withBoolean(sacHeaderEnum, bool);
    }

    @Override
    public SACFile withInt(SACHeaderEnum sacHeaderEnum, int value) {
        return (SACFile) super.withInt(sacHeaderEnum, value);
    }

    @Override
    public SACFile withSACEnumerated(SACHeaderEnum sacHeaderEnum, int value) {
        return (SACFile) super.withSACEnumerated(sacHeaderEnum, value);
    }

    @Override
    public SACFile withValue(SACHeaderEnum sacHeaderEnum, double value) {
        return (SACFile) super.withValue(sacHeaderEnum, value);
    }

    @Override
    public SACFile withSACString(SACHeaderEnum sacHeaderEnum, String string) {
        return (SACFile) super.withSACString(sacHeaderEnum, string);
    }

    @Override
    public double[] getData() {
        return waveData.clone();
    }

}
