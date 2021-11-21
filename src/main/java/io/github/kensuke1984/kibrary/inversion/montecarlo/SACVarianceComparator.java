package io.github.kensuke1984.kibrary.inversion.montecarlo;

import io.github.kensuke1984.kibrary.util.FolderUtils;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Created by kensuke on 16/12/21.
 * <b>Assume that there are no stations with the same name but
 * different networks</b>
 *
 * @author Kensuke Konishi
 * @version 0.0.1
 */
class SACVarianceComparator implements DataComparator<SACFileAccess[]> {

    private final double OBS2;
    private final SACFileAccess[] OBSERVED_DATASET;
    private final double SIGMA = 0.5;


    SACVarianceComparator(Path obsDir) throws IOException {
        OBSERVED_DATASET = readObserved(obsDir);
        OBS2 = Arrays.stream(OBSERVED_DATASET).map(SACFileAccess::getData).flatMapToDouble(Arrays::stream)
                .reduce(0, (i, j) -> i + j * j);
    }

    private SACFileAccess[] readObserved(Path obsDir) throws IOException {
        SACFileName[] names = FolderUtils.sacFileNameSet(obsDir).stream().filter(SACFileName::isOBS)
                .sorted(Comparator.comparing(File::getName)).toArray(SACFileName[]::new);
        SACFileAccess[] dataset = new SACFileAccess[names.length];
        for (int i = 0; i < names.length; i++)
            dataset[i] = names[i].read();
        return dataset;
    }

    private boolean same(SACFileAccess data1, SACFileAccess data2) {
        return data1.getGlobalCMTID().equals(data2.getGlobalCMTID()) && data1.getObserver().equals(data2.getObserver()) &&
                data1.getComponent() == data2.getComponent();
    }

    private double computeVariance(SACFileAccess[] synSAC) {
        double numerator = 0;
        for (int j = 0; j < synSAC.length; j++) {

            double[] obs = OBSERVED_DATASET[j].getData();
            double[] syn = synSAC[j].getData();
            for (int i = 0; i < obs.length; i++)
                numerator += (obs[i] - syn[i]) * (obs[i] - syn[i]);
        }
        return numerator / OBS2;
    }

    /**
     * @param dataset to compute likelihood with
     * @return if there are problems for computing likelihood of the dataset
     */
    private boolean hasProblems(SACFileAccess[] dataset) {
        if (dataset.length != OBSERVED_DATASET.length) return true;
        for (int i = 0; i < dataset.length; i++)
            if (!same(OBSERVED_DATASET[i], dataset[i])) return true;
        return false;
    }

    @Override
    public double likelihood(SACFileAccess[] data) {
        if (!hasProblems(data)) throw new RuntimeException("Invalid dataset");
        return Math.exp(-2 * computeVariance(data) / SIGMA);
    }

}
