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
public class Partial1DSpcMaker {
/*
        private void cutAndWrite(Observer station, double[] filteredUt, TimewindowData t, double bodyR,
                PartialType partialType, double[] periodRange) {

            double[] cutU = cutU(filteredUt, t);
            FourierTransform fourier = new FourierTransform(cutU, finalFreqSamplingHz);
            double[] partialReFy = fourier.getRealFy();
            double[] partialImFy = fourier.getImFy();

            double[] imFy = findImFyID(t).getData();
            double[] reFy = findReFyID(t).getData();
            double[] ampSquared = new double[imFy.length];
            for (int i = 0; i < imFy.length; i++)
                ampSquared[i] = imFy[i] * imFy[i] + reFy[i] * reFy[i];

            double df = fourier.getFreqIncrement(partialSamplingHz);

            if (highFreq > partialSamplingHz)
                throw new RuntimeException("f1 must be <= sacSamplingHz");
            int iStart = (int) (lowFreq / df) - 1;
            int fnpts = (int) ((highFreq - lowFreq) / df);

            double[] cutPartialReFy = IntStream.range(0, fnpts).parallel().mapToDouble(i -> partialReFy[i + iStart]).toArray();
            double[] cutPartialImFy = IntStream.range(0, fnpts).parallel().mapToDouble(i -> partialImFy[i + iStart]).toArray();

            double[] cutPartialSpcAmp = new double[cutPartialReFy.length];

            for (int i = 0; i < cutPartialReFy.length; i++) {
                cutPartialSpcAmp[i] = 1. / ampSquared[i] * (reFy[i] * cutPartialReFy[i] + imFy[i] * cutPartialImFy[i]);
            }

            // water level fix for "jumping" partials
            double max = new ArrayRealVector(ampSquared).getLInfNorm();
            double waterlevel = max / 100.;
            for (int i = 0; i < cutPartialReFy.length; i++) {
                if (ampSquared[i] < waterlevel)
                    cutPartialSpcAmp[i] = 0.;
            }

            PartialID pid = new PartialID(station, id, t.getComponent(), finalSamplingHz, t.getStartTime(), cutPartialReFy.length,
                    periodRange[0], periodRange[1], t.getPhases(), sourceTimeFunction != null, partialType.toParameterType(),
                    partialType.toVariableType(), new FullPosition(0, 0, bodyR), cutPartialSpcAmp);

            try {
                partialDataWriter.addPartialID(pid);
                add();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private BasicID findImFyID(TimewindowData t) {
            try {
                return Arrays.stream(imFyIDs).filter(id -> id.getObserver().equals(t.getObserver())
                        && id.getGlobalCMTID().equals(t.getGlobalCMTID()) && Math.abs(id.getStartTime() - t.getStartTime()) < 1.
                        && t.getComponent().equals(id.getSacComponent()))
                    .findAny().get();
            } catch (NoSuchElementException e) {
                System.err.println(t);
                e.printStackTrace();
                throw new RuntimeException();
            }
        }

        private BasicID findReFyID(TimewindowData t) {
            return Arrays.stream(reFyIDs).filter(id -> id.getObserver().equals(t.getObserver())
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

        private double[] cutU(double[] u, TimewindowData timewindowInformation) {
            int cutstart = (int) (timewindowInformation.getStartTime() * partialSamplingHz);
            // 書きだすための波形
            int outnpts = (int) ((timewindowInformation.getEndTime() - timewindowInformation.getStartTime())
                    * partialSamplingHz);
            double[] sampleU = new double[outnpts];
            // cutting a waveform for outputting
            Arrays.setAll(sampleU, j -> u[cutstart + j]);

            return sampleU;
        }
*/
}
