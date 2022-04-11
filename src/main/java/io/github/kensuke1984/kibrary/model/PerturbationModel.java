package io.github.kensuke1984.kibrary.model;

import java.util.ArrayList;
import java.util.List;

import io.github.kensuke1984.kibrary.util.earth.ParameterType;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

/**
 * @author otsuru
 * @since 2022/4/9
 */
public class PerturbationModel {

    private List<PerturbationVoxel> voxelList = new ArrayList<>();

    public PerturbationModel(List<UnknownParameter> unknowns, double[] values, PolynomialStructure oneDStructure) {
        if (unknowns.size() != values.length) throw new IllegalArgumentException("Number of unknowns and values does not match");

        for (int i = 0; i < unknowns.size(); i++) {
            boolean flag = false;

            // if a voxel of same position is already added, set value to that voxel
            for (PerturbationVoxel voxel : voxelList) {
                if (voxel.getPosition().equals(unknowns.get(i).getPosition())) {
                    voxel.setDelta(ParameterType.of(unknowns.get(i).getPartialType()), values[i]);
                    flag = true;
                }
            }

            // otherwise, create new voxel
            if (flag == false) {
                PerturbationVoxel voxel = new PerturbationVoxel(unknowns.get(i).getPosition(), oneDStructure);
                voxel.setDelta(ParameterType.of(unknowns.get(i).getPartialType()), values[i]);
                voxelList.add(voxel);
            }
        }

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
