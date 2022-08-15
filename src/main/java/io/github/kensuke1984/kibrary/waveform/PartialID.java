package io.github.kensuke1984.kibrary.waveform;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

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
     * position of perturbation
     */
    protected final FullPosition voxelPosition;
    /**
     * type of parameter
     */
    protected final PartialType partialType;

    public PartialID(Observer observer, GlobalCMTID eventID, SACComponent sacComponent, double samplingHz,
            double startTime, int npts, double minPeriod, double maxPeriod, Phase[] phases, long startByte, boolean isConvolved,
            FullPosition voxelPosition, PartialType partialType, double... waveformData) {
        super(WaveformType.PARTIAL, samplingHz, startTime, npts, observer, eventID, sacComponent, minPeriod, maxPeriod,
                phases, startByte, isConvolved, waveformData);
        this.partialType = partialType;
        this.voxelPosition = voxelPosition;
    }

    /**
     * @param data to be set
     * @return {@link PartialID} with the input data
     */
    @Override
    public PartialID withData(double[] data) {
        return new PartialID(observer, event, component, samplingHz, startTime, data.length, minPeriod, maxPeriod,
                phases, startByte, convolved, voxelPosition, partialType, data);
    }

    @Override
    public int hashCode() {
        int prime = 31;
        int result = super.hashCode();
        result = prime * result + ((partialType == null) ? 0 : partialType.hashCode());
        result = prime * result + ((voxelPosition == null) ? 0 : voxelPosition.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!super.equals(obj)) return false;
        if (getClass() != obj.getClass()) return false;
        PartialID other = (PartialID) obj;
        if (partialType != other.partialType) return false;
        if (voxelPosition == null) {
            if (other.voxelPosition != null) return false;
        } else if (!voxelPosition.equals(other.voxelPosition)) return false;
        return true;
    }

    public FullPosition getVoxelPosition() {
        return voxelPosition;
    }

    public PartialType getPartialType() {
        return partialType;
    }

    @Override
    public String toString() {
        String partialString = observer.getStation() + " " + observer.getNetwork() + " " + event + " " + component + " " + samplingHz + " "
                + startTime + " " + npts + " " + minPeriod + " " + maxPeriod + " ";
        for (int i = 0; i < phases.length - 1; i++)
            partialString += phases[i] + ",";
        partialString += phases[phases.length - 1];
        partialString += " " + startByte + " " + convolved + " "
                + voxelPosition + " " + partialType;
        return partialString;
    }

}
