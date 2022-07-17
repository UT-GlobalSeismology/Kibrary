package io.github.kensuke1984.kibrary.model;

import java.util.ArrayList;
import java.util.List;

import io.github.kensuke1984.kibrary.util.earth.ParameterType;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

/**
 * A model composed of a set of voxels, each of which its parameters are perturbed.
 * This class holds a set of {@link PerturbationVoxel}s.
 *
 * @author otsuru
 * @since 2022/4/9
 */
public class PerturbationModel {

    private List<PerturbationVoxel> voxelList = new ArrayList<>();

    public PerturbationModel() {}

    public PerturbationModel(List<UnknownParameter> unknowns, double[] values, PolynomialStructure initialStructure) {
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
                PerturbationVoxel voxel = new PerturbationVoxel(unknowns.get(i).getPosition(), unknowns.get(i).getWeighting(), initialStructure);
                voxel.setDelta(ParameterType.of(unknowns.get(i).getPartialType()), values[i]);
                voxelList.add(voxel);
            }
        }

        // if RHO is not included in unknwons, set RHO to value in initial structure
        for (PerturbationVoxel voxel : voxelList) {
            voxel.setDefaultIfUndefined(ParameterType.RHO);
        }
    }

    public PerturbationModel(List<KnownParameter> knowns, PolynomialStructure initialStructure) {
        for (int i = 0; i < knowns.size(); i++) {
            boolean flag = false;

            UnknownParameter parameter = knowns.get(i).getParameter();
            double value = knowns.get(i).getValue();

            // if a voxel of same position is already added, set value to that voxel
            for (PerturbationVoxel voxel : voxelList) {
                if (voxel.getPosition().equals(parameter.getPosition())) {
                    voxel.setDelta(ParameterType.of(parameter.getPartialType()), value);
                    flag = true;
                }
            }

            // otherwise, create new voxel
            if (flag == false) {
                PerturbationVoxel voxel = new PerturbationVoxel(parameter.getPosition(), parameter.getWeighting(), initialStructure);
                voxel.setDelta(ParameterType.of(parameter.getPartialType()), value);
                voxelList.add(voxel);
            }
        }

        // if RHO is not included in unknwons, set RHO to value in initial structure
        for (PerturbationVoxel voxel : voxelList) {
            voxel.setDefaultIfUndefined(ParameterType.RHO);
        }
    }

    public void add(PerturbationVoxel voxel) {
        voxelList.add(voxel);
    }

    public List<PerturbationVoxel> getVoxels() {
        return voxelList;
    }

//    public PerturbationModel(Map<FullPosition, Double> modelMap, PolynomialStructure oneDStructure) {
//
//    }

}
