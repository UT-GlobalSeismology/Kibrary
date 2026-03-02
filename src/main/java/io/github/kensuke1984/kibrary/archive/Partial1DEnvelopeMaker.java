package io.github.kensuke1984.kibrary.archive;

/**
 * Creates a pair of files containing 1-D partial derivatives
 *
 * TODO shとpsvの曖昧さ 両方ある場合ない場合等 現状では combineして対処している
 *
 * Time length (tlen) and the number of step in frequency domain (np) in DSM
 * software must be same. Those values are set in a parameter file.
 *
 * Only partials for radius written in a parameter file are computed.
 *
 * <b>Assume there are no station with the same name but different networks in
 * same events</b> TODO
 *
 * @version 0.2.0.3
 *
 * @author Kensuke Konishi
 *
 */
public class Partial1DEnvelopeMaker {
/*
        private void cutAndWrite(Observer station, double[] filteredUt, TimewindowData t, double bodyR,
                PartialType partialType, double[] periodRange) {

            HilbertTransform hilbert = new HilbertTransform(filteredUt);
            double[] partialHy = hilbert.getHy();

            double[] waveformData = findWaveformID(t).getData();
            double[] envelope = findEnvelopeID(t).getData();
            double[] waveformHy = findHyID(t).getData();

            double[] cutU = sampleOutput(filteredUt, t);
            double[] cutHy = sampleOutput(partialHy, t);

            double[] cutPartialEnvelope = new double[cutU.length];
            for (int i = 0; i < cutU.length; i++)
                cutPartialEnvelope[i] = 1. / envelope[i] * (waveformData[i] * cutU[i] + waveformHy[i] * cutHy[i]);

            PartialID pid = new PartialID(station, id, t.getComponent(), finalSamplingHz, t.getStartTime(), cutU.length,
                    periodRange[0], periodRange[1], t.getPhases(), sourceTimeFunction != null, partialType.toParameterType(),
                    partialType.toVariableType(), new FullPosition(0, 0, bodyR), cutPartialEnvelope);

            try {
                partialDataWriter.addPartialID(pid);
                add();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private BasicID findWaveformID(TimewindowData t) {
            return Arrays.stream(waveformIDs).filter(id -> id.getObserver().equals(t.getObserver())
                    && id.getGlobalCMTID().equals(t.getGlobalCMTID()) && Math.abs(id.getStartTime() - t.getStartTime()) < 1.
                    && t.getComponent().equals(id.getSacComponent()))
                .findAny().get();
        }

        private BasicID findEnvelopeID(TimewindowData t) {
            return Arrays.stream(envelopeIDs).filter(id -> id.getObserver().equals(t.getObserver())
                    && id.getGlobalCMTID().equals(t.getGlobalCMTID()) && Math.abs(id.getStartTime() - t.getStartTime()) < 1.
                    && t.getComponent().equals(id.getSacComponent()))
                .findAny().get();
        }

        private BasicID findHyID(TimewindowData t) {
            return Arrays.stream(hyIDs).filter(id -> id.getObserver().equals(t.getObserver())
                    && id.getGlobalCMTID().equals(t.getGlobalCMTID()) && Math.abs(id.getStartTime() - t.getStartTime()) < 1.
                    && t.getComponent().equals(id.getSacComponent()))
                .findAny().get();
        }
*/
        /**
         * @param u
         *            partial waveform
         * @param timewindowInformation
         *            cut information
         * @return u cut by considering sampling Hz
         */
/*        private double[] sampleOutput(double[] u, TimewindowData timewindowInformation) {
            int cutstart = (int) (timewindowInformation.getStartTime() * partialSamplingHz);
            // 書きだすための波形
            int outnpts = (int) ((timewindowInformation.getEndTime() - timewindowInformation.getStartTime())
                    * finalSamplingHz);
            double[] sampleU = new double[outnpts];
            // cutting a waveform for outputting
            Arrays.setAll(sampleU, j -> u[cutstart + j * step]);

            return sampleU;
        }
*/
}
