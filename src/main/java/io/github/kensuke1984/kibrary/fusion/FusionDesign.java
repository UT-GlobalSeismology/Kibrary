package io.github.kensuke1984.kibrary.fusion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.ParameterType;
import io.github.kensuke1984.kibrary.voxel.Physical3DParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

/**
 * A class that holds information of voxels that are changed before and after fusing voxels.
 * TODO this is currently only for 3D parameters
 *
 * @author otsuru
 * @since 2022/8/2
 */
public class FusionDesign {
    private List<List<UnknownParameter>> originalParameters = new ArrayList<>();
    private List<UnknownParameter> fusedParameters = new ArrayList<>();

    public FusionDesign() {
    }

    public void addFusion(UnknownParameter... params) {
        addFusion(Arrays.asList(params));
    }

    public void addFusion(List<UnknownParameter> params) {
        if (params.size() < 1) throw new IllegalStateException("No parameters for fusion are given.");

        VariableType type = params.get(0).getVariableType();
        for (UnknownParameter param : params) {
            if (param.getParameterType() != ParameterType.VOXEL) {
                System.err.println("!! Cannot fuse parameters that are not 3D: " + param);
                return;
            }
            if (param.getVariableType() != type) {
                System.err.println("!! Cannot fuse parameters due to variable type mismatch: " + param);
                return;
            }
            if (fuses(param)) {
                System.err.println("!! Parameter is already fused: " + param);
                return;
            }
        }

        // add original parameters
        originalParameters.add(params);

        Set<FullPosition> positions = params.stream().map(param -> param.getPosition()).collect(Collectors.toSet());
        // whether to use longitude range [0:360) instead of [-180,180)
        boolean crossDateLine = HorizontalPosition.crossesDateLine(positions);
        // add new parameter
        double latitude = positions.stream().mapToDouble(pos -> pos.getLatitude()).average().getAsDouble();
        double longitude = positions.stream().mapToDouble(pos -> pos.getLongitude(crossDateLine)).average().getAsDouble();
        double radius = positions.stream().mapToDouble(pos -> pos.getR()).average().getAsDouble();
        FullPosition position = new FullPosition(latitude, longitude, radius);
        double size = params.stream().mapToDouble(param -> param.getSize()).sum();
        UnknownParameter fusedParam = new Physical3DParameter(type, position, size);
        fusedParameters.add(fusedParam);
    }

    public void add(List<UnknownParameter> originalParams, UnknownParameter fusedParam) {
        if (originalParams.size() < 1) throw new IllegalStateException("No parameters for fusion are given.");

        VariableType type = fusedParam.getVariableType();
        for (UnknownParameter param : originalParams) {
            if (param.getParameterType() != ParameterType.VOXEL) {
                System.err.println("Cannot fuse parameters that are not 3D.");
                return;
            }
            if (param.getVariableType() != type) {
                System.err.println("Cannot fuse parameters due to variable type mismatch.");
                return;
            }
        }

        originalParameters.add(originalParams);
        fusedParameters.add(fusedParam);
    }

    public List<KnownParameter> reverseFusion(List<KnownParameter> inputKnowns) {
        List<KnownParameter> reversedKnowns = new ArrayList<>();
        for (KnownParameter known : inputKnowns) {
            if (fusedParameters.contains(known.getParameter())) {
                int index = fusedParameters.indexOf(known.getParameter());
                List<UnknownParameter> correspondingParams = originalParameters.get(index);
                // for each of the corresponding original parameters, form a KnownParameter with the result value of fused grid
                for (UnknownParameter param : correspondingParams) {
                    reversedKnowns.add(new KnownParameter(param, known.getValue()));
                }
            } else {
                reversedKnowns.add(known);
            }
        }
        return reversedKnowns;
    }

    public List<List<UnknownParameter>> getOriginalParameters() {
        return new ArrayList<>(originalParameters);
    }

    public List<UnknownParameter> getFusedParameters() {
        return new ArrayList<>(fusedParameters);
    }

    /**
     * Checks if the specified parameter is contained as one of the original parameters to be fused.
     * @param param
     * @return (boolean) true if the parameter is to be fused
     */
    public boolean fuses(UnknownParameter param) {
        for (List<UnknownParameter> paramList : originalParameters) {
            if (paramList.contains(param)) return true;
        }
        return false;
    }

}
