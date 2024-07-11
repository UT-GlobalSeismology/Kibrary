package io.github.kensuke1984.kibrary.waveform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.math3.util.Precision;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.timewindow.Timewindow;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.voxel.ParameterType;
import io.github.kensuke1984.kibrary.voxel.Physical1DParameter;
import io.github.kensuke1984.kibrary.voxel.Physical3DParameter;
import io.github.kensuke1984.kibrary.voxel.TimeReceiverSideParameter;
import io.github.kensuke1984.kibrary.voxel.TimeSourceSideParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

/***
 * <p>
 * ID for a partial derivative
 * </p>
 * This class is <b>IMMUTABLE</b> <br>
 *
 * =Contents of information for one ID=<br>
 * Name of station<br>
 * Name of network<br>
 * Horizontal position of observer (latitude longitude)<br>
 * Global CMT ID<br>
 * Component (ZRT)<br>
 * Period minimum and maximum<br>
 * Start time<br>
 * Number of points<br>
 * Sampling Hz<br>
 * If one is convoluted or observed, true<br>
 * Position of a waveform for the ID<br>
 * partial type<br>
 * Position of a perturbation point: latitude, longitude, radius
 *
 *
 *
 * <p>
 * One ID volume:{@link PartialIDFile#oneIDByte}
 * </p>
 *
 * @since version 0.2.0.1.1
 * @author Kensuke Konishi
 *
 */
public class PartialID extends BasicID {

    /**
     * type of parameter
     */
    protected final ParameterType parameterType;
    /**
     * type of variable
     */
    protected final VariableType variableType;
    /**
     * position of perturbation
     */
    protected final FullPosition voxelPosition;

    public PartialID(Observer observer, GlobalCMTID eventID, SACComponent sacComponent, double samplingHz,
            double startTime, int npts, double minPeriod, double maxPeriod, Phase[] phases, boolean isConvolved,
            ParameterType parameterType, VariableType variableType, FullPosition voxelPosition, double... waveformData) {
        super(WaveformType.PARTIAL, samplingHz, startTime, npts, observer, eventID, sacComponent, minPeriod, maxPeriod,
                phases, isConvolved, waveformData);
        this.parameterType = parameterType;
        this.variableType = variableType;
        this.voxelPosition = voxelPosition;
    }

    /**
     * @param data to be set
     * @return {@link PartialID} with the input data
     */
    @Override
    public PartialID withData(double[] data) {
        return new PartialID(observer, eventID, component, samplingHz, startTime, data.length, minPeriod, maxPeriod,
                phases, convolved, parameterType, variableType, voxelPosition, data);
    }

    /**
     * Whether this {@link PartialID} is for the given {@link UnknownParameter}.
     * @param parameter ({@link UnknownParameter}) The parameter to compare with.
     * @return (boolean) Whether this {@link PartialID} is for the given {@link UnknownParameter}.
     *
     * @author otsuru
     * @since 2023/4/16
     */
    public boolean isForParameter(UnknownParameter parameter) {
        if (!parameterType.equals(parameter.getParameterType())) return false;
        if (!variableType.equals(parameter.getVariableType())) return false;

        switch(parameterType) {
        case SOURCE:
            if (eventID.equals(((TimeSourceSideParameter) parameter).getGlobalCMTID())) return true;
            else return false;
        case RECEIVER:
            //TODO
            List<Integer> bouncingOrders = new ArrayList<Integer>();
            bouncingOrders.add(1);
            Collections.sort(bouncingOrders);
            int lowestBouncingOrder = bouncingOrders.get(0);
            if (observer.equals( ((TimeReceiverSideParameter) parameter).getObserver() ) &&
                    ((TimeReceiverSideParameter) parameter).getBouncingOrder() == lowestBouncingOrder) return true;
            else return false;
        case LAYER:
            if (Precision.equals(voxelPosition.getR(), ((Physical1DParameter) parameter).getRadius(), FullPosition.RADIUS_EPSILON)) return true;
            else return false;
        case VOXEL:
            if (voxelPosition.equals(((Physical3DParameter) parameter).getPosition())) return true;
            else return false;
        default:
            throw new RuntimeException("Unknown ParameterType.");
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((parameterType == null) ? 0 : parameterType.hashCode());
        result = prime * result + ((variableType == null) ? 0 : variableType.hashCode());
        result = prime * result + ((voxelPosition == null) ? 0 : voxelPosition.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!super.equals(obj))
            return false;
        if (getClass() != obj.getClass())
            return false;
        PartialID other = (PartialID) obj;
        if (parameterType != other.parameterType)
            return false;
        if (variableType != other.variableType)
            return false;
        if (voxelPosition == null) {
            if (other.voxelPosition != null)
                return false;
        } else if (!voxelPosition.equals(other.voxelPosition))
            return false;
        return true;
    }

    public FullPosition getVoxelPosition() {
        return voxelPosition;
    }

    //TODO erase
    @Deprecated
    public PartialType getPartialType() {
        return PartialType.of(parameterType, variableType);
    }

    public ParameterType getParameterType() {
        return parameterType;
    }

    public VariableType getVariableType() {
        return variableType;
    }

    @Override
    public String toString() {
        String partialString = observer.toPaddedInfoString() + " " + eventID.toPaddedString() + " " + component + " "
                + MathAid.padToString(startTime, Timewindow.TYPICAL_MAX_INTEGER_DIGITS, Timewindow.PRECISION, false) + " "
                + npts + " " + samplingHz + " " + minPeriod + " " + maxPeriod + " "
                + TimewindowData.phasesAsString(phases) + " " + convolved + " "
                + parameterType + " " + variableType + " " + voxelPosition;
        return partialString;
    }

}
