package io.github.kensuke1984.kibrary.model;

import java.util.ArrayList;
import java.util.List;

import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.ParameterType;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

/**
 * @author otsuru
 * @since 2022/4/9
 */
public class PerturbationModel {

    private List<PerturbationVoxel> voxelList = new ArrayList<>();

    public PerturbationModel(List<UnknownParameter> unknowns, double[] values, PolynomialStructure initialStructure) {
        if (unknowns.size() != values.length) throw new IllegalArgumentException("Number of unknowns and values does not match");

        List<FullPosition> locations = new ArrayList<>();
        for (int i = 0; i < unknowns.size(); i++) {
            FullPosition loc = unknowns.get(i).getPosition();
            if (!locations.contains(loc))
                locations.add(loc);
        }

        for (FullPosition location : locations) {
            System.err.println(location); //TODO
        }

        for (int i = 0; i < locations.size(); i++) {
            boolean flag = false;

            // if a voxel of same position is already added, set value to that voxel
            for (PerturbationVoxel voxel : voxelList) {
                if (voxel.getPosition().equals(locations.get(i))) {
                    for (int j = 0; j < unknowns.size(); j++) {
                        if (unknowns.get(j).getPosition().equals(locations.get(i)))
                            voxel.setDelta(ParameterType.of(unknowns.get(j).getPartialType()), values[j]);
                    }
                    flag = true;
                }
            }

            // otherwise, create new voxel
            if (flag == false) {
                PerturbationVoxel voxel = new PerturbationVoxel(locations.get(i), initialStructure);
                for (int j = 0; j < unknowns.size(); j++) {
                    if (unknowns.get(j).getPosition().equals(locations.get(i)))
                        voxel.setDelta(ParameterType.of(unknowns.get(j).getPartialType()), values[j]);
                }
                voxelList.add(voxel);
            }
        }

        // if RHO is not included in unknwons, set RHO to value in initial structure
        for (PerturbationVoxel voxel : voxelList) {
            voxel.setDefaultIfUndefined(ParameterType.RHO);
        }
    }

    List<PerturbationVoxel> getVoxels() {
        return voxelList;
    }

//    public PerturbationModel(Map<FullPosition, Double> modelMap, PolynomialStructure oneDStructure) {
//
//    }

}
