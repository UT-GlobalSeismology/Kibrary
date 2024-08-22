package io.github.kensuke1984.kibrary.math.geometry;

import io.github.kensuke1984.kibrary.math.LinearRange;

/**
 * Ellipse.
 * The major axis is set on the x-axis and the minor axis on the y-axis.
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class Ellipse {

    /**
     * Semi-major axis. (0:)
     */
    private double a;

    /**
     * Semi-minor axis. (0:)
     */
    private double b;

    /**
     * Eccentricity.
     */
    private double e;

    /**
     * Flattening.
     */
    private double f;

    /**
     * @param a (double) Semi-major axis.
     * @param b (double) Semi-minor axis.
     */
    public Ellipse(double a, double b) {
        if (a < b) throw new IllegalArgumentException("a: " + a + "must be larger than b: " + b);
        if (!(a > 0) || !(b > 0)) throw new IllegalArgumentException("a, b :" + a + ", " + b + " must pe positive.");

        this.a = a;
        this.b = b;

        f = 1 - b / a;
        e = Math.sqrt(1 - (b * b / a / a));
    }

    /**
     * Compute area of whole ellipse.
     * @return (double) Area of the ellipse.
     */
    public double computeArea() {
        return Math.PI * a * b;
    }

    /**
     * Compute area of an elliptical sector.
     * @param theta0 (double) Lower theta. [0, theta1)
     * @param theta1 (double) Upper theta. (theta0, 2*pi]
     * @return (double) Area of elliptical sector.
     */
    public double computeEllipticalSectorArea(double theta0, double theta1) {
        LinearRange.checkValidity("Theta", theta0, theta1, 0.0, 2 * Math.PI);

        // get (x0, y0) for theta0
        double r0 = toR(theta0);
        double x0 = r0 * Math.cos(theta0);
        double y0 = r0 * Math.sin(theta0);
        // get (x1, y1) for theta1
        double r1 = toR(theta1);
        double x1 = r1 * Math.cos(theta1);
        double y1 = r1 * Math.sin(theta1);

        // expand y0, y1 onto circle
        y0 = y0 * a / b;
        y1 = y1 * a / b;
        XY xy0 = new XY(x0, y0);
        XY xy1 = new XY(x1, y1);
        // compute theta on expanded circle
        double theta = Math.acos(xy0.getInnerProduct(xy1) / xy0.getR() / xy1.getR());
        if (Math.PI < theta1 - theta0) theta = 2 * Math.PI - theta;
        // compute area on expanded circle, then crush to ellipse
        return a * b * theta / 2;
    }

    /**
     * Get y coordinate (0 &le; y) for a point on the ellipse with the specified x coordinate.
     * @param x (double) Input x coordinate.
     * @return (double) Corresponding y coordinate.
     */
    public double toY(double x) {
        return Math.sqrt((1 - x * x / a / a) * b * b);
    }

    /**
     * Get x coordinate (0 &le; x) for a point on the ellipse with the specified y coordinate.
     * @param y (double) Input y coordinate.
     * @return (double) Corresponding x coordinate.
     */
    public double toX(double y) {
        return Math.sqrt((1 - y * y / b / b) * a * a);
    }

    /**
     * Get radius at a point on the ellipse with the specified &theta;.
     * @param theta (double) Input &theta; value.
     * @return (double) Corresponding radius.
     */
    public double toR(double theta) {
        return Math.sqrt(1 / (Math.cos(theta) * Math.cos(theta) / a / a + Math.sin(theta) * Math.sin(theta) / b / b));
    }

    /**
     * Get &theta; [rad] (in [0, pi/2]) at a point on the ellipse with the specified radius.
     * @param r (double) Input radius.
     * @return (double) Corresponding &theta; value.
     */
    public double toTheta(double r) {
        return Math.acos(Math.sqrt((r * r - b * b) / (a * a - b * b)) * a / r);
    }

    public double getA() {
        return a;
    }

    public double getB() {
        return b;
    }

    public double getE() {
        return e;
    }

    public double getF() {
        return f;
    }

}
