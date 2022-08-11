package io.github.kensuke1984.kibrary.inversion.setup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.math.ParallelizedMatrix;
import io.github.kensuke1984.kibrary.util.GadgetAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.voxel.Physical1DParameter;
import io.github.kensuke1984.kibrary.voxel.Physical3DParameter;
import io.github.kensuke1984.kibrary.voxel.TimeReceiverSideParameter;
import io.github.kensuke1984.kibrary.voxel.TimeSourceSideParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
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
final class AMatrixBuilder {

    private final PartialID[] partialIDs;
    private final DVectorBuilder dVector;
    private final List<UnknownParameter> parameterList;

    AMatrixBuilder(PartialID[] partialIDs, List<UnknownParameter> parameterList, DVectorBuilder dVector) {
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
     * @return (Matrix) A
     */
    ParallelizedMatrix buildWithWeight(Weighting weighting) {

        ParallelizedMatrix a = new ParallelizedMatrix(dVector.getNpts(), parameterList.size());
        a.scalarMultiply(0);

        long t = System.nanoTime();
        AtomicInteger count = new AtomicInteger();
        int nUnknowns = (int) parameterList.stream().filter(unknown -> !unknown.getPartialType().isTimePartial()).count();

        Arrays.stream(partialIDs).parallel().forEach(id -> {
            if (count.get() == dVector.getNTimeWindow() * nUnknowns)
                return;

            // find which unknown parameter this partialID corresponds to
            int column = findColumn(id.getPartialType(), id.getVoxelPosition(),
                    id.getObserver(), id.getGlobalCMTID(), id.getPhases());
            if (column < 0) {
                return;
            }

            // find which timewindow this partialID corresponds to
            int k = dVector.whichTimewindow(id);
            if (k < 0) {
                return;
            }
            int row = dVector.getStartPoint(k);

            // read partial data
            double[] partial = id.getData();

            // check partial data
            double max = new ArrayRealVector(partial).getLInfNorm();
            if (Double.isNaN(max)) {
                System.out.println("Caution partial is NaN: " + id);
            }
            if (partial.length != dVector.nptsOfWindow(k)) {
                System.err.println(id + " " + partial.length + " " + dVector.nptsOfWindow(k));
                throw new RuntimeException("Partial length does not match window length");
            }

            // set weighting
            RealVector weightingVector = weighting.get(k);
            weightingVector = weightingVector.mapMultiply(parameterList.get(column).getWeighting());

            // set A
            for (int j = 0; j < dVector.nptsOfWindow(k); j++) {
                a.setEntry(row + j, column, partial[j] * weightingVector.getEntry(j));
            }

            count.incrementAndGet();
        });

        if (count.get() != dVector.getNTimeWindow() * nUnknowns) {
            System.out.println("Printing BasicIDs that are not in the partialID set...");
            //TODO
//            Set<id_station> idStationSet
//                = Stream.of(ids).map(id -> new id_station(id.getGlobalCMTID(), id.getObserver()))
//                    .distinct().collect(Collectors.toSet());
//            Stream.of(DVECTOR.getObsIDs()).forEach(id -> {
//                id_station idStation = new id_station(id.getGlobalCMTID(), id.getObserver());
//                if (!idStationSet.contains(idStation)) {
//                    System.out.println(id);
//                }
//            });
            throw new RuntimeException("Input partials are not enough: " + " " + count.get() + " != " +
                    dVector.getNTimeWindow() + " * (" + nUnknowns + ")");
        }
        System.err.println("A is read and built in " + GadgetAid.toTimeString(System.nanoTime() - t));

        return a;
    }

    /**
     * Find which column a parameter should be in.
     * @param type     to look for
     * @param position to look for
     * @return i, m<sub>i</sub> = type, parameterが何番目にあるか なければ-1
     */
    private int findColumn(PartialType type, FullPosition position, Observer observer, GlobalCMTID event, Phase[] phases) {
        for (int i = 0; i < parameterList.size(); i++) {
            if (parameterList.get(i).getPartialType() != type)
                continue;

            switch (type) {
            case TIME_SOURCE:
                if (event.equals( ((TimeSourceSideParameter) parameterList.get(i)).getGlobalCMTID() ))
                    return i;
                break;
            case TIME_RECEIVER:
                //TODO
                List<Integer> bouncingOrders = new ArrayList<Integer>();
                bouncingOrders.add(1);
                Collections.sort(bouncingOrders);
                int lowestBouncingOrder = bouncingOrders.get(0);
                if (observer.equals( ((TimeReceiverSideParameter) parameterList.get(i)).getStation() ) &&
                        ((TimeReceiverSideParameter) parameterList.get(i)).getBouncingOrder() == lowestBouncingOrder)
                    return i;
                break;
            case PARA:
            case PARC:
            case PARF:
            case PARL:
            case PARN:
            case PARQ:
                if (position.getR() == ((Physical1DParameter) parameterList.get(i)).getPerturbationR())
                    return i;
                break;
            case PAR1:
            case PAR2:
            case PARVS:
            case PARVP:
            case PARG:
            case PARM:
            case PAR00:
                if (position.getR() == ((Physical1DParameter) parameterList.get(i)).getPerturbationR())
                    return i;
                break;
            case A:
            case C:
            case F:
            case L:
            case N:
            case Q:
            case MU:
            case LAMBDA:
            case KAPPA:
            case LAMBDA2MU:
            case Vs:
                if (position.equals(((Physical3DParameter) parameterList.get(i)).getPointLocation()))
                    return i;
                break;
            default:
                break;
            }
        }
        return -1;
    }

}
