package io.github.kensuke1984.kibrary.voxel;

/**
 * A parameter with its known value.
 *
 * @author otsuru
 * @since 2022/7/2
 */
public class KnownParameter {

    private final UnknownParameter parameter;
    private final double value;

    public KnownParameter(UnknownParameter parameter, double value) {
        super();
        this.parameter = parameter;
        this.value = value;
    }

    public UnknownParameter getParameter() {
        return parameter;
    }

    public double getValue() {
        return value;
    }

    @Override
    public String toString() {
        return parameter + " " + value;
    }
}
