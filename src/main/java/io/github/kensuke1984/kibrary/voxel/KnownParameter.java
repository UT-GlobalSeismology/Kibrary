package io.github.kensuke1984.kibrary.voxel;

import java.util.ArrayList;
import java.util.List;

/**
 * A parameter with its known delta value.
 *
 * <p>
 * This extra class is created because {@link UnknownParameter} is not Comparable
 * and thus {@code Map<UnknownParameter, Double>} cannot be sorted.
 * (Map mixes up the order. Even with LinkedHashMap, keySet() cannot get the keys in order.)
 *
 * @author otsuru
 * @since 2022/7/2
 */
public class KnownParameter {

    private final UnknownParameter parameter;
    private final double value;

    public static List<UnknownParameter> extractParameterList(List<KnownParameter> knowns) {
        List<UnknownParameter> parameterList = new ArrayList<>();
        for (KnownParameter known : knowns) {
            parameterList.add(known.getParameter());
        }
        return parameterList;
    }

    public static double[] extractValueArray(List<KnownParameter> knowns) {
        double[] values = new double[knowns.size()];
        for (int i = 0; i < knowns.size(); i++) {
            values[i] = knowns.get(i).getValue();
        }
        return values;
    }

    public KnownParameter(UnknownParameter parameter, double value) {
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
