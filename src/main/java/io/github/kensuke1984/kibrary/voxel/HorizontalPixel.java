package io.github.kensuke1984.kibrary.voxel;

import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * @author otsuru
 * @since 2022/9/12
 */
public class HorizontalPixel {
    private HorizontalPosition position;
    private double dLatitude;
    private double dLongitude;

    public HorizontalPixel(HorizontalPosition position, double dLatitude, double dLongitude) {
        super();
        this.position = position;
        this.dLatitude = dLatitude;
        this.dLongitude = dLongitude;
    }

    public HorizontalPosition getPosition() {
        return position;
    }

    public double getDLatitude() {
        return dLatitude;
    }

    public double getDLongitude() {
        return dLongitude;
    }
}
