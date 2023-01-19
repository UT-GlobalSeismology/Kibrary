package io.github.kensuke1984.kibrary.fusion;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
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
        if (params.length <= 1) return;
        PartialType type = params[0].getPartialType();
        for (UnknownParameter param : params) {
            if (param.getPartialType() != type) {
                System.err.println("Cannot fuse parameters due to partial type mismatch.");
                return;
            }
            if (fuses(param)) {
                // skip if a parameter is already contained in originalParameters  TODO: this should be changed when combining 3 or more parameters
                return;
            }
        }

        // add original parameters
        List<UnknownParameter> paramList = Arrays.asList(params);
        originalParameters.add(paramList);

        // add new parameter
        double latitude = paramList.stream().mapToDouble(param -> param.getPosition().getLatitude()).average().getAsDouble();
        double longitude = paramList.stream().mapToDouble(param -> param.getPosition().getLongitude()).average().getAsDouble();
        double radius = paramList.stream().mapToDouble(param -> param.getPosition().getR()).average().getAsDouble();
        FullPosition position = new FullPosition(latitude, longitude, radius);
        double weight = paramList.stream().mapToDouble(param -> param.getWeighting()).sum();
        UnknownParameter fusedParam = new Physical3DParameter(type, position, weight);
        fusedParameters.add(fusedParam);
    }

    public void add(List<UnknownParameter> originalParams, UnknownParameter fusedParam) {
        if (originalParams.size() == 0) return;
        PartialType type = fusedParam.getPartialType();
        for (UnknownParameter param : originalParams) {
            if (param.getPartialType() != type) {
                System.err.println("Cannot fuse parameters due to partial type mismatch.");
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
