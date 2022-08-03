package io.github.kensuke1984.kibrary.multigrid;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.voxel.Physical3DParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

/**
 *
 * TODO this is currently only for 3D parameters
 *
 * @author otsuru
 * @since 2022/8/2
 */
public class MultigridDesign {
    private List<List<UnknownParameter>> originalParameters = new ArrayList<>();
    private List<UnknownParameter> fusedParameters = new ArrayList<>();

    public MultigridDesign() {
    }

    public void addFusion(UnknownParameter... params) {
        if (params.length <= 1) return;
        PartialType type = params[0].getPartialType();
        for (UnknownParameter param : params) {
            if (param.getPartialType() != type) {
                System.err.println("Cannot fuse parameters due to partial type mismatch.");
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

    public List<List<UnknownParameter>> getOriginalParameters() {
        return new ArrayList<>(originalParameters);
    }

    public List<UnknownParameter> getFusedParameters() {
        return new ArrayList<>(fusedParameters);
    }



}
