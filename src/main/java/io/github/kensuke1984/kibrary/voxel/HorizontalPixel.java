package io.github.kensuke1984.kibrary.voxel;

import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.earth.Latitude;
import io.github.kensuke1984.kibrary.util.earth.Longitude;

/**
 * A rectangular pixel on a sphere.
 *
 * @author otsuru
 * @since 2022/9/12
 */
public class HorizontalPixel {
    private HorizontalPosition position;
    private double dLatitude;
    private double dLongitude;
    private int iLatitude;
    private int iLongitude;

    public HorizontalPixel(HorizontalPosition position, double dLatitude, double dLongitude, int iLatitude, int iLongitude) {
        super();
        this.position = position;
        this.dLatitude = dLatitude;
        this.dLongitude = dLongitude;
        this.iLatitude = iLatitude;
        this.iLongitude = iLongitude;
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

    public int getILatitude() {
        return iLatitude;
    }

    public int getILongitude() {
        return iLongitude;
    }

    @Override
    public String toString() {
        return position + " " + MathAid.padToString(dLatitude, 2, Latitude.DECIMALS + 1, false)
                + " " + MathAid.padToString(dLongitude, 2, Longitude.DECIMALS + 1, false)
                + " " + MathAid.padToString(iLatitude, 3, false) + " " + MathAid.padToString(iLongitude, 3, false);
    }

}
