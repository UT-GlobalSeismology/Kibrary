package io.github.kensuke1984.kibrary.inversion.setup;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.kibrary.math.ParallelizedMatrix;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.PartialID;

/**
 * Class for building A matrix in Am=d.
 * It will be weighed as WA = [weight diagonal matrix][partial derivatives].
 * The volumes of voxels will be multiplied to the partial waveforms here.
 * <p>
 * The number of rows will be decided by the input {@DVectorBuilder}.
 * The number of columns will be decided by the input List of {@UnknownParameter}s.
 * The input {@PartialID} array can have extra IDs, but all needed IDs must be included.
 * <p>
 * This class is <b>IMMUTABLE</b>.
 *
 * @author otsuru
 * @since 2022/7/6 created based on inversion.ObservationEquation
 */
public final class AMatrixBuilder {

    private final List<PartialID> partialIDs;
    private final DVectorBuilder dVector;
    private final List<UnknownParameter> parameterList;

    public AMatrixBuilder(List<PartialID> partialIDs, List<UnknownParameter> parameterList, DVectorBuilder dVector) {
        this.partialIDs = partialIDs;
        this.dVector = dVector;
        this.parameterList = parameterList;
    }

    /**
     * Builds and returns the A matrix.
     * It will be weighed as WA = [weight diagonal matrix][partial derivatives].
     * The volumes of voxels will be multiplied to the partial waveforms here.
     *
     * @param weighting (Weighting)
     * @param fillEmptyPartial (boolean)
     * @return (Matrix) A
     */
    public ParallelizedMatrix buildWithWeight(RealVector[] weighting, boolean fillEmptyPartial) {

        ParallelizedMatrix a = new ParallelizedMatrix(dVector.getNpts(), parameterList.size());
        a.scalarMultiply(0);

        long t = System.nanoTime();
        AtomicInteger count = new AtomicInteger();
        int nUnknowns = parameterList.size();

        partialIDs.stream().parallel().forEach(id -> {
            if (count.get() == dVector.getNTimeWindow() * nUnknowns)
                return;

            // find which unknown parameter this partialID corresponds to
            int column = findColumnForID(id);
            if (column < 0) {
                return;
            }

            // find which timewindow this partialID corresponds to
            int k = dVector.whichTimewindow(id);
            if (k < 0) {
                //TODO delete
                if (id.getVoxelPosition().equals(new FullPosition(-12.5, -67.5, 3630.0))) {
                    for (int i = 0; i < dVector.getNTimeWindow(); i++) {
                        BasicID synID = dVector.getSynID(i);
                        if (synID.getGlobalCMTID().equals(id.getGlobalCMTID()) && synID.getObserver().equals(id.getObserver())
                                && synID.getSacComponent().equals(id.getSacComponent())) {
                            System.err.println("basic: " + synID.getStartTime() + " " + synID.getNpts() + " / partial: " + id.getStartTime() + " " + id.getNpts());
                        }
                    }
                }
              //TODO delete
                return;
            }
            int row = dVector.getStartPoint(k);

            // read partial data
            double[] partial = id.getData();

            // check partial data
            double max = new ArrayRealVector(partial).getLInfNorm();
            if (Double.isNaN(max)) {
                System.err.println(" Caution partial is NaN: " + id);
            }
            if (partial.length != dVector.nptsOfWindow(k)) {
                System.err.println(id + " " + partial.length + " " + dVector.nptsOfWindow(k));
                throw new RuntimeException("Partial length does not match window length");
            }

            // set weighting
            // This includes the volumes of voxels.
            RealVector weightingVector = weighting[k];
            weightingVector = weightingVector.mapMultiply(parameterList.get(column).getSize());

            // set A
            for (int j = 0; j < dVector.nptsOfWindow(k); j++) {
                a.setEntry(row + j, column, partial[j] * weightingVector.getEntry(j));
            }

            count.incrementAndGet();
        });

        if (count.get() != dVector.getNTimeWindow() * nUnknowns) {
            if (fillEmptyPartial) {
                System.err.println("Fill 0 to empty partials : The number of empty partial is " + dVector.getNTimeWindow()
                        + " * " + nUnknowns + " - " + count.get() + " = " + (dVector.getNTimeWindow() * nUnknowns - count.get()));
            } else {
                System.err.println("Printing BasicIDs that are not in the partialID set...");
                //TODO
//                Set<id_station> idStationSet
//                    = Stream.of(ids).map(id -> new id_station(id.getGlobalCMTID(), id.getObserver()))
//                        .distinct().collect(Collectors.toSet());
//                Stream.of(DVECTOR.getObsIDs()).forEach(id -> {
//                    id_station idStation = new id_station(id.getGlobalCMTID(), id.getObserver());
//                    if (!idStationSet.contains(idStation)) {
//                        System.out.println(id);
//                    }
//                });
                throw new RuntimeException("Input partials are not enough: " + " " + count.get() + " != " +
                        dVector.getNTimeWindow() + " * (" + nUnknowns + ")");
            }
        }
        System.err.println(" A is read and built in " + GadgetAid.toTimeString(System.nanoTime() - t));

        return a;
    }

    /**
     * Find the column that the parameter for a {@link PartialID} is in.
     * @param id ({@link PartialID}) The {@link PartialID} to find column for.
     * @return (int) Column number. When none is found, -1.
     */
    private int findColumnForID(PartialID id) {
        for (int i = 0; i < parameterList.size(); i++) {
            if (id.isForParameter(parameterList.get(i))) return i;
        }
        return -1;
    }

}
