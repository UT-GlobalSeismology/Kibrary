package io.github.kensuke1984.kibrary.external.gmt;


import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

/**
 * 中心点　距離（中心点からの面の広がり）　azimuth r など から、断面を作る
 *
 * @author kensuke
 * @version 0.1.0
 */
public class CrossSection extends CrossSectionLine {


    /**
     * 断面上の点の半径
     */
    private double[] r;

    /**
     * 断面上の点の位置
     */
    private FullPosition[] locations;


    /**
     * @param centerLocation {@link HorizontalPosition} center
     * @param theta          [rad]  theta width
     * @param azimuth        [rad] azimuth of cross section
     * @param deltaTheta     [rad] delta theta
     * @param r              points in radius direction
     */
    public CrossSection(HorizontalPosition centerLocation, double theta, double azimuth, double deltaTheta,
                        double[] r) {
        super(centerLocation, theta, azimuth, deltaTheta);
        HorizontalPosition[] positions = getPositions();
        locations = new FullPosition[r.length * positions.length];
        double[] tmpThetaX = thetaX;
        thetaX = new double[r.length * positions.length];
//		System.exit(0);
        for (int i = 0; i < positions.length; i++) {
            for (int j = 0; j < r.length; j++) {
                locations[i * r.length + j] = positions[i].toFullPosition(r[j]);
//				System.out.println(locations[i*r.length+j]);
                thetaX[i * r.length + j] = tmpThetaX[i];
            }


        }
    }

    public static void main(String[] args) {
        HorizontalPosition hp = new HorizontalPosition(30, 160);
        double theta = Math.toRadians(10);
        double azimuth = Math.toRadians(120);
        double deltaTheta = Math.toRadians(5);
        double[] r = new double[]{1000, 2000};
        CrossSection cs = new CrossSection(hp, theta, azimuth, deltaTheta, r);


    }

    public double[] getR() {
        return r;
    }

    public FullPosition[] getLocations() {
        return locations;
    }

}
